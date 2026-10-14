<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AppResponsiveList from '@/components/lists/AppResponsiveList.vue'
import AppRowIconButton from '@/components/lists/AppRowIconButton.vue'
import AppRowPanelButton from '@/components/lists/AppRowPanelButton.vue'
import type { AppTableColumn } from '@/components/lists/dataTable'
import { useAuthStore } from '@/stores/auth'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import type { Account } from '@/types/ledger'
import { getAccountAppearance } from '@/utils/appearance/accountAppearance'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { formatMoney } from '@/utils/money'

const props = defineProps<{
  accounts: Account[]
  balances: Map<string, number>
  sharedAccountIds: Set<string>
  loading: boolean
  /** The account being archived; its row shows a busy button and every archive button is disabled. */
  archivingAccountId: string | null
}>()

const emit = defineEmits<{
  rename: [account: Account]
  archive: [account: Account]
}>()

// Rows are expanded by account id on the phone list, which shows the row actions in place.
const expanded = defineModel<Record<string, boolean>>('expanded', { required: true })

const { t } = useI18n()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()
const authStore = useAuthStore()

function isOwnedAccount(account: Account): boolean {
  return account.ownerUserId === authStore.user?.id
}

function balanceText(account: Account): string | null {
  const balance = props.balances.get(account.id)
  if (balance === undefined) return null
  return formatMoney(balance, account.currency, currencyStore.getMinorUnit)
}

const ROW_HEIGHT = 'h-[63px]'

const columns = computed<AppTableColumn<Account>[]>(() => {
  // The Shared column leaves less room for the name, so the other columns tighten up.
  const tight = modeStore.isHousehold
  const typeWidth = tight ? 'min-w-[112px]' : 'min-w-[147px]'
  const currencyWidth = tight ? 'min-w-[88px]' : 'min-w-[130px]'
  const balanceWidth = tight ? 'min-w-[128px]' : 'min-w-[183px]'
  const actionsWidth = tight ? 'min-w-[150px]' : 'min-w-[162px]'
  const cols: AppTableColumn<Account>[] = [
    {
      id: 'name',
      accessorKey: 'name',
      header: t('common.fields.account'),
      meta: { class: { th: 'h-[50px] w-full', td: `${ROW_HEIGHT} w-full max-w-0` } },
    },
    {
      id: 'type',
      header: t('common.table.type'),
      meta: { class: { th: `h-[50px] ${typeWidth}`, td: `${ROW_HEIGHT} ${typeWidth}` } },
    },
    {
      id: 'currency',
      accessorKey: 'currency',
      header: t('common.fields.currency'),
      meta: { class: { th: `h-[50px] ${currencyWidth}`, td: `${ROW_HEIGHT} ${currencyWidth}` } },
    },
  ]
  if (modeStore.isHousehold) {
    cols.push({
      id: 'shared',
      header: t('common.table.shared'),
      meta: { class: { th: 'h-[50px] min-w-[96px]', td: `${ROW_HEIGHT} min-w-[96px]` } },
    })
  }
  cols.push(
    {
      id: 'balance',
      header: t('common.table.balance'),
      meta: {
        align: 'end',
        class: {
          th: `h-[50px] ${balanceWidth}`,
          td: `${ROW_HEIGHT} ${balanceWidth} whitespace-nowrap`,
        },
      },
    },
    {
      id: 'actions',
      header: t('common.table.actions'),
      meta: {
        align: 'center',
        class: { th: `h-[50px] ${actionsWidth}`, td: `${ROW_HEIGHT} ${actionsWidth} py-0` },
      },
    },
  )
  return cols
})
</script>

<template>
  <AppResponsiveList
    v-model:expanded="expanded"
    :rows="accounts"
    :columns="columns"
    :row-key="(account: Account) => account.id"
    panel-id-prefix="account-actions"
    :loading="loading"
    :loading-text="t('views.accounts.loading')"
    :empty-text="t('views.accounts.empty')"
  >
    <template #name-cell="{ row }">
      <span class="flex min-w-0 items-center gap-3.5">
        <UIcon
          :name="getAccountAppearance(row.original.type).icon"
          class="size-7 shrink-0 text-highlighted"
          aria-hidden="true"
        />
        <span class="truncate text-[15px] font-medium text-highlighted">
          {{ row.original.name }}
        </span>
      </span>
    </template>
    <template #type-cell="{ row }">
      <UBadge
        :color="getAccountAppearance(row.original.type).badgeColor"
        variant="soft"
        size="lg"
        class="rounded-md px-3 font-normal"
        :class="getAccountAppearance(row.original.type).badgeClass"
      >
        {{ t(`display.accountTypes.${row.original.type}`) }}
      </UBadge>
    </template>
    <template #currency-cell="{ row }">
      <span class="text-muted">{{ row.original.currency }}</span>
    </template>
    <template #shared-cell="{ row }">
      <UBadge
        v-if="sharedAccountIds.has(row.original.id)"
        color="neutral"
        variant="soft"
        size="lg"
        class="rounded-md px-3 font-normal"
      >
        {{ t('common.table.shared') }}
      </UBadge>
    </template>
    <template #balance-cell="{ row }">
      <span class="text-[15px] text-highlighted tabular-nums">
        <template v-if="balanceText(row.original)">{{ balanceText(row.original) }}</template>
        <template v-else>
          ...
          <span class="sr-only">{{ t('common.feedback.loading') }}</span>
        </template>
      </span>
    </template>
    <template #actions-cell="{ row }">
      <div
        v-if="isOwnedAccount(row.original)"
        class="inline-flex items-center justify-center gap-1"
      >
        <AppRowIconButton
          :icon="APP_ICONS.edit"
          :tooltip="t('common.actions.edit')"
          :label="t('common.table.editRow', { name: row.original.name })"
          @click="emit('rename', row.original)"
        />
        <AppRowIconButton
          destructive
          :icon="APP_ICONS.remove"
          :tooltip="t('common.actions.archive')"
          :label="t('views.accounts.archive.actionLabel', { name: row.original.name })"
          :loading="archivingAccountId === row.original.id"
          :disabled="archivingAccountId !== null"
          @click="emit('archive', row.original)"
        />
      </div>
      <span v-else class="inline-block max-w-36 text-start text-xs whitespace-normal text-muted">
        {{ t('views.accounts.sharedFromOtherMember') }}
      </span>
    </template>
    <template #row="{ item: account }">
      <UIcon
        :name="getAccountAppearance(account.type).icon"
        class="me-3 size-7 shrink-0 text-highlighted"
        aria-hidden="true"
      />
      <span class="min-w-0 flex-1">
        <span class="block truncate text-[15px] font-medium text-highlighted">
          {{ account.name }}
        </span>
        <span class="flex items-center gap-1.5 text-[13px] text-muted">
          <span class="truncate">
            <span :class="getAccountAppearance(account.type).textClass">
              {{ t(`display.accountTypes.${account.type}`) }}
            </span>
            · {{ account.currency }}
          </span>
          <UBadge
            v-if="sharedAccountIds.has(account.id)"
            color="neutral"
            variant="soft"
            size="sm"
            class="shrink-0 rounded-md font-normal"
          >
            {{ t('common.table.shared') }}
          </UBadge>
        </span>
      </span>
      <span class="shrink-0 text-[15px] whitespace-nowrap text-highlighted tabular-nums">
        <template v-if="balanceText(account)">{{ balanceText(account) }}</template>
        <template v-else>
          ...
          <span class="sr-only">{{ t('common.feedback.loading') }}</span>
        </template>
      </span>
    </template>
    <template #details="{ item: account }">
      <template v-if="isOwnedAccount(account)">
        <AppRowPanelButton
          :icon="APP_ICONS.edit"
          :label="t('common.actions.edit')"
          :accessible-label="t('common.table.editRow', { name: account.name })"
          @click="emit('rename', account)"
        />
        <AppRowPanelButton
          destructive
          :icon="APP_ICONS.remove"
          :label="t('common.actions.archive')"
          :accessible-label="t('views.accounts.archive.actionLabel', { name: account.name })"
          :loading="archivingAccountId === account.id"
          :disabled="archivingAccountId !== null"
          @click="emit('archive', account)"
        />
      </template>
      <p v-else class="text-sm text-muted">
        {{ t('views.accounts.sharedFromOtherMember') }}
      </p>
    </template>
  </AppResponsiveList>
</template>
