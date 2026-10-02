import type { CategoryTag, LedgerSplit, LedgerTxn } from '@/types/ledger'
import { formatSplitAmount } from '@/utils/txnDisplay'

export interface TxnDetailsLookups {
  accountName: (accountId: string) => string | undefined
  categories: readonly Pick<CategoryTag, 'id' | 'name'>[]
  minorUnit: (currency: string) => number
}

export interface TxnDetails {
  /** The accounts money left and the accounts it reached ("Daily account → Living expenses"). */
  route: { from: string; to: string }
  categories: string[]
  /** Per-split amounts, only for transactions the route line cannot describe on its own. */
  splits: { key: string; label: string; amount: string }[]
}

const SIDE_RANK: Record<LedgerSplit['side'], number> = { CREDIT: 0, DEBIT: 1 }

export function orderedSplits(txn: LedgerTxn): LedgerSplit[] {
  return [...txn.splits].sort((a, b) => SIDE_RANK[a.side] - SIDE_RANK[b.side])
}

function splitAccountLabel(split: LedgerSplit, lookups: TxnDetailsLookups): string {
  return lookups.accountName(split.accountId) ?? split.accountName ?? split.accountId
}

/** Unique category names across all splits, in split order. */
export function txnCategoryNames(
  txn: LedgerTxn,
  categories: TxnDetailsLookups['categories'],
): string[] {
  const names: string[] = []
  for (const split of txn.splits) {
    if (!split.categoryTagId) continue
    const name =
      categories.find((category) => category.id === split.categoryTagId)?.name ??
      split.categoryTagName
    if (name && !names.includes(name)) names.push(name)
  }
  return names
}

export function buildTxnDetails(txn: LedgerTxn, lookups: TxnDetailsLookups): TxnDetails {
  const splits = orderedSplits(txn)
  const labelsFor = (side: LedgerSplit['side']) =>
    [
      ...new Set(
        splits.filter((split) => split.side === side).map((s) => splitAccountLabel(s, lookups)),
      ),
    ].join(', ')

  // One amount, one currency, two accounts: the row already shows everything the route line omits.
  const isSimple =
    splits.length <= 2 && splits.every((split) => split.accountCurrency === txn.currency)

  return {
    route: { from: labelsFor('CREDIT'), to: labelsFor('DEBIT') },
    categories: txnCategoryNames(txn, lookups.categories),
    splits: isSimple
      ? []
      : splits.map((split) => ({
          key: split.id ?? `${split.accountId}-${split.side}`,
          label: splitAccountLabel(split, lookups),
          amount: formatSplitAmount(split, lookups.minorUnit),
        })),
  }
}
