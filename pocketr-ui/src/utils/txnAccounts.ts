import type { Account, LedgerTxn } from '@/types/ledger'

/**
 * Name of the account a spending transaction went to: the LIABILITY DEBIT split for debt payments,
 * otherwise the (first) EXPENSE DEBIT split. Uses the account store name (reflects renames), then
 * the split's `accountName`; returns null when no such split or name exists so callers keep their own label.
 */
export function resolveSpendingAccountName(
  txn: LedgerTxn,
  lookupAccount: (accountId: string) => Pick<Account, 'name' | 'type'> | undefined,
): string | null {
  const targetType = txn.txnKind === 'DEBT_PAYMENT' ? 'LIABILITY' : 'EXPENSE'
  const split = txn.splits.find(
    (candidate) =>
      candidate.side === 'DEBIT' &&
      (candidate.accountType ?? lookupAccount(candidate.accountId)?.type) === targetType,
  )
  if (!split) return null
  return lookupAccount(split.accountId)?.name?.trim() || split.accountName?.trim() || null
}
