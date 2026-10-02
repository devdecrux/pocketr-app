<script setup lang="ts">
import { HTTPError } from 'ky'
import { useMediaQuery } from '@vueuse/core'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { createTxn, deleteTxn } from '@/api/ledger'
import AccountSelect from '@/components/forms/AccountSelect.vue'
import AppDateRangePicker from '@/components/forms/AppDateRangePicker.vue'
import CategorySelect from '@/components/forms/CategorySelect.vue'
import FormMessage from '@/components/forms/FormMessage.vue'
import TransactionForm from '@/components/forms/TransactionForm.vue'
import { FIELD_BASE_CLASS } from '@/components/forms/fieldStyles'
import AppPagePanel from '@/components/layout/AppPagePanel.vue'
import AppConfirmDialog from '@/components/shared/AppConfirmDialog.vue'
import AppDataTable from '@/components/shared/AppDataTable.vue'
import AppFormOverlay from '@/components/shared/AppFormOverlay.vue'
import AppPaginationBar from '@/components/shared/AppPaginationBar.vue'
import TransactionDetails, {
  type TxnDetailsCreator,
} from '@/components/shared/TransactionDetails.vue'
import { useAccountStore } from '@/stores/account'
import { useCategoryStore } from '@/stores/category'
import { useCurrencyStore } from '@/stores/currency'
import { useHouseholdStore } from '@/stores/household'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import type { AppTableColumn } from '@/types/dataTable'
import type { CreateTxnRequest, LedgerTxn } from '@/types/ledger'
import { formatIsoDate } from '@/utils/dates'
import { initialsFromName } from '@/utils/initials'
import { formatTxnDisplayAmount } from '@/utils/txnDisplay'
import { buildTxnDetails, txnCategoryNames, type TxnDetails } from '@/utils/txnDetails'
import { getTxnAppearance } from '@/utils/txnAppearance'
import { groupTransactionsByDay } from '@/utils/txnGroups'
import { getTxnPresentation } from '@/utils/txnPresentation'

const CREATE_FORM_ID = 'create-transaction-form'
const FILTERS_PANEL_ID = 'transaction-filters'

const { t } = useI18n()
const ledgerStore = useLedgerStore()
const accountStore = useAccountStore()
const categoryStore = useCategoryStore()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()
const householdStore = useHouseholdStore()
const isDesktop = useMediaQuery('(min-width: 1024px)')

// Filters
const filterDateFrom = ref<string | undefined>(undefined)
const filterDateTo = ref<string | undefined>(undefined)
const filterAccountId = ref('')
const filterCategoryId = ref<string | null>(null)
const filterSearch = ref('')

// The filters stay out of the way until asked for; the button counts the ones in use.
const isFiltersOpen = ref(false)

const activeFilterCount = computed(
  () =>
    Number(Boolean(filterSearch.value.trim())) +
    Number(Boolean(filterDateFrom.value || filterDateTo.value)) +
    Number(Boolean(filterAccountId.value)) +
    Number(Boolean(filterCategoryId.value)),
)

function clearFilters(): void {
  filterSearch.value = ''
  filterDateFrom.value = undefined
  filterDateTo.value = undefined
  filterAccountId.value = ''
  filterCategoryId.value = null
}

// Rows are expanded by transaction id, shared by the desktop table and the mobile list.
const expanded = ref<Record<string, boolean>>({})

async function loadData(resetPage = false): Promise<void> {
  const filters: Record<string, string | undefined> = {}
  if (filterDateFrom.value) filters.dateFrom = filterDateFrom.value
  if (filterDateTo.value) filters.dateTo = filterDateTo.value
  if (filterAccountId.value) filters.accountId = filterAccountId.value
  if (filterCategoryId.value) filters.categoryId = filterCategoryId.value

  const page = resetPage ? 0 : ledgerStore.currentPage
  if (resetPage) ledgerStore.currentPage = 0
  expanded.value = {}
  await ledgerStore.load(filters, page, ledgerStore.pageSize)
}

async function goToPage(page: number): Promise<void> {
  ledgerStore.currentPage = page
  await loadData()
}

async function changePageSize(size: number): Promise<void> {
  ledgerStore.pageSize = size
  ledgerStore.currentPage = 0
  await loadData()
}

onMounted(async () => {
  await Promise.all([
    accountStore.load(),
    categoryStore.load(),
    currencyStore.load(),
    householdStore.loadHouseholds(),
    loadData(),
  ])
})

watch(
  [() => modeStore.viewMode, filterDateFrom, filterDateTo, filterAccountId, filterCategoryId],
  () => {
    void loadData(true)
  },
)

// The description search only narrows the page that is already loaded.
const filteredTransactions = computed(() => {
  const query = filterSearch.value.trim().toLowerCase()
  if (!query) return ledgerStore.transactions
  return ledgerStore.transactions.filter((txn) => txn.description.toLowerCase().includes(query))
})

const pagination = computed(() => ({
  page: ledgerStore.currentPage,
  pageSize: ledgerStore.pageSize,
  totalPages: ledgerStore.totalPages,
  totalElements: ledgerStore.totalElements,
}))

// --- Row presentation ---

function amountText(txn: LedgerTxn): string {
  return formatTxnDisplayAmount(txn, currencyStore.getMinorUnit)
}

function isTransfer(txn: LedgerTxn): boolean {
  return getTxnPresentation(txn.txnKind).indicator === 'transfer'
}

// "−€46.80" or "+€3,500.00" with a true minus, as on the dashboard; transfers show two arrows instead.
function signedAmountText(txn: LedgerTxn): string {
  if (isTransfer(txn)) return amountText(txn)
  return `${getTxnPresentation(txn.txnKind).indicator === 'minus' ? '−' : '+'}${amountText(txn)}`
}

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

function toggleRow(txn: LedgerTxn): void {
  expanded.value = { ...expanded.value, [txn.id]: !expanded.value[txn.id] }
}

// The header toggle expands or collapses every row currently shown.
const allExpanded = computed(
  () =>
    filteredTransactions.value.length > 0 &&
    filteredTransactions.value.every((txn) => expanded.value[txn.id]),
)

function toggleAllRows(): void {
  expanded.value = allExpanded.value
    ? {}
    : Object.fromEntries(filteredTransactions.value.map((txn) => [txn.id, true]))
}

const ROW_HEIGHT = 'h-[58px]'

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
      header: t('common.table.date'),
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
      header: t('common.table.description'),
      meta: { class: { th: 'h-[50px] w-full', td: `${ROW_HEIGHT} w-full max-w-0` } },
    },
    {
      id: 'kind',
      header: t('common.table.type'),
      meta: { class: { th: `h-[50px] ${typeWidth}`, td: `${ROW_HEIGHT} ${typeWidth}` } },
    },
    {
      id: 'categories',
      header: t('common.table.category'),
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
      header: t('common.table.amount'),
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

// --- Mobile list ---

// Group headings are relative to the day the page was opened.
const today = new Date()

const groups = computed(() =>
  groupTransactionsByDay(filteredTransactions.value, today).map((group) => ({
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

// --- Create ---

const isCreateOpen = ref(false)
// Remounts the form for every new transaction, so each one starts from an empty draft.
const createKey = ref(0)
const isFormValid = ref(false)
const isSubmitting = ref(false)
const submitError = ref('')

function openCreate(): void {
  if (isSubmitting.value) return
  submitError.value = ''
  isFormValid.value = false
  createKey.value += 1
  isCreateOpen.value = true
}

function onCreateOpenChange(open: boolean): void {
  if (!open && isSubmitting.value) return
  isCreateOpen.value = open
}

async function resolveMessage(error: unknown, fallback: string): Promise<string> {
  if (error instanceof HTTPError) {
    const payload = await error.response.json<{ message?: string }>().catch(() => null)
    return payload?.message?.trim() || fallback
  }
  return fallback
}

async function submitTransaction(request: CreateTxnRequest): Promise<void> {
  if (isSubmitting.value) return
  submitError.value = ''
  isSubmitting.value = true

  try {
    await createTxn(request)
    isCreateOpen.value = false
    await loadData()
  } catch (error: unknown) {
    submitError.value = await resolveMessage(error, t('errors.transactions.create'))
  } finally {
    isSubmitting.value = false
  }
}

// --- Delete ---

const deleteTarget = ref<LedgerTxn | null>(null)
// Kept apart from the target so the dialog text does not blank out while it animates closed.
const deleteTargetDescription = ref('')
const deleteError = ref('')
const deletingTxnId = ref<string | null>(null)

const isDeleteDialogOpen = computed({
  get: () => deleteTarget.value !== null,
  set: (open: boolean) => {
    if (!open && deletingTxnId.value === null) deleteTarget.value = null
  },
})

function requestDelete(txn: LedgerTxn): void {
  if (deletingTxnId.value !== null) return
  deleteError.value = ''
  deleteTargetDescription.value = txn.description
  deleteTarget.value = txn
}

async function confirmDelete(): Promise<void> {
  const txn = deleteTarget.value
  if (!txn || deletingTxnId.value !== null) return

  deletingTxnId.value = txn.id

  try {
    await deleteTxn(txn.id)
    deleteTarget.value = null
    await loadData()
  } catch (error: unknown) {
    deleteTarget.value = null
    deleteError.value = await resolveMessage(error, t('errors.transactions.delete'))
  } finally {
    deletingTxnId.value = null
  }
}

// Compact controls next to a pointer, full touch targets below `lg`.
const controlHeightClass = computed(() => (isDesktop.value ? 'h-9' : 'h-11'))
const searchUi = computed(() => ({
  base: `${FIELD_BASE_CLASS} ${controlHeightClass.value}`,
  leadingIcon: 'size-5 text-default',
}))
</script>

<template>
  <AppPagePanel :title="t('views.transactions.title')">
    <div class="flex min-w-0 flex-col gap-3 lg:gap-5">
      <!-- Below `lg` the subtitle runs under the title and the Filters button, at full width. -->
      <div class="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4">
        <h1 class="min-w-0 truncate text-[22px] leading-8 font-bold text-highlighted lg:hidden">
          {{ t('views.transactions.title') }}
        </h1>
        <h2 class="hidden min-w-0 truncate text-2xl leading-8 font-bold text-highlighted lg:block">
          {{ t('views.transactions.title') }}
        </h2>
        <p class="row-start-2 text-base text-muted max-lg:col-span-2">
          {{ t('views.transactions.subtitle') }}
        </p>
        <div class="flex shrink-0 items-center gap-2.5 lg:row-span-2 lg:self-start">
          <UButton
            type="button"
            color="neutral"
            variant="outline"
            size="md"
            icon="i-lucide-sliders-horizontal"
            :label="isDesktop ? t('views.transactions.filters.button') : undefined"
            :aria-label="t('views.transactions.filters.button')"
            :aria-expanded="isFiltersOpen"
            :aria-controls="FILTERS_PANEL_ID"
            class="h-10 rounded-lg bg-(--pocketr-field-bg) px-3 font-normal text-highlighted ring-default lg:px-4"
            @click="isFiltersOpen = !isFiltersOpen"
          >
            <template v-if="activeFilterCount > 0" #trailing>
              <UBadge
                :label="String(activeFilterCount)"
                color="primary"
                variant="subtle"
                size="sm"
              />
            </template>
          </UButton>
          <UButton
            icon="i-lucide-plus"
            size="md"
            :label="t('views.transactions.actions.new')"
            class="hidden h-10 rounded-lg px-5 lg:inline-flex"
            @click="openCreate"
          />
        </div>
      </div>

      <!-- Hidden until the Filters button opens them: one row on desktop, stacked below `lg`. -->
      <div
        v-if="isFiltersOpen"
        :id="FILTERS_PANEL_ID"
        role="group"
        :aria-label="t('views.transactions.filters.title')"
        class="grid gap-2.5 lg:flex lg:flex-wrap lg:items-center"
      >
        <UInput
          v-model="filterSearch"
          icon="i-lucide-search"
          type="search"
          autocomplete="off"
          size="lg"
          :aria-label="t('common.fields.search')"
          :placeholder="t('views.transactions.formHints.searchDescriptions')"
          :ui="searchUi"
          class="w-full lg:w-48"
        />
        <div class="min-w-0 lg:w-[232px]">
          <AppDateRangePicker
            v-model:from="filterDateFrom"
            v-model:to="filterDateTo"
            :trigger-class="controlHeightClass"
          />
        </div>
        <div class="min-w-0 lg:w-44">
          <AccountSelect
            v-model="filterAccountId"
            include-archived
            :trigger-class="controlHeightClass"
            :all-label="t('views.transactions.formHints.allAccounts')"
            :aria-label="t('common.fields.account')"
          />
        </div>
        <div class="min-w-0 lg:w-44">
          <CategorySelect
            v-model="filterCategoryId"
            icon="i-lucide-layout-grid"
            :trigger-class="controlHeightClass"
            :none-label="t('views.transactions.formHints.allCategories')"
            :aria-label="t('common.fields.category')"
          />
        </div>
        <UTooltip v-if="activeFilterCount > 0" :text="t('views.transactions.filters.clear')">
          <UButton
            type="button"
            color="neutral"
            variant="ghost"
            size="md"
            icon="i-lucide-x"
            :label="isDesktop ? undefined : t('views.transactions.filters.clear')"
            :aria-label="t('views.transactions.filters.clear')"
            class="rounded-lg text-default max-lg:justify-self-start"
            @click="clearFilters"
          />
        </UTooltip>
      </div>

      <FormMessage v-if="deleteError" tone="error" :message="deleteError" />
      <FormMessage v-if="ledgerStore.error" tone="error" :message="ledgerStore.error" />

      <template v-else>
        <AppDataTable
          v-if="isDesktop"
          v-model:expanded="expanded"
          :data="filteredTransactions"
          :columns="columns"
          :loading="ledgerStore.isLoading"
          :empty-text="t('views.transactions.empty')"
          :get-row-id="(txn: LedgerTxn) => txn.id"
          :pagination="pagination"
          clickable
          @row-click="(row) => row.toggleExpanded()"
          @update:page="goToPage"
          @update:page-size="changePageSize"
        >
          <template #txnDate-header="{ column }">
            <span class="inline-flex items-center gap-1.5">
              {{ column.columnDef.header }}
              <UIcon name="i-lucide-chevron-down" class="size-4 text-default" aria-hidden="true" />
            </span>
          </template>
          <template #kind-header="{ column }">
            <span class="inline-flex items-center gap-1.5">
              {{ column.columnDef.header }}
              <UIcon name="i-lucide-chevron-down" class="size-4 text-default" aria-hidden="true" />
            </span>
          </template>
          <template #categories-header="{ column }">
            <span class="inline-flex items-center gap-1.5">
              {{ column.columnDef.header }}
              <UIcon name="i-lucide-chevron-down" class="size-4 text-default" aria-hidden="true" />
            </span>
          </template>
          <template #amount-header="{ column }">
            <span class="inline-flex items-center gap-1.5">
              {{ column.columnDef.header }}
              <UIcon
                name="i-lucide-chevrons-up-down"
                class="size-4 text-default"
                aria-hidden="true"
              />
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
                size="md"
                :icon="allExpanded ? 'i-lucide-fold-vertical' : 'i-lucide-unfold-vertical'"
                :aria-expanded="allExpanded"
                :aria-label="
                  t(
                    allExpanded
                      ? 'views.transactions.rowActions.collapseAll'
                      : 'views.transactions.rowActions.expandAll',
                  )
                "
                :disabled="filteredTransactions.length === 0"
                class="rounded-lg text-default"
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
              {{ getTxnPresentation(row.original.txnKind).label }}
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
                    initialsFromName(
                      row.original.createdBy.firstName,
                      row.original.createdBy.lastName,
                    )
                  "
                  size="lg"
                  aria-hidden="true"
                  :ui="{
                    root: 'size-9 bg-(--pocketr-avatar-bg)',
                    fallback: 'text-sm font-normal text-highlighted',
                  }"
                />
                <span class="sr-only">
                  {{ creatorName(row.original) }}, {{ row.original.createdBy.email }}
                </span>
              </span>
            </UTooltip>
          </template>
          <template #amount-cell="{ row }">
            <span
              class="inline-flex items-center gap-1.5 text-[15px] font-semibold"
              :class="getTxnAppearance(row.original.txnKind).amountClass"
            >
              <UIcon
                v-if="isTransfer(row.original)"
                name="i-lucide-arrow-left-right"
                class="size-4 shrink-0"
                aria-hidden="true"
              />
              {{ signedAmountText(row.original) }}
            </span>
          </template>
          <template #actions-cell="{ row }">
            <UTooltip :text="t('common.actions.delete')">
              <UButton
                color="neutral"
                variant="ghost"
                size="md"
                icon="i-lucide-trash-2"
                :loading="deletingTxnId === row.original.id"
                :disabled="deletingTxnId !== null"
                :aria-label="
                  t('views.transactions.rowActions.delete', {
                    description: row.original.description,
                  })
                "
                class="rounded-lg text-default hover:bg-error/10 hover:text-error dark:hover:text-error-400"
                @click="requestDelete(row.original)"
              />
            </UTooltip>
          </template>
          <template #expand-cell="{ row }">
            <UButton
              color="neutral"
              variant="ghost"
              size="md"
              :icon="row.getIsExpanded() ? 'i-lucide-chevron-down' : 'i-lucide-chevron-left'"
              :aria-expanded="row.getIsExpanded()"
              :aria-label="
                t(
                  row.getIsExpanded()
                    ? 'views.transactions.rowActions.collapse'
                    : 'views.transactions.rowActions.expand',
                  { description: row.original.description },
                )
              "
              class="rounded-lg text-default"
              @click="row.toggleExpanded()"
            />
          </template>
          <template #expanded="{ row }">
            <TransactionDetails :details="detailsFor(row.original)" />
          </template>
          <template #loading>{{ t('views.transactions.loading') }}</template>
        </AppDataTable>

        <template v-else>
          <div
            v-if="ledgerStore.isLoading && filteredTransactions.length === 0"
            class="space-y-2"
            :aria-label="t('views.transactions.loading')"
          >
            <USkeleton v-for="index in 4" :key="index" class="h-16 w-full rounded-xl" />
          </div>
          <p
            v-else-if="filteredTransactions.length === 0"
            class="rounded-xl border border-default bg-default px-4 py-8 text-center text-sm text-muted"
          >
            {{ t('views.transactions.empty') }}
          </p>
          <div v-else class="flex flex-col" :aria-busy="ledgerStore.isLoading">
            <section v-for="group in groups" :key="group.id" class="flex flex-col">
              <h2 class="px-1 pt-2 pb-2 text-sm font-semibold text-highlighted">
                {{ group.label }}
              </h2>
              <ul
                class="divide-y divide-default overflow-hidden rounded-xl border border-default bg-default"
              >
                <li v-for="txn in group.items" :key="txn.id">
                  <button
                    type="button"
                    class="flex w-full items-center gap-3 px-3 py-2.5 text-start outline-primary/25 focus-visible:outline-3 focus-visible:-outline-offset-3"
                    :aria-expanded="Boolean(expanded[txn.id])"
                    :aria-controls="`txn-details-${txn.id}`"
                    @click="toggleRow(txn)"
                  >
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
                        {{ categoryText(txn) || getTxnPresentation(txn.txnKind).label }}
                      </span>
                    </span>
                    <span
                      class="inline-flex shrink-0 items-center gap-1.5 text-[15px] font-semibold whitespace-nowrap"
                      :class="getTxnAppearance(txn.txnKind).amountClass"
                    >
                      <UIcon
                        v-if="isTransfer(txn)"
                        name="i-lucide-arrow-left-right"
                        class="size-4 shrink-0"
                        aria-hidden="true"
                      />
                      {{ signedAmountText(txn) }}
                    </span>
                    <UIcon
                      :name="expanded[txn.id] ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
                      class="size-5 shrink-0 text-default"
                      aria-hidden="true"
                    />
                  </button>
                  <div
                    v-if="expanded[txn.id]"
                    :id="`txn-details-${txn.id}`"
                    class="flex flex-col gap-2.5 px-3 pt-2 pb-3"
                  >
                    <TransactionDetails
                      :details="detailsFor(txn)"
                      :creator="detailsCreator(txn)"
                      :date="
                        group.id === 'today' || group.id === 'yesterday'
                          ? undefined
                          : formatIsoDate(txn.txnDate)
                      "
                    />
                    <UButton
                      block
                      color="error"
                      variant="soft"
                      size="md"
                      icon="i-lucide-trash-2"
                      :label="t('common.actions.delete')"
                      :loading="deletingTxnId === txn.id"
                      :disabled="deletingTxnId !== null"
                      :aria-label="
                        t('views.transactions.rowActions.delete', { description: txn.description })
                      "
                      class="h-10 justify-center rounded-lg"
                      @click="requestDelete(txn)"
                    />
                  </div>
                </li>
              </ul>
            </section>
          </div>
          <AppPaginationBar
            v-if="!ledgerStore.isLoading || filteredTransactions.length > 0"
            :pagination="pagination"
            @update:page="goToPage"
            @update:page-size="changePageSize"
          />
        </template>
      </template>
    </div>

    <AppFormOverlay
      :open="isCreateOpen"
      size="lg"
      cancel-variant="ghost"
      :title="t('views.transactions.create.title')"
      :description="t('views.transactions.create.description')"
      :form-id="CREATE_FORM_ID"
      :submit-label="t('views.transactions.create.submit')"
      :loading="isSubmitting"
      :submit-disabled="!isFormValid"
      @update:open="onCreateOpenChange"
    >
      <TransactionForm
        :id="CREATE_FORM_ID"
        :key="createKey"
        v-model:valid="isFormValid"
        :server-error="submitError"
        @submit="submitTransaction"
      />
    </AppFormOverlay>

    <AppConfirmDialog
      v-model:open="isDeleteDialogOpen"
      :title="t('views.transactions.confirmDelete', { description: deleteTargetDescription })"
      :description="t('views.transactions.delete.description')"
      :confirm-label="t('views.transactions.delete.confirm')"
      :loading="deletingTxnId !== null"
      @confirm="confirmDelete"
    />

    <template #footer>
      <UButton
        block
        icon="i-lucide-plus"
        size="md"
        :label="t('views.transactions.actions.new')"
        class="h-11 justify-center rounded-lg"
        @click="openCreate"
      />
    </template>
  </AppPagePanel>
</template>
