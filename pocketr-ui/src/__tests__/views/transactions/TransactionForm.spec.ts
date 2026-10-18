import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { h } from 'vue'
import AppSelect from '@/components/fields/AppSelect.vue'
import MoneyInput from '@/components/fields/MoneyInput.vue'
import TransactionForm from '@/views/transactions/TransactionForm.vue'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import type { CreateTxnRequest } from '@/types/ledger'
import { buildAccount, CURRENCIES, freezeToday } from '@/__tests__/support/fixtures'
import { mountInApp } from '@/__tests__/support/mount'

function mountForm() {
  const onSubmit = vi.fn<(request: CreateTxnRequest) => void>()
  const onValid = vi.fn<(valid: boolean) => void>()
  const wrapper = mountInApp(() =>
    h(TransactionForm, { id: 'txn-form', serverError: null, 'onUpdate:valid': onValid, onSubmit }),
  )
  return { wrapper, onSubmit, onValid }
}

/** The account select of a form field, by the name its id ends with. */
function selectFor(wrapper: VueWrapper, field: string) {
  return wrapper
    .findAllComponents(AppSelect)
    .find((select) => String(select.props('id')).endsWith(`-${field}`))!
}

async function pick(wrapper: VueWrapper, field: string, accountId: string) {
  selectFor(wrapper, field).vm.$emit('update:modelValue', accountId)
  await flushPromises()
}

async function enterAmount(wrapper: VueWrapper, minor: number) {
  wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', minor)
  await flushPromises()
}

async function chooseTab(wrapper: VueWrapper, label: string) {
  const tab = wrapper.findAll('[role="tab"]').find((candidate) => candidate.text() === label)!
  await tab.trigger('mousedown')
  await tab.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

async function submit(wrapper: VueWrapper) {
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('TransactionForm', () => {
  beforeEach(() => {
    freezeToday()
    useAccountStore().accounts = [
      buildAccount({ id: 'daily', name: 'daily' }),
      buildAccount({ id: 'savings', name: 'savings' }),
      buildAccount({ id: 'jamie', name: 'jamie', ownerUserId: 2 }),
      buildAccount({ id: 'yen', name: 'yen', currency: 'JPY' }),
      buildAccount({ id: 'card', name: 'card', type: 'LIABILITY' }),
      buildAccount({ id: 'salary', name: 'salary', type: 'INCOME' }),
      buildAccount({ id: 'living', name: 'living', type: 'EXPENSE' }),
    ]
    useCurrencyStore().currencies = [CURRENCIES[0]!, { code: 'JPY', minorUnit: 0, name: 'Yen' }]
  })

  it('reports validity and does not submit an invalid draft', async () => {
    const { wrapper, onSubmit, onValid } = mountForm()
    await flushPromises()

    await submit(wrapper)
    await pick(wrapper, 'expense-pay-from', 'daily')
    await pick(wrapper, 'expense-account', 'living')
    await submit(wrapper)
    expect(onSubmit).not.toHaveBeenCalled()
    expect(onValid).not.toHaveBeenCalledWith(true)

    await enterAmount(wrapper, 1250)
    expect(onValid).toHaveBeenLastCalledWith(true)

    await enterAmount(wrapper, 0)
    expect(onValid).toHaveBeenLastCalledWith(false)
  })

  it('builds income, transfer and debt payment requests with the right account sides', async () => {
    const cases: [string, [string, string][], { accountId: string; side: string }[]][] = [
      [
        'Income',
        [
          ['income-account', 'salary'],
          ['income-deposit', 'daily'],
        ],
        [
          { accountId: 'daily', side: 'DEBIT' },
          { accountId: 'salary', side: 'CREDIT' },
        ],
      ],
      [
        'Transfer',
        [
          ['transfer-from', 'daily'],
          ['transfer-to', 'savings'],
        ],
        [
          { accountId: 'daily', side: 'CREDIT' },
          { accountId: 'savings', side: 'DEBIT' },
        ],
      ],
      [
        'Debt payment',
        [
          ['debt-payment-pay-from', 'daily'],
          ['debt-payment-liability', 'card'],
        ],
        [
          { accountId: 'daily', side: 'CREDIT' },
          { accountId: 'card', side: 'DEBIT' },
        ],
      ],
    ]

    for (const [tab, picks, splits] of cases) {
      const { wrapper, onSubmit } = mountForm()
      await chooseTab(wrapper, tab)
      for (const [field, accountId] of picks) {
        await pick(wrapper, field, accountId)
      }
      await enterAmount(wrapper, 350000)

      await submit(wrapper)

      expect(onSubmit).toHaveBeenCalledOnce()
      expect(onSubmit.mock.calls[0]![0].splits).toEqual(
        splits.map((split) => ({ ...split, amountMinor: 350000 })),
      )
    }
  })

  it("keeps each type's own fields while switching tabs", async () => {
    const { wrapper } = mountForm()
    await pick(wrapper, 'expense-account', 'living')

    await chooseTab(wrapper, 'Income')
    await chooseTab(wrapper, 'Expense')

    expect(selectFor(wrapper, 'expense-account').props('modelValue')).toBe('living')
  })

  it('derives the currency and minor unit of the request from the pay-from account', async () => {
    const { wrapper, onSubmit } = mountForm()

    await pick(wrapper, 'expense-pay-from', 'yen')
    await pick(wrapper, 'expense-account', 'living')
    await enterAmount(wrapper, 500)

    const money = wrapper.findComponent(MoneyInput)
    expect(money.props('currencyCode')).toBe('JPY')
    expect(money.props('minorUnit')).toBe(0)

    await submit(wrapper)

    expect(onSubmit.mock.calls[0]![0]).toMatchObject({ currency: 'JPY' })
  })

  it('warns about a cross-user transfer only between household members, in household mode', async () => {
    const personal = mountForm().wrapper
    await chooseTab(personal, 'Transfer')
    await pick(personal, 'transfer-from', 'daily')
    await pick(personal, 'transfer-to', 'jamie')
    expect(personal.text()).not.toContain('cross-user transfer')

    useModeStore().switchToHousehold('h1')
    const { wrapper } = mountForm()
    await chooseTab(wrapper, 'Transfer')
    await pick(wrapper, 'transfer-from', 'daily')
    await pick(wrapper, 'transfer-to', 'jamie')
    expect(wrapper.text()).toContain('This is a cross-user transfer between household members.')

    await pick(wrapper, 'transfer-to', 'savings')
    expect(wrapper.text()).not.toContain('cross-user transfer')
  })
})
