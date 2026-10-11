import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import AppSelect from '@/components/fields/AppSelect.vue'
import AppDateField from '@/components/fields/AppDateField.vue'
import MoneyInput from '@/components/fields/MoneyInput.vue'
import TransactionForm from '@/views/transactions/TransactionForm.vue'
import { i18n } from '@/i18n'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import type { Account, CreateTxnRequest } from '@/types/ledger'
import { stubLocalStorage } from '@/__tests__/helpers/stubs'

function account(id: string, type: Account['type'], currency = 'EUR', ownerUserId = 1): Account {
  return {
    id,
    ownerUserId,
    name: id,
    type,
    currency,
    createdAt: '2026-01-01T00:00:00Z',
    status: 'ACTIVE',
    archivedAt: null,
  }
}

function mountForm() {
  const submitted: CreateTxnRequest[] = []
  const validity: boolean[] = []
  const wrapper = mount(
    {
      render: () =>
        h(UApp, null, {
          default: () =>
            h(TransactionForm, {
              id: 'txn-form',
              serverError: null,
              'onUpdate:valid': (value: boolean) => validity.push(value),
              onSubmit: (request: CreateTxnRequest) => submitted.push(request),
            }),
        }),
    },
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
  return { wrapper, submitted, validity }
}

function last<T>(items: readonly T[]): T | undefined {
  return items[items.length - 1]
}

type Wrapper = ReturnType<typeof mountForm>['wrapper']

/** The account select of a form field, by the name its id ends with. */
function selectFor(wrapper: Wrapper, field: string) {
  return wrapper
    .findAllComponents(AppSelect)
    .find((select) => String(select.props('id')).endsWith(`-${field}`))!
}

async function chooseTab(wrapper: Wrapper, label: string) {
  const tab = wrapper.findAll('[role="tab"]').find((candidate) => candidate.text() === label)!
  await tab.trigger('mousedown')
  await tab.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

async function submit(wrapper: Wrapper) {
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('TransactionForm', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-20T10:00:00'))
    setActivePinia(createPinia())
    stubLocalStorage()
    useAccountStore().accounts = [
      account('daily', 'ASSET'),
      account('savings', 'ASSET'),
      account('jamie', 'ASSET', 'EUR', 2),
      account('yen', 'ASSET', 'JPY'),
      account('card', 'LIABILITY'),
      account('salary', 'INCOME'),
      account('living', 'EXPENSE'),
    ]
    useCurrencyStore().currencies = [
      { code: 'EUR', minorUnit: 2, name: 'Euro' },
      { code: 'JPY', minorUnit: 0, name: 'Yen' },
    ]
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('offers the four types as tabs with the expense fields first', () => {
    const { wrapper } = mountForm()

    expect(wrapper.findAll('[role="tab"]').map((tab) => tab.text())).toEqual([
      'Expense',
      'Income',
      'Transfer',
      'Debt payment',
    ])
    expect(wrapper.findAll('label').map((label) => label.text())).toEqual([
      'Date',
      'Amount',
      'Pay from',
      'Expense account',
      'Category',
      'Description',
    ])
    expect(wrapper.findComponent(AppDateField).props('modelValue')).toBe('2026-09-20')

    wrapper.unmount()
  })

  it('reports validity and does not submit an invalid draft', async () => {
    const { wrapper, submitted, validity } = mountForm()
    await flushPromises()

    await submit(wrapper)
    expect(submitted).toHaveLength(0)

    selectFor(wrapper, 'expense-pay-from').vm.$emit('update:modelValue', 'daily')
    selectFor(wrapper, 'expense-account').vm.$emit('update:modelValue', 'living')
    await flushPromises()
    await submit(wrapper)
    expect(validity).toEqual([])
    expect(submitted).toHaveLength(0)

    wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', 1250)
    await flushPromises()
    expect(last(validity)).toBe(true)

    wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', 0)
    await flushPromises()
    expect(last(validity)).toBe(false)

    wrapper.unmount()
  })

  it('builds an expense request from the pay-from currency', async () => {
    const { wrapper, submitted } = mountForm()
    selectFor(wrapper, 'expense-pay-from').vm.$emit('update:modelValue', 'daily')
    selectFor(wrapper, 'expense-account').vm.$emit('update:modelValue', 'living')
    wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', 1250)
    await wrapper.get('input[placeholder="What was this for?"]').setValue('  Lunch ')
    await submit(wrapper)

    expect(submitted).toEqual([
      {
        mode: 'INDIVIDUAL',
        householdId: null,
        txnDate: '2026-09-20',
        currency: 'EUR',
        description: 'Lunch',
        splits: [
          { accountId: 'daily', side: 'CREDIT', amountMinor: 1250 },
          { accountId: 'living', side: 'DEBIT', amountMinor: 1250, categoryTagId: null },
        ],
      },
    ])

    wrapper.unmount()
  })

  it('builds income, transfer and debt payment requests with the right account sides', async () => {
    const { wrapper, submitted } = mountForm()

    await chooseTab(wrapper, 'Income')
    selectFor(wrapper, 'income-account').vm.$emit('update:modelValue', 'salary')
    selectFor(wrapper, 'income-deposit').vm.$emit('update:modelValue', 'daily')
    wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', 350000)
    await submit(wrapper)
    expect(last(submitted)?.splits).toEqual([
      { accountId: 'daily', side: 'DEBIT', amountMinor: 350000 },
      { accountId: 'salary', side: 'CREDIT', amountMinor: 350000 },
    ])

    await chooseTab(wrapper, 'Transfer')
    selectFor(wrapper, 'transfer-from').vm.$emit('update:modelValue', 'daily')
    selectFor(wrapper, 'transfer-to').vm.$emit('update:modelValue', 'savings')
    wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', 25000)
    await submit(wrapper)
    expect(last(submitted)?.splits).toEqual([
      { accountId: 'daily', side: 'CREDIT', amountMinor: 25000 },
      { accountId: 'savings', side: 'DEBIT', amountMinor: 25000 },
    ])

    await chooseTab(wrapper, 'Debt payment')
    selectFor(wrapper, 'debt-payment-pay-from').vm.$emit('update:modelValue', 'daily')
    selectFor(wrapper, 'debt-payment-liability').vm.$emit('update:modelValue', 'card')
    wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', 12000)
    await submit(wrapper)
    expect(last(submitted)?.splits).toEqual([
      { accountId: 'daily', side: 'CREDIT', amountMinor: 12000 },
      { accountId: 'card', side: 'DEBIT', amountMinor: 12000 },
    ])
    expect(submitted).toHaveLength(3)

    wrapper.unmount()
  })

  it("keeps each type's own fields while switching tabs", async () => {
    const { wrapper } = mountForm()
    selectFor(wrapper, 'expense-account').vm.$emit('update:modelValue', 'living')
    await flushPromises()

    await chooseTab(wrapper, 'Income')
    await chooseTab(wrapper, 'Expense')

    expect(selectFor(wrapper, 'expense-account').props('modelValue')).toBe('living')

    wrapper.unmount()
  })

  it('derives the currency symbol and minor unit from the source account', async () => {
    const { wrapper } = mountForm()
    selectFor(wrapper, 'expense-pay-from').vm.$emit('update:modelValue', 'yen')
    await flushPromises()

    const money = wrapper.findComponent(MoneyInput)
    expect(money.props('currencyCode')).toBe('JPY')
    expect(money.props('minorUnit')).toBe(0)

    wrapper.unmount()
  })

  it('shows the household notice and the cross-user transfer warning', async () => {
    useModeStore().switchToHousehold('h1')
    const { wrapper } = mountForm()

    await chooseTab(wrapper, 'Transfer')
    expect(wrapper.text()).toContain('Transfers between household accounts are allowed.')
    expect(wrapper.text()).not.toContain('cross-user transfer')

    selectFor(wrapper, 'transfer-from').vm.$emit('update:modelValue', 'daily')
    selectFor(wrapper, 'transfer-to').vm.$emit('update:modelValue', 'jamie')
    await flushPromises()
    expect(wrapper.text()).toContain('This is a cross-user transfer between household members.')

    selectFor(wrapper, 'transfer-to').vm.$emit('update:modelValue', 'savings')
    await flushPromises()
    expect(wrapper.text()).not.toContain('cross-user transfer')

    wrapper.unmount()
  })

  it('does not show the household notice in personal mode', async () => {
    const { wrapper } = mountForm()

    await chooseTab(wrapper, 'Transfer')
    expect(wrapper.text()).not.toContain('Transfers between household accounts')

    wrapper.unmount()
  })
})
