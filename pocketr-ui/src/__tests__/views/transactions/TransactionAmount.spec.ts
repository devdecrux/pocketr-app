import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import TransactionAmount from '@/views/transactions/TransactionAmount.vue'
import { buildTxn } from '@/__tests__/support/fixtures'
import { mountInApp } from '@/__tests__/support/mount'

const render = (txnKind: string) =>
  mountInApp(() => h(TransactionAmount, { txn: buildTxn({ txnKind }) }))

describe('TransactionAmount', () => {
  it('signs spending with a true minus and income with a plus, and shows no sign for transfers', () => {
    expect(render('EXPENSE').text()).toMatch(/^−.*46[.,]80/)
    expect(render('INCOME').text()).toMatch(/^\+.*46[.,]80/)
    expect(render('TRANSFER').text()).not.toMatch(/^[+−-]/)
  })
})
