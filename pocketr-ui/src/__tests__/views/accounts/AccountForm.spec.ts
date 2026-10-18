import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { h } from 'vue'
import AccountForm from '@/views/accounts/AccountForm.vue'
import { useCurrencyStore } from '@/stores/currency'
import type { CreateAccountRequest } from '@/types/ledger'
import { CURRENCIES, freezeToday } from '@/__tests__/support/fixtures'
import { mountInApp } from '@/__tests__/support/mount'

function mountForm(serverError: string | null = null) {
  const onSubmit = vi.fn<(request: CreateAccountRequest) => void>()
  const onValid = vi.fn<(valid: boolean) => void>()
  const wrapper = mountInApp(() =>
    h(AccountForm, { id: 'account-form', serverError, 'onUpdate:valid': onValid, onSubmit }),
  )
  return { wrapper, onSubmit, onValid }
}

async function chooseTab(wrapper: VueWrapper, label: string) {
  const tab = wrapper.findAll('[role="tab"]').find((candidate) => candidate.text() === label)!
  await tab.trigger('mousedown')
  await tab.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

async function typeName(wrapper: VueWrapper, value: string) {
  await wrapper.get('input#account-form-name').setValue(value)
  await flushPromises()
}

async function setBalance(wrapper: VueWrapper, value: string) {
  const input = wrapper.get('input#account-form-initial-balance')
  await input.setValue(value)
  await input.trigger('blur')
  await flushPromises()
}

async function submit(wrapper: VueWrapper) {
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('AccountForm', () => {
  beforeEach(() => {
    freezeToday()
    useCurrencyStore().currencies = CURRENCIES
  })

  it('is valid only with a name and does not submit without one', async () => {
    const { wrapper, onSubmit, onValid } = mountForm()
    await flushPromises()

    await submit(wrapper)
    expect(onSubmit).not.toHaveBeenCalled()

    await typeName(wrapper, '   ')
    expect(onValid).not.toHaveBeenLastCalledWith(true)

    await typeName(wrapper, ' Holiday savings ')
    expect(onValid).toHaveBeenLastCalledWith(true)
  })

  it('sends only name, type and currency when the opening balance is 0', async () => {
    const { wrapper, onSubmit } = mountForm()
    await flushPromises()

    await typeName(wrapper, 'Holiday savings')
    await submit(wrapper)

    expect(onSubmit).toHaveBeenCalledExactlyOnceWith({
      name: 'Holiday savings',
      type: 'ASSET',
      currency: 'EUR',
    })
  })

  it('sends the balance with the opening date when the balance is not 0', async () => {
    const { wrapper, onSubmit } = mountForm()
    await flushPromises()

    await typeName(wrapper, 'Holiday savings')
    await setBalance(wrapper, '500')
    await submit(wrapper)

    expect(onSubmit).toHaveBeenCalledExactlyOnceWith({
      name: 'Holiday savings',
      type: 'ASSET',
      currency: 'EUR',
      openingBalanceMinor: 50000,
      openingBalanceDate: '2026-09-20',
    })
  })

  it('turns a negative draft positive when switching to Liability', async () => {
    const { wrapper, onSubmit } = mountForm()
    await flushPromises()

    await typeName(wrapper, 'Loan')
    await setBalance(wrapper, '-40')
    await chooseTab(wrapper, 'Liability')
    await submit(wrapper)

    expect(onSubmit).toHaveBeenCalledExactlyOnceWith({
      name: 'Loan',
      type: 'LIABILITY',
      currency: 'EUR',
      openingBalanceMinor: 4000,
      openingBalanceDate: '2026-09-20',
    })
  })

  it('shows no opening fields for Expense and Income and never sends a balance for them', async () => {
    const { wrapper, onSubmit } = mountForm()
    await flushPromises()
    await typeName(wrapper, 'Groceries')
    await setBalance(wrapper, '75')

    for (const [label, type] of [
      ['Expense', 'EXPENSE'],
      ['Income', 'INCOME'],
    ]) {
      await chooseTab(wrapper, label!)
      await submit(wrapper)

      expect(wrapper.find('input#account-form-initial-balance').exists()).toBe(false)
      expect(onSubmit).toHaveBeenLastCalledWith({ name: 'Groceries', type, currency: 'EUR' })
    }
  })
})
