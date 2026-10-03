import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import AccountForm from '@/components/forms/AccountForm.vue'
import AppDateField from '@/components/forms/AppDateField.vue'
import MoneyInput from '@/components/forms/MoneyInput.vue'
import { i18n } from '@/i18n'
import { useCurrencyStore } from '@/stores/currency'
import type { CreateAccountRequest } from '@/types/ledger'

function mountForm() {
  const submitted: CreateAccountRequest[] = []
  const validity: boolean[] = []
  const wrapper = mount(
    {
      render: () =>
        h(UApp, null, {
          default: () =>
            h(AccountForm, {
              id: 'account-form',
              serverError: null,
              'onUpdate:valid': (value: boolean) => validity.push(value),
              onSubmit: (request: CreateAccountRequest) => submitted.push(request),
            }),
        }),
    },
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
  return { wrapper, submitted, validity }
}

type Wrapper = ReturnType<typeof mountForm>['wrapper']

async function chooseTab(wrapper: Wrapper, label: string) {
  const tab = wrapper.findAll('[role="tab"]').find((candidate) => candidate.text() === label)!
  await tab.trigger('mousedown')
  await tab.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

async function typeName(wrapper: Wrapper, value: string) {
  await wrapper.get('input#account-form-name').setValue(value)
  await flushPromises()
}

async function setBalance(wrapper: Wrapper, value: string) {
  const input = wrapper.get('input#account-form-initial-balance')
  await input.setValue(value)
  await input.trigger('blur')
  await flushPromises()
}

async function submit(wrapper: Wrapper) {
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

function last<T>(items: readonly T[]): T | undefined {
  return items[items.length - 1]
}

describe('AccountForm', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-20T10:00:00'))
    setActivePinia(createPinia())
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    })
    useCurrencyStore().currencies = [
      { code: 'EUR', minorUnit: 2, name: 'Euro' },
      { code: 'JPY', minorUnit: 0, name: 'Yen' },
    ]
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('offers the four account types and starts on Asset with EUR', async () => {
    const { wrapper, validity } = mountForm()
    await flushPromises()

    expect(wrapper.findAll('[role="tab"]').map((tab) => tab.text())).toEqual([
      'Asset',
      'Expense',
      'Income',
      'Liability',
    ])
    expect(wrapper.get('[role="tab"][data-state="active"]').text()).toBe('Asset')
    expect(wrapper.text()).toContain('EUR — Euro')
    expect(last(validity) ?? false).toBe(false)

    wrapper.unmount()
  })

  it('requires a name before it is valid and does not submit without one', async () => {
    const { wrapper, submitted, validity } = mountForm()
    await flushPromises()

    await submit(wrapper)
    expect(submitted).toEqual([])

    await typeName(wrapper, '   ')
    expect(last(validity) ?? false).toBe(false)

    await typeName(wrapper, ' Holiday savings ')
    expect(last(validity)).toBe(true)

    wrapper.unmount()
  })

  it('sends only name, type and currency when the opening balance is 0', async () => {
    const { wrapper, submitted } = mountForm()
    await flushPromises()

    await typeName(wrapper, 'Holiday savings')
    await submit(wrapper)

    expect(submitted).toEqual([{ name: 'Holiday savings', type: 'ASSET', currency: 'EUR' }])

    wrapper.unmount()
  })

  it('sends the opening balance and date when the balance is not 0', async () => {
    const { wrapper, submitted } = mountForm()
    await flushPromises()

    await typeName(wrapper, 'Holiday savings')
    await setBalance(wrapper, '500')
    await submit(wrapper)

    expect(submitted).toEqual([
      {
        name: 'Holiday savings',
        type: 'ASSET',
        currency: 'EUR',
        openingBalanceMinor: 50000,
        openingBalanceDate: '2026-09-20',
      },
    ])

    wrapper.unmount()
  })

  it('disables the opening date while the balance is 0', async () => {
    const { wrapper } = mountForm()
    await flushPromises()

    expect(wrapper.get('button#account-form-opening-date').attributes('disabled')).toBeDefined()
    expect(wrapper.findComponent(AppDateField).text()).toContain('20/09/2026')

    await setBalance(wrapper, '12.5')
    expect(wrapper.get('button#account-form-opening-date').attributes('disabled')).toBeUndefined()

    wrapper.unmount()
  })

  it('allows a negative opening balance for an asset only', async () => {
    const { wrapper, submitted } = mountForm()
    await flushPromises()

    expect(wrapper.findComponent(MoneyInput).props('allowNegative')).toBe(true)
    await typeName(wrapper, 'Overdraft')
    await setBalance(wrapper, '-40')
    await submit(wrapper)
    expect(last(submitted)?.openingBalanceMinor).toBe(-4000)

    await chooseTab(wrapper, 'Liability')
    expect(wrapper.findComponent(MoneyInput).props('allowNegative')).toBe(false)

    wrapper.unmount()
  })

  it('turns a negative draft positive when switching to Liability', async () => {
    const { wrapper, submitted } = mountForm()
    await flushPromises()

    await typeName(wrapper, 'Loan')
    await setBalance(wrapper, '-40')
    await chooseTab(wrapper, 'Liability')
    await submit(wrapper)

    expect(submitted).toEqual([
      {
        name: 'Loan',
        type: 'LIABILITY',
        currency: 'EUR',
        openingBalanceMinor: 4000,
        openingBalanceDate: '2026-09-20',
      },
    ])

    wrapper.unmount()
  })

  it('shows no opening fields for Expense and Income and never sends a balance for them', async () => {
    const { wrapper, submitted } = mountForm()
    await flushPromises()

    await typeName(wrapper, 'Groceries')
    await setBalance(wrapper, '75')

    for (const [label, type] of [
      ['Expense', 'EXPENSE'],
      ['Income', 'INCOME'],
    ] as const) {
      await chooseTab(wrapper, label)
      expect(wrapper.find('input#account-form-initial-balance').exists()).toBe(false)
      expect(wrapper.find('button#account-form-opening-date').exists()).toBe(false)
      await submit(wrapper)
      expect(last(submitted)).toEqual({ name: 'Groceries', type, currency: 'EUR' })
    }

    wrapper.unmount()
  })

  it('keeps the name while switching types and uses the currency minor unit', async () => {
    const { wrapper, submitted } = mountForm()
    await flushPromises()

    await typeName(wrapper, 'Cash')
    await chooseTab(wrapper, 'Income')
    expect(wrapper.get<HTMLInputElement>('input#account-form-name').element.value).toBe('Cash')
    await chooseTab(wrapper, 'Asset')

    expect(wrapper.findComponent(MoneyInput).props('minorUnit')).toBe(2)
    wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', 7)
    await submit(wrapper)
    expect(last(submitted)?.openingBalanceMinor).toBe(7)

    wrapper.unmount()
  })

  it('shows the server error', async () => {
    const wrapper = mount(
      {
        render: () =>
          h(UApp, null, {
            default: () => h(AccountForm, { id: 'account-form', serverError: 'Failed to create' }),
          }),
      },
      { attachTo: document.body, global: { plugins: [i18n, ui] } },
    )
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Failed to create')

    wrapper.unmount()
  })
})
