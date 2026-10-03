/**
 * The icon of every concept in the Nuxt UI interface. A concept has exactly one icon wherever it
 * appears (navigation, headers, fields, table cells, row tiles, buttons), so the same thing is always
 * recognisable. Page-level concepts use their sidebar icon. Transaction kinds and account types that
 * mean the same thing share an icon: a debt payment is shown like the liability it pays down.
 */
export const APP_ICONS = {
  // Pages and the things they list.
  dashboard: 'i-lucide-layout-grid',
  transactions: 'i-lucide-arrow-up-down',
  accounts: 'i-lucide-wallet-minimal',
  categories: 'i-lucide-shapes',
  personal: 'i-lucide-user',
  household: 'i-lucide-users',

  // Fields.
  name: 'i-lucide-text-cursor-input',
  description: 'i-lucide-file-text',
  date: 'i-lucide-calendar',
  amount: 'i-lucide-coins',
  search: 'i-lucide-search',
  filters: 'i-lucide-sliders-horizontal',

  // Actions.
  add: 'i-lucide-plus',
  edit: 'i-lucide-pencil',
  remove: 'i-lucide-trash-2',

  // Expandable rows, desktop table and mobile lists alike: closed points left, open points down.
  collapsed: 'i-lucide-chevron-left',
  expanded: 'i-lucide-chevron-down',

  // Transaction kinds and account types.
  expense: 'i-lucide-shopping-cart',
  income: 'i-lucide-trending-up',
  transfer: 'i-lucide-arrow-left-right',
  asset: 'i-lucide-wallet-minimal',
  liability: 'i-lucide-credit-card',
  debtPayment: 'i-lucide-credit-card',
} as const
