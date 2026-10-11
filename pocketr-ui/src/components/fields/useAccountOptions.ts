import { useI18n } from 'vue-i18n'
import type { AppSelectEntry, AppSelectItems } from '@/components/fields/selectItems'
import { useAccountStore } from '@/stores/account'
import { useModeStore } from '@/stores/mode'
import type { Account, AccountType } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'

export interface AccountOptionsFilter {
  allowedTypes?: AccountType[]
  currency?: string
  /** History filters also list archived accounts. EQUITY accounts are never listed. */
  includeArchived?: boolean
  /** Entry that clears the selection, for filters. */
  allLabel?: string
}

/**
 * Props for an `AppSelect` that picks an account: the accounts grouped by type (by owner in
 * household mode), the account icon and the "Select account" placeholder. Call it in a template
 * with `v-bind`; it reads the stores, so the list follows them.
 */
export function useAccountOptions() {
  const { t } = useI18n()
  const accountStore = useAccountStore()
  const modeStore = useModeStore()

  function option(account: Account): AppSelectEntry {
    return {
      value: account.id,
      label:
        account.status === 'ARCHIVED'
          ? `${account.name} (${t('common.states.archived')})`
          : account.name,
      trailing: account.currency,
    }
  }

  function accountItems(filter: AccountOptionsFilter = {}): AppSelectItems {
    let accounts = filter.includeArchived
      ? accountStore.accounts.filter((account) => account.type !== 'EQUITY')
      : accountStore.activeAccounts
    if (filter.allowedTypes) {
      const allowed = new Set(filter.allowedTypes)
      accounts = accounts.filter((account) => allowed.has(account.type))
    }
    if (filter.currency)
      accounts = accounts.filter((account) => account.currency === filter.currency)

    const groups = new Map<string, Account[]>()
    for (const account of accounts) {
      const key = modeStore.isHousehold ? `owner:${account.ownerUserId}` : `type:${account.type}`
      groups.set(key, [...(groups.get(key) ?? []), account])
    }
    return [...groups.entries()].map(([key, members]) => [
      {
        type: 'label' as const,
        label: modeStore.isHousehold
          ? t('components.accountSelect.ownerGroup', { ownerId: members[0]!.ownerUserId })
          : t(`display.accountTypes.${key.slice('type:'.length)}`),
      },
      ...members.map(option),
    ])
  }

  function accountSelect(filter: AccountOptionsFilter = {}) {
    return {
      items: accountItems(filter),
      icon: APP_ICONS.accounts,
      placeholder: t('common.formHints.selectAccount'),
      allLabel: filter.allLabel,
    }
  }

  return { accountSelect }
}
