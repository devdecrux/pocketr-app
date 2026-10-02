<script setup lang="ts">
/**
 * Account picker on `USelect`, grouped by account type (or by owner in household mode). EQUITY
 * accounts are never listed; archived ones only with `includeArchived` (history filters). With
 * `allLabel` the list starts with an entry that clears the selection (`''`), for filters.
 */
import type { SelectItem } from '@nuxt/ui'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { FIELD_SELECT_UI } from '@/components/forms/fieldStyles'
import { useAccountStore } from '@/stores/account'
import { useModeStore } from '@/stores/mode'
import type { Account, AccountType } from '@/types/ledger'

const ALL_VALUE = '__all__'

const props = withDefaults(
  defineProps<{
    id?: string
    allowedTypes?: AccountType[]
    currency?: string
    includeArchived?: boolean
    placeholder?: string
    allLabel?: string
    icon?: string
    ariaLabel?: string
    /** Extra classes for the trigger, for example a taller height. */
    triggerClass?: string
  }>(),
  { icon: 'i-lucide-wallet' },
)

const model = defineModel<string>({ default: '' })

const { t } = useI18n()
const accountStore = useAccountStore()
const modeStore = useModeStore()

const accounts = computed<Account[]>(() => {
  let list = props.includeArchived
    ? accountStore.accounts.filter((account) => account.type !== 'EQUITY')
    : accountStore.activeAccounts
  if (props.allowedTypes) {
    const allowed = new Set(props.allowedTypes)
    list = list.filter((account) => allowed.has(account.type))
  }
  if (props.currency) list = list.filter((account) => account.currency === props.currency)
  return list
})

function accountItem(account: Account): SelectItem {
  return {
    value: account.id,
    label:
      account.status === 'ARCHIVED'
        ? `${account.name} (${t('common.states.archived')})`
        : account.name,
    currency: account.currency,
  } as SelectItem
}

const items = computed<SelectItem[][]>(() => {
  const groups = new Map<string, Account[]>()
  for (const account of accounts.value) {
    const key = modeStore.isHousehold ? `owner:${account.ownerUserId}` : `type:${account.type}`
    groups.set(key, [...(groups.get(key) ?? []), account])
  }

  const grouped = [...groups.entries()].map(([key, members]) => {
    const label = modeStore.isHousehold
      ? t('components.accountSelector.ownerGroup', { ownerId: members[0]!.ownerUserId })
      : t(`display.accountTypes.${key.slice('type:'.length)}`)
    return [{ type: 'label', label } as SelectItem, ...members.map(accountItem)]
  })

  return props.allLabel ? [[{ value: ALL_VALUE, label: props.allLabel }], ...grouped] : grouped
})

const selected = computed(() => model.value || (props.allLabel ? ALL_VALUE : undefined))

function onSelect(value: unknown): void {
  if (typeof value === 'string') model.value = value === ALL_VALUE ? '' : value
}
</script>

<template>
  <USelect
    :id="id"
    :model-value="selected"
    :items="items"
    :icon="icon"
    :placeholder="placeholder ?? t('common.formHints.selectAccount')"
    :aria-label="ariaLabel"
    size="lg"
    class="w-full"
    :ui="{ ...FIELD_SELECT_UI, base: [FIELD_SELECT_UI.base, triggerClass] }"
    @update:model-value="onSelect"
  >
    <template #item-trailing="{ item }">
      <span v-if="(item as { currency?: string }).currency" class="text-xs text-muted">
        {{ (item as { currency?: string }).currency }}
      </span>
    </template>
  </USelect>
</template>
