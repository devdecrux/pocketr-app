export type TxnAmountIndicator = 'transfer' | 'plus' | 'minus'

export interface TxnPresentation {
  label: string
  badgeVariant: 'default' | 'destructive' | 'secondary' | 'outline'
  amountClass: string
  indicator: TxnAmountIndicator
}

const DEFAULT_PRESENTATION: TxnPresentation = {
  label: 'Transfer',
  badgeVariant: 'secondary',
  amountClass: 'text-muted-foreground',
  indicator: 'transfer',
}

const TRANSACTION_AMOUNT_CLASS = {
  negative: 'text-[var(--app-transaction-amount-negative-fg)]',
  positive: 'text-[var(--app-transaction-amount-positive-fg)]',
  warning: 'text-[var(--app-transaction-amount-warning-fg)]',
} as const

const TXN_PRESENTATION_BY_KIND: Record<string, TxnPresentation> = {
  EXPENSE: {
    label: 'Expense',
    badgeVariant: 'destructive',
    amountClass: TRANSACTION_AMOUNT_CLASS.negative,
    indicator: 'minus',
  },
  INCOME: {
    label: 'Income',
    badgeVariant: 'default',
    amountClass: TRANSACTION_AMOUNT_CLASS.positive,
    indicator: 'plus',
  },
  TRANSFER: DEFAULT_PRESENTATION,
  DEBT_PAYMENT: {
    label: 'Debt Payment',
    badgeVariant: 'destructive',
    amountClass: TRANSACTION_AMOUNT_CLASS.negative,
    indicator: 'minus',
  },
  OPENING_BALANCE: {
    label: 'Opening Balance',
    badgeVariant: 'secondary',
    amountClass: TRANSACTION_AMOUNT_CLASS.positive,
    indicator: 'plus',
  },
  OPENING_DEBT: {
    label: 'Opening Debt',
    badgeVariant: 'outline',
    amountClass: TRANSACTION_AMOUNT_CLASS.warning,
    indicator: 'plus',
  },
}

export function getTxnPresentation(txnKind: string): TxnPresentation {
  return TXN_PRESENTATION_BY_KIND[txnKind] ?? DEFAULT_PRESENTATION
}
