import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ui from '@nuxt/ui/vue-plugin'
import { createMemoryHistory, createRouter } from 'vue-router'
import TransactionAmount from '@/views/transactions/TransactionAmount.vue'
import { i18n } from '@/i18n'
import type { LedgerTxn } from '@/types/ledger'

function txn(txnKind: LedgerTxn['txnKind']): LedgerTxn {
  const split = (side: 'DEBIT' | 'CREDIT') => ({
    id: side,
    accountId: side,
    accountName: side,
    accountCurrency: 'EUR',
    side,
    amountMinor: 4680,
    exchangeRate: '1',
  })
  return {
    id: 't1',
    txnDate: '2026-09-20',
    description: 'Sample',
    currency: 'EUR',
    txnKind,
    createdAt: '2026-09-20T08:00:00Z',
    updatedAt: '2026-09-20T08:00:00Z',
    splits: [split('CREDIT'), split('DEBIT')],
  }
}

function render(txnKind: LedgerTxn['txnKind']) {
  const router = createRouter({ history: createMemoryHistory(), routes: [] })
  return mount(TransactionAmount, {
    props: { txn: txn(txnKind) },
    global: { plugins: [createPinia(), i18n, router, ui] },
  })
}

describe('TransactionAmount', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('signs spending with a true minus and income with a plus', () => {
    expect(render('EXPENSE').text()).toMatch(/^−.*46[.,]80/)
    expect(render('INCOME').text()).toMatch(/^\+.*46[.,]80/)
  })

  it('shows no sign but a transfer icon for transfers', () => {
    const wrapper = render('TRANSFER')
    expect(wrapper.text()).not.toMatch(/^[+−-]/)
    expect(wrapper.find('[aria-hidden="true"]').exists()).toBe(true)
  })
})
