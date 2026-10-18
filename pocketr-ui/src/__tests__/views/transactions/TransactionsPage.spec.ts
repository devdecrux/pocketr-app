import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { listAccounts } from '@/api/accounts'
import { listCategories } from '@/api/categories'
import { createTxn, deleteTxn, listTxns } from '@/api/ledger'
import AppDateRangePicker from '@/components/fields/AppDateRangePicker.vue'
import AppSelect from '@/components/fields/AppSelect.vue'
import MoneyInput from '@/components/fields/MoneyInput.vue'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import TransactionsPage from '@/views/transactions/TransactionsPage.vue'
import {
  ACCOUNTS,
  buildCategory,
  buildSplit,
  buildTxn,
  freezeToday,
  httpError,
  signIn,
  txnPage,
} from '@/__tests__/support/fixtures'
import {
  alertText,
  buttonIn,
  chooseOption,
  click,
  dialogByTitle,
  dialogs,
  openFilters,
  optionLabels,
  typeInto,
} from '@/__tests__/support/dom'
import { mountRoute } from '@/__tests__/support/mount'

const TXNS = [
  buildTxn(),
  buildTxn({
    id: 't2',
    txnDate: '2026-09-19',
    description: 'Savings transfer',
    txnKind: 'TRANSFER',
    splits: [
      buildSplit({ accountId: 'daily', side: 'CREDIT', amountMinor: 25000 }),
      buildSplit({ accountId: 'savings', side: 'DEBIT', amountMinor: 25000 }),
    ],
  }),
  buildTxn({
    id: 't3',
    txnDate: '2026-09-01',
    description: 'September salary',
    txnKind: 'INCOME',
    splits: [
      buildSplit({ accountId: 'salary', side: 'CREDIT', amountMinor: 350000 }),
      buildSplit({ accountId: 'daily', side: 'DEBIT', amountMinor: 350000 }),
    ],
  }),
]

async function mountPage() {
  const { wrapper } = await mountRoute(TransactionsPage, { path: '/transactions' })
  return wrapper
}

const dataRows = (wrapper: VueWrapper) => wrapper.findAll('main tbody tr[data-selectable="true"]')
const rowTexts = (wrapper: VueWrapper) => dataRows(wrapper).map((row) => row.text())
const lastListCall = () => vi.mocked(listTxns).mock.lastCall![0]
const deleteButton = (wrapper: VueWrapper) =>
  wrapper.get('button[aria-label="Delete Grocery market"]').element

describe('TransactionsPage', () => {
  beforeEach(() => {
    freezeToday()
    signIn()
    vi.mocked(listTxns).mockResolvedValue(txnPage(TXNS))
    vi.mocked(listAccounts).mockResolvedValue(ACCOUNTS)
    vi.mocked(listCategories).mockResolvedValue([
      buildCategory({ id: 'c1', name: 'Groceries' }),
      buildCategory({ id: 'c2', name: 'Transport', color: '#8391a5' }),
    ])
  })

  describe('list', () => {
    it('loads the first page once and shows one row per transaction', async () => {
      const wrapper = await mountPage()

      expect(listTxns).toHaveBeenCalledOnce()
      expect(lastListCall()).toEqual({ mode: 'INDIVIDUAL', page: 0, size: 15 })
      expect(rowTexts(wrapper)).toHaveLength(3)
    })

    it('filters descriptions on the client without a request', async () => {
      const wrapper = await mountPage()
      await openFilters(wrapper)

      await typeInto(wrapper.get('input[type="search"]').element as HTMLInputElement, '  SALARY ')

      expect(rowTexts(wrapper)).toHaveLength(1)
      expect(rowTexts(wrapper)[0]).toContain('September salary')
      expect(listTxns).toHaveBeenCalledOnce()
    })

    it('reloads from page 0 for a date range, an account (archived included) and a category', async () => {
      const wrapper = await mountPage()
      useLedgerStore().currentPage = 2
      await openFilters(wrapper)

      wrapper.findComponent(AppDateRangePicker).vm.$emit('update:from', '2026-09-01')
      wrapper.findComponent(AppDateRangePicker).vm.$emit('update:to', '2026-09-30')
      await flushPromises()
      expect(lastListCall()).toMatchObject({
        dateFrom: '2026-09-01',
        dateTo: '2026-09-30',
        page: 0,
      })

      expect(optionLabels(wrapper, 'Account')).toContain('Old checking (Archived)')
      await chooseOption(wrapper, 'Account', 'Old checking (Archived)')
      expect(lastListCall()).toMatchObject({ accountId: 'old', page: 0 })

      await chooseOption(wrapper, 'Category', 'Transport')
      expect(lastListCall()).toMatchObject({ accountId: 'old', categoryId: 'c2', page: 0 })
    })

    it('reloads from page 0 when the view mode changes', async () => {
      await mountPage()

      useModeStore().switchToHousehold('h1')
      await flushPromises()

      expect(lastListCall()).toMatchObject({ mode: 'HOUSEHOLD', householdId: 'h1', page: 0 })
      expect(listTxns).toHaveBeenCalledTimes(2)
    })
  })

  describe('delete', () => {
    it('asks for confirmation, then deletes and reloads', async () => {
      const wrapper = await mountPage()

      await click(deleteButton(wrapper))
      const dialog = dialogByTitle('Delete transaction "Grocery market"?')
      expect(deleteTxn).not.toHaveBeenCalled()

      await click(buttonIn(dialog, 'Delete transaction'))

      expect(deleteTxn).toHaveBeenCalledExactlyOnceWith('t1')
      expect(listTxns).toHaveBeenCalledTimes(2)
      expect(dialogs()).toHaveLength(0)
    })

    it('does not delete when the confirmation is cancelled', async () => {
      const wrapper = await mountPage()

      await click(deleteButton(wrapper))
      await click(buttonIn(dialogByTitle('Delete transaction'), 'Cancel'))

      expect(dialogs()).toHaveLength(0)
      expect(deleteTxn).not.toHaveBeenCalled()
    })

    it('shows an error and no rows when loading fails and the server message when deleting fails', async () => {
      vi.mocked(listTxns).mockRejectedValueOnce(new Error('offline'))
      const failed = await mountPage()
      expect(failed.text()).toContain('Failed to load transactions.')
      expect(rowTexts(failed)).toEqual([])
      failed.unmount()

      vi.mocked(listTxns).mockClear()
      vi.mocked(deleteTxn).mockRejectedValue(
        httpError('Cannot delete a reconciled transaction', 409),
      )
      const wrapper = await mountPage()

      await click(deleteButton(wrapper))
      await click(buttonIn(dialogByTitle('Delete transaction'), 'Delete transaction'))

      expect(alertText(wrapper.element)).toContain('Cannot delete a reconciled transaction')
      expect(listTxns).toHaveBeenCalledOnce()
    })
  })

  describe('create', () => {
    async function openCreate(wrapper: VueWrapper) {
      await click(buttonIn(wrapper.element as HTMLElement, 'New transaction'))
      return dialogByTitle('New transaction')
    }

    async function fillExpense(wrapper: VueWrapper, amountMinor = 4680) {
      const selects = wrapper.findAllComponents(AppSelect)
      const field = (suffix: string) =>
        selects.find((select) => String(select.props('id')).endsWith(suffix))!
      field('-expense-pay-from').vm.$emit('update:modelValue', 'daily')
      field('-expense-account').vm.$emit('update:modelValue', 'living')
      wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', amountMinor)
      await flushPromises()
    }

    it('creates an expense in the currency of the pay-from account, closes and reloads', async () => {
      const wrapper = await mountPage()
      const dialog = await openCreate(wrapper)
      await fillExpense(wrapper)

      await click(buttonIn(dialog, 'Create'))

      expect(createTxn).toHaveBeenCalledExactlyOnceWith({
        mode: 'INDIVIDUAL',
        householdId: null,
        txnDate: '2026-09-20',
        currency: 'EUR',
        description: '',
        splits: [
          { accountId: 'daily', side: 'CREDIT', amountMinor: 4680 },
          { accountId: 'living', side: 'DEBIT', amountMinor: 4680, categoryTagId: null },
        ],
      })
      expect(dialogs()).toHaveLength(0)
      expect(listTxns).toHaveBeenCalledTimes(2)
    })

    it('shows the server message at the top of the form and stays open', async () => {
      vi.mocked(createTxn).mockRejectedValue(httpError('Account is archived'))
      const wrapper = await mountPage()
      const dialog = await openCreate(wrapper)
      await fillExpense(wrapper)

      await click(buttonIn(dialog, 'Create'))

      expect(alertText(dialog)).toContain('Account is archived')
      expect(dialogs()).toHaveLength(1)
      expect(listTxns).toHaveBeenCalledOnce()
    })
  })
})
