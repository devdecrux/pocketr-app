import { daysAgo } from '@/utils/dashboardPeriods'

export type TxnGroupId = 'today' | 'yesterday' | 'earlierThisMonth' | 'upcoming' | `month:${string}`

export interface TxnGroup<T> {
  id: TxnGroupId
  /** First day of the month for `month:` groups, so the heading can be formatted for the locale. */
  monthDate?: Date
  items: T[]
}

function groupIdFor(txnDate: string, today: Date): { id: TxnGroupId; monthDate?: Date } {
  const age = daysAgo(txnDate, today)
  if (age < 0) return { id: 'upcoming' }
  if (age === 0) return { id: 'today' }
  if (age === 1) return { id: 'yesterday' }

  const [year = 0, month = 1] = txnDate.split('-').map(Number)
  if (year === today.getFullYear() && month === today.getMonth() + 1) {
    return { id: 'earlierThisMonth' }
  }
  return {
    id: `month:${year}-${String(month).padStart(2, '0')}`,
    monthDate: new Date(year, month - 1, 1),
  }
}

/**
 * Groups transactions for the mobile list: Today, Yesterday, Earlier this month, then one group per
 * older month. Consecutive transactions share a group, so the server's order is kept.
 */
export function groupTransactionsByDay<T extends { txnDate: string }>(
  txns: readonly T[],
  today: Date,
): TxnGroup<T>[] {
  const groups: TxnGroup<T>[] = []
  for (const txn of txns) {
    const { id, monthDate } = groupIdFor(txn.txnDate, today)
    const last = groups[groups.length - 1]
    if (last?.id === id) last.items.push(txn)
    else groups.push({ id, monthDate, items: [txn] })
  }
  return groups
}
