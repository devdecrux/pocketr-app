export type TxnBadgeColor = 'error' | 'success' | 'neutral' | 'warning'

export interface TxnAppearance {
  /** Outline icon of the mobile row tile. */
  icon: string
  /** Nuxt UI colour of the type badge. */
  badgeColor: TxnBadgeColor
  /**
   * Text shade of the type badge: darker than the base colour in light mode for contrast. Debt
   * payments also set their own orange surface, which Nuxt UI has no semantic colour for.
   */
  badgeClass: string
  /** Text colour of the amount: red for money going out, green for money coming in, grey for transfers. */
  amountClass: string
}

const OUT: Pick<TxnAppearance, 'amountClass'> = {
  amountClass: 'text-error dark:text-error-400',
}
const NEUTRAL: Pick<TxnAppearance, 'amountClass'> = { amountClass: 'text-muted' }
const IN: Pick<TxnAppearance, 'amountClass'> = { amountClass: 'text-(--pocketr-success-fg)' }

const APPEARANCE_BY_KIND: Record<string, TxnAppearance> = {
  EXPENSE: {
    icon: 'i-lucide-shopping-cart',
    badgeColor: 'error',
    badgeClass: 'text-error-700 dark:text-error-400',
    ...OUT,
  },
  INCOME: {
    icon: 'i-lucide-trending-up',
    badgeColor: 'success',
    badgeClass: 'text-success-700 dark:text-success-300',
    ...IN,
  },
  TRANSFER: {
    icon: 'i-lucide-arrow-left-right',
    badgeColor: 'neutral',
    badgeClass: 'text-highlighted',
    ...NEUTRAL,
  },
  DEBT_PAYMENT: {
    icon: 'i-lucide-wallet',
    badgeColor: 'neutral',
    badgeClass: 'bg-orange-500/10 text-orange-700 dark:bg-orange-400/10 dark:text-orange-400',
    ...OUT,
  },
  OPENING_BALANCE: {
    icon: 'i-lucide-piggy-bank',
    badgeColor: 'neutral',
    badgeClass: 'text-highlighted',
    ...IN,
  },
  OPENING_DEBT: {
    icon: 'i-lucide-wallet',
    badgeColor: 'warning',
    badgeClass: 'text-warning-700 dark:text-warning',
    amountClass: 'text-(--pocketr-warning-fg)',
  },
}

/** Nuxt UI presentation of a transaction kind; unknown kinds look like transfers, as in `getTxnPresentation`. */
export function getTxnAppearance(txnKind: string): TxnAppearance {
  return APPEARANCE_BY_KIND[txnKind] ?? APPEARANCE_BY_KIND.TRANSFER!
}
