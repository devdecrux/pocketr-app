import { describe, expect, it } from 'vitest'
import { groupTransactionsByDay } from '@/views/transactions/txnGroups'

describe('groupTransactionsByDay', () => {
  const today = new Date(2026, 8, 20, 10)

  it('groups consecutive transactions as upcoming, today, yesterday, earlier this month and older months', () => {
    const groups = groupTransactionsByDay(
      [
        { txnDate: '2026-09-21' },
        { txnDate: '2026-09-20' },
        { txnDate: '2026-09-20' },
        { txnDate: '2026-09-19' },
        { txnDate: '2026-09-18' },
        { txnDate: '2026-09-01' },
        { txnDate: '2026-08-31' },
        { txnDate: '2025-12-24' },
      ],
      today,
    )

    expect(groups.map((group) => [group.id, group.items.length])).toEqual([
      ['upcoming', 1],
      ['today', 2],
      ['yesterday', 1],
      ['earlierThisMonth', 2],
      ['month:2026-08', 1],
      ['month:2025-12', 1],
    ])
    expect(groups[4]!.monthDate).toEqual(new Date(2026, 7, 1))
  })

  it('does not merge non-consecutive groups, so the server order is kept', () => {
    const groups = groupTransactionsByDay(
      [{ txnDate: '2026-09-20' }, { txnDate: '2026-09-19' }, { txnDate: '2026-09-20' }],
      today,
    )

    expect(groups.map((group) => group.id)).toEqual(['today', 'yesterday', 'today'])
  })
})
