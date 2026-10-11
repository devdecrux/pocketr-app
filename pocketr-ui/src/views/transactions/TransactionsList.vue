<script setup lang="ts">
/**
 * The transactions list on top of `AppResponsiveList`: the desktop table columns and cells, the
 * grouped phone rows and their details panel. Loading, filtering and paging stay with the page;
 * row expansion is shared with it through `v-model:expanded`, and deleting is handed back through
 * `delete`.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AppResponsiveList from '@/components/lists/AppResponsiveList.vue'
import AppRowIconButton from '@/components/lists/AppRowIconButton.vue'
import AppRowPanelButton from '@/components/lists/AppRowPanelButton.vue'
import type { AppTableColumn, AppTablePagination } from '@/components/lists/dataTable'
import TransactionAmount from '@/views/transactions/TransactionAmount.vue'
import TransactionDetails, {
  type TxnDetailsCreator,
} from '@/views/transactions/TransactionDetails.vue'
import { buildTxnDetails, txnCategoryNames, type TxnDetails } from '@/views/transactions/txnDetails'
import { groupTransactionsByDay } from '@/views/transactions/txnGroups'
import { useAccountStore } from '@/stores/account'
import { useCategoryStore } from '@/stores/category'
import { useCurrencyStore } from '@/stores/currency'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import type { LedgerTxn } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { getTxnAppearance } from '@/utils/appearance/txnAppearance'
import { formatIsoDate } from '@/utils/dates'
import { initialsFromName } from '@/utils/initials'

const props = defineProps<{
  /** The rows to show: the loaded page after the description search. */
  rows: LedgerTxn[]
  pagination: AppTablePagination
  /** Id of the transaction whose delete is running, if any. */
  deletingId: string | null
}>()

const emit = defineEmits<{
  delete: [txn: LedgerTxn]
  'update:page': [page: number]
  'update:page-size': [size: number]
}>()

const expanded = defineModel<Record<string, boolean>>('expanded', { required: true })

const { t } = useI18n()
const ledgerStore = useLedgerStore()
const accountStore = useAccountStore()
const categoryStore = useCategoryStore()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()

// --- Row presentation ---

function categoryText(txn: LedgerTxn): string {
  return txnCategoryNames(txn, categoryStore.categories).join(', ')
}

function creatorName(txn: LedgerTxn): string {
  const creator = txn.createdBy
  return creator
    ? [creator.firstName, creator.lastName].filter(Boolean).join(' ') || creator.email
    : ''
}

// The mobile list has no member column, so in household mode the details name who added the row.
function detailsCreator(txn: LedgerTxn): TxnDetailsCreator | undefined {
  const creator = txn.createdBy
  if (!modeStore.isHousehold || !creator) return undefined
  return {
    name: creatorName(txn),
    initials: initialsFromName(creator.firstName, creator.lastName),
    avatar: creator.avatar,
  }
}

function detailsFor(txn: LedgerTxn): TxnDetails {
  return buildTxnDetails(txn, {
    accountName: (accountId) => accountStore.accountMap.get(accountId)?.name,
    categories: categoryStore.categories,
    minorUnit: currencyStore.getMinorUnit,
  })
}

function deleteLabel(txn: LedgerTxn): string {
  return t('views.transactions.rowActions.delete', { description: txn.description })
}

// The header toggle expands or collapses every row currently shown.
const allExpanded = computed(
  () => props.rows.length > 0 && props.rows.every((txn) => expanded.value[txn.id]),
)

function toggleAllRows(): void {
  expanded.value = allExpanded.value
    ? {}
    : Object.fromEntries(props.rows.map((txn) => [txn.id, true]))
}

// --- Desktop columns ---

const ROW_HEIGHT = 'h-[58px]'

/** Columns whose header carries a chevron; the amount column has its own icon. */
const CHEVRON_HEADERS = ['txnDate', 'kind', 'categories'] as const

const columns = computed<AppTableColumn<LedgerTxn>[]>(() => {
  // The member column leaves less room for the description, so the other columns tighten up.
  const tight = modeStore.isHousehold
  const dateWidth = tight ? 'min-w-[112px]' : 'min-w-[120px]'
  const typeWidth = tight ? 'min-w-[140px]' : 'min-w-[149px]'
  const categoryWidth = tight ? 'min-w-[112px]' : 'min-w-[140px]'
  const amountWidth = tight ? 'min-w-[128px]' : 'min-w-[150px]'
  const cols: AppTableColumn<LedgerTxn>[] = [
    {
      id: 'txnDate',
      accessorKey: 'txnDate',
      header: t('common.fields.date'),
      meta: {
        class: {
          th: `h-[50px] ${dateWidth}`,
          td: `${ROW_HEIGHT} ${dateWidth} whitespace-nowrap`,
        },
      },
    },
    {
      id: 'description',
      accessorKey: 'description',
      header: t('common.fields.description'),
      meta: { class: { th: 'h-[50px] w-full', td: `${ROW_HEIGHT} w-full max-w-0` } },
    },
    {
      id: 'kind',
      header: t('common.table.type'),
      meta: { class: { th: `h-[50px] ${typeWidth}`, td: `${ROW_HEIGHT} ${typeWidth}` } },
    },
    {
      id: 'categories',
      header: t('common.fields.category'),
      meta: { class: { th: `h-[50px] ${categoryWidth}`, td: `${ROW_HEIGHT} ${categoryWidth}` } },
    },
  ]
  if (modeStore.isHousehold) {
    cols.push({
      id: 'member',
      header: t('common.table.member'),
      meta: { class: { th: 'h-[50px] min-w-[88px]', td: `${ROW_HEIGHT} min-w-[88px] py-0` } },
    })
  }
  cols.push(
    {
      id: 'amount',
      header: t('common.fields.amount'),
      meta: {
        align: 'end',
        class: {
          th: `h-[50px] ${amountWidth}`,
          td: `${ROW_HEIGHT} ${amountWidth} whitespace-nowrap`,
        },
      },
    },
    {
      id: 'actions',
      header: t('common.table.actions'),
      meta: {
        class: {
          th: 'h-[50px] min-w-[66px] px-0',
          td: `${ROW_HEIGHT} min-w-[66px] px-0 py-0 text-center`,
        },
      },
    },
    {
      id: 'expand',
      header: '',
      meta: {
        class: {
          th: 'h-[50px] min-w-10 px-0 py-0 text-center',
          td: `${ROW_HEIGHT} min-w-10 px-0 py-0 text-center`,
        },
      },
    },
  )
  return cols
})

// --- Phone list groups ---

// Group headings are relative to the day the page was opened.
const today = new Date()

const groups = computed(() =>
  groupTransactionsByDay(props.rows, today).map((group) => ({
    ...group,
    label: groupLabel(group.id, group.monthDate),
  })),
)

function groupLabel(id: string, monthDate?: Date): string {
  if (id === 'today' || id === 'yesterday' || id === 'earlierThisMonth' || id === 'upcoming') {
    return t(`views.transactions.groups.${id}`)
  }
  return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(monthDate)
}
</script>

<template>
  <AppResponsiveList
    v-model:expanded="expanded"
    :rows="rows"
    :columns="columns"
    :row-key="(txn: LedgerTxn) => txn.id"
    panel-id-prefix="txn-details"
    :groups="groups"
    :loading="ledgerStore.isLoading"
    :loading-text="t('views.transactions.loading')"
    :empty-text="t('views.transactions.empty')"
    :pagination="pagination"
    clickable
    @row-click="(row) => row.toggleExpanded()"
    @update:page="emit('update:page', $event)"
    @update:page-size="emit('update:page-size', $event)"
  >
    <template v-for="id in CHEVRON_HEADERS" :key="id" #[`${id}-header`]="{ column }">
      <span class="inline-flex items-center gap-1.5">
        {{ column.columnDef.header }}
        <UIcon name="i-lucide-chevron-down" class="size-4 text-default" aria-hidden="true" />
      </span>
    </template>
    <template #amount-header="{ column }">
      <span class="inline-flex items-center gap-1.5">
        {{ column.columnDef.header }}
        <UIcon name="i-lucide-chevrons-up-down" class="size-4 text-default" aria-hidden="true" />
      </span>
    </template>
    <template #actions-header="{ column }">
      <span class="sr-only">{{ column.columnDef.header }}</span>
    </template>
    <template #expand-header>
      <span class="sr-only">{{ t('views.transactions.details.column') }}</span>
      <UTooltip
        :text="
          t(
            allExpanded
              ? 'views.transactions.rowActions.collapseAll'
              : 'views.transactions.rowActions.expandAll',
          )
        "
      >
        <UButton
          color="neutral"
          variant="ghost"
          size="xl"
          :icon="allExpanded ? 'i-lucide-fold-vertical' : 'i-lucide-unfold-vertical'"
          :aria-expanded="allExpanded"
          :aria-label="
            t(
              allExpanded
                ? 'views.transactions.rowActions.collapseAll'
                : 'views.transactions.rowActions.expandAll',
            )
          "
          :disabled="rows.length === 0"
          class="text-default"
          @click="toggleAllRows"
        />
      </UTooltip>
    </template>

    <template #txnDate-cell="{ row }">
      <span class="text-muted tabular-nums">{{ formatIsoDate(row.original.txnDate) }}</span>
    </template>
    <template #description-cell="{ row }">
      <span class="block truncate font-medium text-highlighted">
        {{ row.original.description }}
      </span>
    </template>
    <template #kind-cell="{ row }">
      <UBadge
        :color="getTxnAppearance(row.original.txnKind).badgeColor"
        variant="soft"
        size="lg"
        class="rounded-md px-3 font-normal"
        :class="getTxnAppearance(row.original.txnKind).badgeClass"
      >
        {{ getTxnAppearance(row.original.txnKind).label }}
      </UBadge>
    </template>
    <template #categories-cell="{ row }">
      <span class="block max-w-[108px] truncate text-highlighted">
        {{ categoryText(row.original) || '—' }}
      </span>
    </template>
    <template #member-cell="{ row }">
      <UTooltip
        v-if="row.original.createdBy"
        :text="`${creatorName(row.original)} (${row.original.createdBy.email})`"
      >
        <span class="inline-flex">
          <UAvatar
            :src="row.original.createdBy.avatar ?? undefined"
            :text="
              initialsFromName(row.original.createdBy.firstName, row.original.createdBy.lastName)
            "
            size="lg"
            aria-hidden="true"
          />
          <span class="sr-only">
            {{ creatorName(row.original) }}, {{ row.original.createdBy.email }}
          </span>
        </span>
      </UTooltip>
    </template>
    <template #amount-cell="{ row }">
      <TransactionAmount :txn="row.original" />
    </template>
    <template #actions-cell="{ row }">
      <AppRowIconButton
        destructive
        :icon="APP_ICONS.remove"
        :tooltip="t('common.actions.delete')"
        :label="deleteLabel(row.original)"
        :loading="deletingId === row.original.id"
        :disabled="deletingId !== null"
        @click="emit('delete', row.original)"
      />
    </template>
    <template #expand-cell="{ row }">
      <UButton
        color="neutral"
        variant="ghost"
        size="xl"
        :icon="row.getIsExpanded() ? APP_ICONS.expanded : APP_ICONS.collapsed"
        :aria-expanded="row.getIsExpanded()"
        :aria-label="
          t(
            row.getIsExpanded()
              ? 'views.transactions.rowActions.collapse'
              : 'views.transactions.rowActions.expand',
            { description: row.original.description },
          )
        "
        class="text-default"
        @click="row.toggleExpanded()"
      />
    </template>
    <template #expanded="{ row }">
      <TransactionDetails :details="detailsFor(row.original)" />
    </template>

    <template #row="{ item: txn }">
      <span
        class="flex size-11 shrink-0 items-center justify-center rounded-full bg-(--pocketr-tile-bg)"
      >
        <UIcon
          :name="getTxnAppearance(txn.txnKind).icon"
          class="size-5 text-highlighted dark:text-primary"
        />
      </span>
      <span class="min-w-0 flex-1">
        <span class="block truncate text-sm font-medium text-highlighted">
          {{ txn.description }}
        </span>
        <span class="block truncate text-[13px] text-muted">
          {{ categoryText(txn) || getTxnAppearance(txn.txnKind).label }}
        </span>
      </span>
      <TransactionAmount :txn="txn" />
    </template>
    <template #details="{ item: txn, group }">
      <TransactionDetails
        :details="detailsFor(txn)"
        :creator="detailsCreator(txn)"
        :date="
          group.id === 'today' || group.id === 'yesterday' ? undefined : formatIsoDate(txn.txnDate)
        "
      />
      <AppRowPanelButton
        destructive
        :icon="APP_ICONS.remove"
        :label="t('common.actions.delete')"
        :accessible-label="deleteLabel(txn)"
        :loading="deletingId === txn.id"
        :disabled="deletingId !== null"
        @click="emit('delete', txn)"
      />
    </template>
  </AppResponsiveList>
</template>
