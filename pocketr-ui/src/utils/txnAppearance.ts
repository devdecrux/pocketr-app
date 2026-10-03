import { translate } from '@/i18n/translate'
import { APP_ICONS } from '@/utils/appIcons'

export type TxnBadgeColor = 'error' | 'success' | 'neutral' | 'warning'

export type TxnAmountIndicator = 'transfer' | 'plus' | 'minus'

export interface TxnAppearance {
  /** Translated name of the transaction kind. */
  label: string
  /** Direction of the amount: money out (`minus`), money in (`plus`) or between own accounts. */
  indicator: TxnAmountIndicator
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

type TxnAppearanceDefinition = Omit<TxnAppearance, 'label'> & {
  labelKey: `display.transactionKinds.${string}`
}

const OUT = { indicator: 'minus', amountClass: 'text-error dark:text-error-400' } as const
const NEUTRAL = { indicator: 'transfer', amountClass: 'text-muted' } as const
const IN = { indicator: 'plus', amountClass: 'text-(--pocketr-success-fg)' } as const

const APPEARANCE_BY_KIND: Record<string, TxnAppearanceDefinition> = {
  EXPENSE: {
    labelKey: 'display.transactionKinds.EXPENSE',
    icon: APP_ICONS.expense,
    badgeColor: 'error',
    badgeClass: 'text-error-700 dark:text-error-400',
    ...OUT,
  },
  INCOME: {
    labelKey: 'display.transactionKinds.INCOME',
    icon: APP_ICONS.income,
    badgeColor: 'success',
    badgeClass: 'text-success-700 dark:text-success-300',
    ...IN,
  },
  TRANSFER: {
    labelKey: 'display.transactionKinds.TRANSFER',
    icon: APP_ICONS.transfer,
    badgeColor: 'neutral',
    badgeClass: 'text-highlighted',
    ...NEUTRAL,
  },
  DEBT_PAYMENT: {
    labelKey: 'display.transactionKinds.DEBT_PAYMENT',
    icon: APP_ICONS.debtPayment,
    badgeColor: 'neutral',
    badgeClass: 'bg-orange-500/10 text-orange-700 dark:bg-orange-400/10 dark:text-orange-400',
    ...OUT,
  },
  OPENING_BALANCE: {
    labelKey: 'display.transactionKinds.OPENING_BALANCE',
    icon: APP_ICONS.asset,
    badgeColor: 'neutral',
    badgeClass: 'text-highlighted',
    ...IN,
  },
  OPENING_DEBT: {
    labelKey: 'display.transactionKinds.OPENING_DEBT',
    icon: APP_ICONS.liability,
    badgeColor: 'warning',
    badgeClass: 'text-warning-700 dark:text-warning',
    indicator: 'plus',
    amountClass: 'text-(--pocketr-warning-fg)',
  },
}

/** Presentation of a transaction kind; unknown kinds look like transfers. */
export function getTxnAppearance(txnKind: string): TxnAppearance {
  const { labelKey, ...appearance } = APPEARANCE_BY_KIND[txnKind] ?? APPEARANCE_BY_KIND.TRANSFER!
  return { label: translate(labelKey), ...appearance }
}
