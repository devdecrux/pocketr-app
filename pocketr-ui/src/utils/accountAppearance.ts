import type { AccountType } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appIcons'

export interface AccountAppearance {
  /** Outline icon of the account row and of the create-account name field. */
  icon: string
  /** Nuxt UI colour of the type badge. */
  badgeColor: 'primary' | 'success' | 'error' | 'warning' | 'neutral'
  /** Text shade of the type badge: darker than the base colour in light mode for contrast. */
  badgeClass: string
  /** Text colour of the type name in the mobile list, matching the badge. */
  textClass: string
}

// One icon per account type: accounts carry no icon data of their own.
const APPEARANCE_BY_TYPE: Record<AccountType, AccountAppearance> = {
  ASSET: {
    icon: APP_ICONS.asset,
    badgeColor: 'primary',
    badgeClass: 'text-primary-700 dark:text-primary',
    textClass: 'text-primary-700 dark:text-primary',
  },
  EXPENSE: {
    icon: APP_ICONS.expense,
    badgeColor: 'error',
    badgeClass: 'text-error-700 dark:text-error-400',
    textClass: 'text-error-700 dark:text-error-400',
  },
  INCOME: {
    icon: APP_ICONS.income,
    badgeColor: 'success',
    badgeClass: 'text-success-700 dark:text-success-300',
    textClass: 'text-(--pocketr-success-fg)',
  },
  LIABILITY: {
    icon: APP_ICONS.liability,
    badgeColor: 'warning',
    badgeClass: 'text-warning-700 dark:text-warning',
    textClass: 'text-(--pocketr-warning-fg)',
  },
  EQUITY: {
    icon: APP_ICONS.asset,
    badgeColor: 'neutral',
    badgeClass: 'text-highlighted',
    textClass: 'text-muted',
  },
}

/** Nuxt UI presentation of an account type. */
export function getAccountAppearance(type: AccountType): AccountAppearance {
  return APPEARANCE_BY_TYPE[type]
}
