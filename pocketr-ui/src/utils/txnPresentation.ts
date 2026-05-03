import { translate } from '@/i18n/translate'

export type TxnAmountIndicator = 'transfer' | 'plus' | 'minus'

export interface TxnPresentation {
  label: string
  badgeVariant: 'default' | 'destructive' | 'secondary' | 'outline'
  amountClass: string
  indicator: TxnAmountIndicator
}

type TxnPresentationLabelKey = `display.transactionKinds.${string}`

interface TxnPresentationDefinition extends Omit<TxnPresentation, 'label'> {
  labelKey: TxnPresentationLabelKey
}

const DEFAULT_PRESENTATION: TxnPresentationDefinition = {
  labelKey: 'display.transactionKinds.TRANSFER',
  badgeVariant: 'secondary',
  amountClass: 'text-muted-foreground',
  indicator: 'transfer',
}

const TRANSACTION_AMOUNT_CLASS = {
  negative: 'text-[var(--app-transaction-amount-negative-fg)]',
  positive: 'text-[var(--app-transaction-amount-positive-fg)]',
  warning: 'text-[var(--app-transaction-amount-warning-fg)]',
} as const

const TXN_PRESENTATION_BY_KIND: Record<string, TxnPresentationDefinition> = {
  EXPENSE: {
    labelKey: 'display.transactionKinds.EXPENSE',
    badgeVariant: 'destructive',
    amountClass: TRANSACTION_AMOUNT_CLASS.negative,
    indicator: 'minus',
  },
  INCOME: {
    labelKey: 'display.transactionKinds.INCOME',
    badgeVariant: 'default',
    amountClass: TRANSACTION_AMOUNT_CLASS.positive,
    indicator: 'plus',
  },
  TRANSFER: DEFAULT_PRESENTATION,
  DEBT_PAYMENT: {
    labelKey: 'display.transactionKinds.DEBT_PAYMENT',
    badgeVariant: 'destructive',
    amountClass: TRANSACTION_AMOUNT_CLASS.negative,
    indicator: 'minus',
  },
  OPENING_BALANCE: {
    labelKey: 'display.transactionKinds.OPENING_BALANCE',
    badgeVariant: 'secondary',
    amountClass: TRANSACTION_AMOUNT_CLASS.positive,
    indicator: 'plus',
  },
  OPENING_DEBT: {
    labelKey: 'display.transactionKinds.OPENING_DEBT',
    badgeVariant: 'outline',
    amountClass: TRANSACTION_AMOUNT_CLASS.warning,
    indicator: 'plus',
  },
}

export function getTxnPresentation(txnKind: string): TxnPresentation {
  const presentation = TXN_PRESENTATION_BY_KIND[txnKind] ?? DEFAULT_PRESENTATION

  return {
    label: translate(presentation.labelKey),
    badgeVariant: presentation.badgeVariant,
    amountClass: presentation.amountClass,
    indicator: presentation.indicator,
  }
}
