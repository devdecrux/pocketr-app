import type { LedgerSplit, LedgerTxn } from '@/types/ledger'
import { translate } from '@/i18n/translate'
import { formatMinor } from '@/utils/money'

type MinorUnitResolver = (currency: string) => number

export function formatTxnDisplayAmount(
  txn: LedgerTxn,
  getMinorUnit: MinorUnitResolver,
): string {
  const sourceCurrencySplits = txn.splits.filter((split) => split.accountCurrency === txn.currency)

  if (sourceCurrencySplits.length === 0) {
    return translate('common.amounts.mixedCurrencies')
  }

  const debitTotal = totalForSide(sourceCurrencySplits, 'DEBIT')
  const creditTotal = totalForSide(sourceCurrencySplits, 'CREDIT')
  const total = Math.max(debitTotal, creditTotal)

  return formatMinor(total, txn.currency, getMinorUnit(txn.currency))
}

export function formatSplitAmount(
  split: LedgerSplit,
  getMinorUnit: MinorUnitResolver,
): string {
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
