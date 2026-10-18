import { describe, expect, it } from 'vitest'
import { getTxnAppearance } from '@/utils/appearance/txnAppearance'

describe('getTxnAppearance', () => {
  it('names each kind and shows its amount with the matching indicator', () => {
    const expected: [string, string, string][] = [
      ['EXPENSE', 'Expense', 'minus'],
      ['DEBT_PAYMENT', 'Debt payment', 'minus'],
      ['INCOME', 'Income', 'plus'],
      ['OPENING_BALANCE', 'Opening balance', 'plus'],
      ['OPENING_DEBT', 'Opening debt', 'plus'],
      ['TRANSFER', 'Transfer', 'transfer'],
    ]

    expect(
      expected.map(([kind]) => {
        const { label, indicator } = getTxnAppearance(kind)
        return [kind, label, indicator]
      }),
    ).toEqual(expected)
  })

  it('treats an unknown kind like a transfer', () => {
    expect(getTxnAppearance('SOMETHING_ELSE')).toEqual(getTxnAppearance('TRANSFER'))
  })
})
