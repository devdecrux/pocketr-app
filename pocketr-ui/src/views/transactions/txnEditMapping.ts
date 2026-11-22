import type { LedgerSplit, LedgerTxn } from '@/types/ledger'
import type { TxnTab } from '@/views/transactions/useTransactionDraft'

/** Field values of one tab, ready to be assigned to the matching draft field set. */
export type TxnPrefill =
  | {
      tab: 'expense'
      date: string
      payFrom: string
      account: string
      amount: number
      category: string | null
      description: string
    }
  | {
      tab: 'income'
      date: string
      deposit: string
      account: string
      amount: number
      description: string
    }
  | { tab: 'transfer'; date: string; from: string; to: string; amount: number; description: string }
  | {
      tab: 'debt-payment'
      date: string
      payFrom: string
      liabilityAccount: string
      amount: number
      description: string
    }

/** The transaction kinds the form can create, and so edit, with the tab of each. */
const KIND_TABS: Record<string, TxnTab> = {
  EXPENSE: 'expense',
  INCOME: 'income',
  TRANSFER: 'transfer',
  DEBT_PAYMENT: 'debt-payment',
}

interface TxnSides {
  credit: LedgerSplit
  debit: LedgerSplit
}

/** The single credit and debit split the form builds; anything else does not fit a tab. */
function twoSides(txn: LedgerTxn): TxnSides | null {
  if (txn.splits.length !== 2) return null
  const credit = txn.splits.filter((split) => split.side === 'CREDIT')
  const debit = txn.splits.filter((split) => split.side === 'DEBIT')
  return credit.length === 1 && debit.length === 1 ? { credit: credit[0]!, debit: debit[0]! } : null
}

/**
 * The amount of the form is in the transaction currency. Split amounts are in their account's
 * currency, so the split whose account has the transaction currency carries it.
 */
function txnAmount({ credit, debit }: TxnSides, currency: string): number {
  return (debit.accountCurrency === currency ? debit : credit).amountMinor
}

/** Whether the edit form can represent `txn`: a form-created kind with one credit and one debit. */
export function isTxnEditable(txn: LedgerTxn): boolean {
  return txn.txnKind in KIND_TABS && twoSides(txn) !== null
}

/**
 * The inverse of the `txnStrategies` request builders: the tab and field values that rebuild the
 * request `txn` was created from, or `null` when the form cannot represent it.
 */
export function prefillFromTxn(txn: LedgerTxn): TxnPrefill | null {
  const tab = KIND_TABS[txn.txnKind]
  const sides = twoSides(txn)
  if (!tab || !sides) return null

  const { credit, debit } = sides
  const common = {
    date: txn.txnDate,
    amount: txnAmount(sides, txn.currency),
    description: txn.description,
  }
  switch (tab) {
    case 'expense':
      return {
        tab,
        ...common,
        payFrom: credit.accountId,
        account: debit.accountId,
        category: debit.categoryTagId ?? null,
      }
    case 'income':
      return { tab, ...common, deposit: debit.accountId, account: credit.accountId }
    case 'transfer':
      return { tab, ...common, from: credit.accountId, to: debit.accountId }
    default:
      return { tab, ...common, payFrom: credit.accountId, liabilityAccount: debit.accountId }
  }
}
