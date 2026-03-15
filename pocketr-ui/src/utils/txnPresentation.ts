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

const TXN_PRESENTATION_BY_KIND: Record<string, TxnPresentation> = {
  EXPENSE: {
    label: 'Expense',
    badgeVariant: 'destructive',
    amountClass: 'text-red-500',
    indicator: 'minus',
  },
  INCOME: {
    label: 'Income',
    badgeVariant: 'default',
    amountClass: 'text-green-500',
    indicator: 'plus',
  },
  TRANSFER: DEFAULT_PRESENTATION,
  DEBT_PAYMENT: {
    label: 'Debt Payment',
    badgeVariant: 'destructive',
    amountClass: 'text-red-500',
    indicator: 'minus',
  },
  OPENING_BALANCE: {
    label: 'Opening Balance',
    badgeVariant: 'secondary',
    amountClass: 'text-green-500',
    indicator: 'plus',
  },
  OPENING_DEBT: {
    label: 'Opening Debt',
    badgeVariant: 'outline',
    amountClass: 'text-amber-600',
    indicator: 'plus',
  },
}

export function getTxnPresentation(txnKind: string): TxnPresentation {
  return TXN_PRESENTATION_BY_KIND[txnKind] ?? DEFAULT_PRESENTATION
}
