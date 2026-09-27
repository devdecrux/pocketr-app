import type { Account, LedgerSplit, LedgerTxn } from '@/types/ledger'
import { translate } from '@/i18n/translate'
import { formatMinor } from '@/utils/money'

type MinorUnitResolver = (currency: string) => number

export function formatTxnDisplayAmount(txn: LedgerTxn, getMinorUnit: MinorUnitResolver): string {
  const sourceCurrencySplits = txn.splits.filter((split) => split.accountCurrency === txn.currency)

  if (sourceCurrencySplits.length === 0) {
    return translate('common.amounts.mixedCurrencies')
  }

  const debitTotal = totalForSide(sourceCurrencySplits, 'DEBIT')
  const creditTotal = totalForSide(sourceCurrencySplits, 'CREDIT')
  const total = Math.max(debitTotal, creditTotal)

  return formatMinor(total, txn.currency, getMinorUnit(txn.currency))
}

export function formatSplitAmount(split: LedgerSplit, getMinorUnit: MinorUnitResolver): string {
  const prefix = split.side === 'DEBIT' ? '+' : '-'
  return `${prefix}${formatMinor(
    split.amountMinor,
    split.accountCurrency,
    getMinorUnit(split.accountCurrency),
  )}`
}

function totalForSide(splits: LedgerSplit[], side: LedgerSplit['side']): number {
  return splits.reduce((sum, split) => {
    if (split.side !== side) return sum
    return sum + split.amountMinor
  }, 0)
}

/**
 * Name of the account a spending transaction went to: the LIABILITY DEBIT split for debt payments,
 * otherwise the (first) EXPENSE DEBIT split. Uses the account store name (reflects renames), then
 * the split's `accountName`, like TransactionsPage; returns null when no such split or name exists so callers keep their own label.
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
