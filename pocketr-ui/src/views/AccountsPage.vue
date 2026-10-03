<script setup lang="ts">
import { HTTPError } from 'ky'
import { useMediaQuery } from '@vueuse/core'
import { computed, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { createAccount, updateAccount } from '@/api/accounts'
import { getAccountBalances } from '@/api/ledger'
import AccountForm from '@/components/forms/AccountForm.vue'
import AccountRenameForm from '@/components/forms/AccountRenameForm.vue'
import FormMessage from '@/components/forms/FormMessage.vue'
import { FIELD_SELECT_UI } from '@/components/forms/fieldStyles'
import AppPagePanel from '@/components/layout/AppPagePanel.vue'
import AppConfirmDialog from '@/components/shared/AppConfirmDialog.vue'
import AppFilterPanel from '@/components/shared/AppFilterPanel.vue'
import AppFiltersToggle from '@/components/shared/AppFiltersToggle.vue'
import AppPageHeading from '@/components/shared/AppPageHeading.vue'
import AppExpandableRow from '@/components/shared/AppExpandableRow.vue'
import AppDataTable from '@/components/shared/AppDataTable.vue'
import AppFormOverlay from '@/components/shared/AppFormOverlay.vue'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useCurrencyStore } from '@/stores/currency'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import type { AppTableColumn } from '@/types/dataTable'
import type { Account, AccountType, CreateAccountRequest } from '@/types/ledger'
import { getAccountAppearance } from '@/utils/accountAppearance'
import { pickPrimaryCurrency, totalsByCurrency } from '@/utils/currencyTotals'
import { formatMinor } from '@/utils/money'
import { APP_ICONS } from '@/utils/appIcons'

const FILTERS_PANEL_ID = 'account-filters'
const CREATE_FORM_ID = 'create-account-form'
const RENAME_FORM_ID = 'rename-account-form'
const ALL = 'ALL'

const { t } = useI18n()
const accountStore = useAccountStore()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()
const householdStore = useHouseholdStore()
const authStore = useAuthStore()
const isDesktop = useMediaQuery('(min-width: 1024px)')

const balances = ref<Map<string, number>>(new Map())
const balancesLoading = ref(false)
const typeFilter = ref<string>(ALL)
const currencyFilter = ref<string>(ALL)

// Rows are expanded by account id on the mobile list, which shows the row actions in place.
const expanded = ref<Record<string, boolean>>({})

// The filters stay out of the way until asked for; the button counts the ones in use.
const isFiltersOpen = ref(false)

const activeFilterCount = computed(
  () => Number(typeFilter.value !== ALL) + Number(currencyFilter.value !== ALL),
)

function clearFilters(): void {
  typeFilter.value = ALL
  currencyFilter.value = ALL
}

const accountTypes: AccountType[] = ['ASSET', 'LIABILITY', 'INCOME', 'EXPENSE']

const filteredAccounts = computed(() => {
  let list = accountStore.activeAccounts
  if (typeFilter.value !== ALL) list = list.filter((a) => a.type === typeFilter.value)
  if (currencyFilter.value !== ALL) list = list.filter((a) => a.currency === currencyFilter.value)
  return list
})

const availableCurrencies = computed(() =>
  [...new Set(accountStore.activeAccounts.map((a) => a.currency))].sort(),
)

// An archived account can take the selected currency out of the list; fall back to all currencies.
watch(availableCurrencies, (codes) => {
  if (currencyFilter.value !== ALL && !codes.includes(currencyFilter.value)) {
    currencyFilter.value = ALL
  }
})

const typeItems = computed(() => [
  { value: ALL, label: t('views.accounts.filters.allTypes') },
  ...accountTypes.map((type) => ({ value: type, label: t(`display.accountTypes.${type}`) })),
])

const currencyItems = computed(() => [
  { value: ALL, label: t('views.accounts.filters.allCurrencies') },
  ...availableCurrencies.value.map((code) => ({ value: code, label: code })),
])

const sharedAccountIds = computed(
  () => new Set(householdStore.sharedAccounts.map((share) => share.accountId)),
)

function isOwnedAccount(account: Account): boolean {
  return account.ownerUserId === authStore.user?.id
}

function balanceText(account: Account): string | null {
  const balance = balances.value.get(account.id)
  if (balance === undefined) return null
  return formatMinor(balance, account.currency, currencyStore.getMinorUnit(account.currency))
}

// --- Summary cards ---

// As on the dashboard, household mode counts only the accounts shared with the household.
const summaryAccounts = computed(() =>
  modeStore.isHousehold
    ? accountStore.activeAccounts.filter((account) => sharedAccountIds.value.has(account.id))
    : accountStore.activeAccounts,
)

// The selected currency, else EUR as on the dashboard (or the first one in use without any EUR account).
const summaryCurrency = computed(() =>
  currencyFilter.value !== ALL
    ? currencyFilter.value
    : pickPrimaryCurrency(availableCurrencies.value),
)

function summarize(type: 'ASSET' | 'LIABILITY') {
  const accounts = summaryAccounts.value.filter(
    (account) =>
      account.type === type &&
      (currencyFilter.value === ALL || account.currency === currencyFilter.value),
  )
  const known = accounts.filter((account) => balances.value.has(account.id))
  const totals = totalsByCurrency(
    known.map((account) => ({
      currency: account.currency,
      amountMinor: balances.value.get(account.id) ?? 0,
    })),
    summaryCurrency.value,
  )
  return {
    complete: known.length === accounts.length,
    primary: totals.find((total) => total.currency === summaryCurrency.value)?.amountMinor ?? 0,
    others: totals.filter((total) => total.currency !== summaryCurrency.value),
  }
}

function formatMoney(amountMinor: number, currency: string): string {
  return formatMinor(amountMinor, currency, currencyStore.getMinorUnit(currency))
}

const summaryCards = computed(() => [
  {
    key: 'available',
    label: t('views.accounts.summary.available'),
    icon: APP_ICONS.asset,
    circleClass: 'bg-(--pocketr-icon-bg)',
    iconClass: 'text-primary',
    ...summarize('ASSET'),
  },
  {
    key: 'debt',
    label: t('views.accounts.summary.debt'),
    icon: APP_ICONS.liability,
    circleClass: 'bg-warning/10',
    iconClass: 'text-(--pocketr-warning-fg)',
    ...summarize('LIABILITY'),
  },
])

// Only the first load swaps the page for placeholders; later reloads keep the rows on screen.
const isInitialLoad = computed(
  () => (accountStore.isLoading && accountStore.accounts.length === 0) || balancesLoading.value,
)

// --- Loading ---

// A newer load supersedes an older one, so a slow earlier response cannot overwrite its balances.
let balancesRequest = 0

async function loadBalances(): Promise<void> {
  const request = (balancesRequest += 1)
  balancesLoading.value = true
  balances.value = new Map()
  const accountIds = accountStore.activeAccounts.map((account) => account.id)

  try {
    const result = await getAccountBalances(
      accountIds,
      undefined,
      modeStore.householdId ?? undefined,
    )
    if (request !== balancesRequest) return
    const loaded = new Map<string, number>()
    for (const balance of result) loaded.set(balance.accountId, balance.balanceMinor)
    balances.value = loaded
  } catch {
    // Balances stay unknown ("...") when they cannot be loaded.
  } finally {
    if (request === balancesRequest) balancesLoading.value = false
  }
}

async function loadAll(): Promise<void> {
  expanded.value = {}
  await Promise.all([accountStore.load(), currencyStore.load()])
  if (modeStore.isHousehold && modeStore.householdId) {
    await householdStore.loadSharedAccounts(modeStore.householdId)
  }
  await loadBalances()
}

onMounted(loadAll)

watch(() => modeStore.viewMode, loadAll, { deep: true })

async function resolveMessage(error: unknown, fallback: string): Promise<string> {
  if (error instanceof HTTPError) {
    const payload = await error.response.json<{ message?: string }>().catch(() => null)
    return payload?.message?.trim() || fallback
  }
  return fallback
}

// --- Create ---

const isCreateOpen = ref(false)
// Remounts the form for every new account, so each one starts from an empty draft.
const createKey = ref(0)
const isFormValid = ref(false)
const isCreating = ref(false)
const createError = ref('')

function openCreate(): void {
  if (isCreating.value) return
  createError.value = ''
  isFormValid.value = false
  createKey.value += 1
  isCreateOpen.value = true
}

function onCreateOpenChange(open: boolean): void {
  if (!open && isCreating.value) return
  isCreateOpen.value = open
}

async function submitCreate(request: CreateAccountRequest): Promise<void> {
  if (isCreating.value) return
  createError.value = ''
  isCreating.value = true
  try {
    await createAccount(request)
    isCreateOpen.value = false
    await loadAll()
  } catch {
    createError.value = t('errors.accounts.create')
  } finally {
    isCreating.value = false
  }
}

// --- Rename ---

const renameTarget = ref<Account | null>(null)
// Kept apart from the target so the overlay text does not blank out while it animates closed.
const renameTargetName = ref('')
const renameDraft = ref('')
const renameError = ref('')
const isRenaming = ref(false)

const isRenameOpen = computed({
  get: () => renameTarget.value !== null,
  set: (open: boolean) => {
    if (!open && !isRenaming.value) renameTarget.value = null
  },
})

function startRename(account: Account): void {
  if (isRenaming.value) return
  renameError.value = ''
  renameDraft.value = account.name
  renameTargetName.value = account.name
  renameTarget.value = account
}

async function submitRename(name: string): Promise<void> {
  const target = renameTarget.value
  if (!target || isRenaming.value) return

  renameError.value = ''
  isRenaming.value = true
  try {
    await updateAccount(target.id, { name })
    renameTarget.value = null
    await accountStore.load()
  } catch {
    renameError.value = t('errors.accounts.rename')
  } finally {
    isRenaming.value = false
  }
}

// --- Archive ---

const archiveTarget = ref<Account | null>(null)
// Kept apart from the target so the dialog text does not blank out while it animates closed.
const archiveTargetName = ref('')
const archiveError = ref('')
const archivingAccountId = ref<string | null>(null)
// An archived account's button is gone, so focus returns to the New account button instead.
const archiveReturnFocus = ref<HTMLElement | null>(null)
const desktopNewButton = useTemplateRef<{ $el: HTMLElement }>('desktopNewButton')
const footerNewButton = useTemplateRef<{ $el: HTMLElement }>('footerNewButton')

const isArchiveOpen = computed({
  get: () => archiveTarget.value !== null,
  set: (open: boolean) => {
    if (!open && archivingAccountId.value === null) archiveTarget.value = null
  },
})

function startArchive(account: Account): void {
  if (archivingAccountId.value !== null) return
  archiveError.value = ''
  archiveReturnFocus.value = null
  archiveTargetName.value = account.name
  archiveTarget.value = account
}

async function confirmArchive(): Promise<void> {
  const account = archiveTarget.value
  if (!account || archivingAccountId.value !== null) return

  archivingAccountId.value = account.id
  try {
    await accountStore.archiveAccount(account.id)
    archiveReturnFocus.value =
      (isDesktop.value ? desktopNewButton : footerNewButton).value?.$el ?? null
    archiveTarget.value = null
    await loadAll()
  } catch (error: unknown) {
    archiveTarget.value = null
    archiveError.value = await resolveMessage(error, t('errors.accounts.archive'))
  } finally {
    archivingAccountId.value = null
  }
}

// --- Presentation ---

function toggleRow(account: Account): void {
  expanded.value = { ...expanded.value, [account.id]: !expanded.value[account.id] }
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
      header: t('common.table.currency'),
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

// Compact controls next to a pointer, full touch targets below `lg`.
const filterUi = computed(() => ({
  ...FIELD_SELECT_UI,
  base: [FIELD_SELECT_UI.base, isDesktop.value ? 'h-9' : 'h-11'],
}))
</script>

<template>
  <AppPagePanel :title="t('views.accounts.title')">
    <div class="flex min-w-0 flex-col gap-3 lg:gap-5">
      <AppPageHeading :title="t('views.accounts.title')" :subtitle="t('views.accounts.subtitle')">
        <template #actions>
          <AppFiltersToggle
            v-model:open="isFiltersOpen"
            :panel-id="FILTERS_PANEL_ID"
            :count="activeFilterCount"
          />
          <UButton
            ref="desktopNewButton"
            :icon="APP_ICONS.add"
            size="md"
            :label="t('views.accounts.actions.new')"
            class="hidden h-10 rounded-lg px-5 lg:inline-flex"
            @click="openCreate"
          />
        </template>
      </AppPageHeading>

      <AppFilterPanel
        v-if="isFiltersOpen"
        :id="FILTERS_PANEL_ID"
        :count="activeFilterCount"
        @clear="clearFilters"
      >
        <div class="min-w-0 lg:w-fit lg:max-w-72 lg:min-w-44">
          <USelect
            v-model="typeFilter"
            :items="typeItems"
            size="lg"
            :aria-label="t('common.formHints.filterType')"
            class="w-full"
            :ui="filterUi"
          />
        </div>
        <div class="min-w-0 lg:w-fit lg:max-w-72 lg:min-w-44">
          <USelect
            v-model="currencyFilter"
            :items="currencyItems"
            size="lg"
            :aria-label="t('common.formHints.filterCurrency')"
            class="w-full"
            :ui="filterUi"
          />
        </div>
      </AppFilterPanel>

      <FormMessage v-if="archiveError" tone="error" :message="archiveError" />
      <FormMessage v-if="accountStore.error" tone="error" :message="accountStore.error" />

      <template v-else>
        <div class="grid gap-3 lg:grid-cols-2 lg:gap-4">
          <UCard
            v-for="card in summaryCards"
            :key="card.key"
            :ui="{
              root: 'rounded-xl',
              body: 'flex items-center gap-4 p-3.5 sm:p-3.5 lg:gap-5 lg:p-5',
            }"
          >
            <span
              class="flex size-[58px] shrink-0 items-center justify-center rounded-full lg:size-[74px]"
              :class="card.circleClass"
            >
              <UIcon
                :name="card.icon"
                class="size-7 lg:size-8"
                :class="card.iconClass"
                aria-hidden="true"
              />
            </span>
            <div class="min-w-0 flex-1">
              <p class="text-sm leading-5 text-muted">{{ card.label }}</p>
              <template v-if="isInitialLoad">
                <USkeleton class="mt-1 h-8 w-40" />
                <USkeleton class="mt-1.5 h-4 w-24" />
              </template>
              <template v-else>
                <p
                  class="text-[26px] leading-8 font-bold text-highlighted tabular-nums lg:text-[32px] lg:leading-10"
                >
                  <template v-if="card.complete">
                    {{ formatMoney(card.primary, summaryCurrency) }}
                  </template>
                  <template v-else>
                    ...
                    <span class="sr-only">{{ t('common.feedback.loading') }}</span>
                  </template>
                </p>
                <p class="text-sm leading-5 text-muted">
                  {{ t('views.accounts.summary.currencyAccounts', { currency: summaryCurrency }) }}
                </p>
                <div v-if="card.complete && card.others.length" class="flex flex-wrap gap-1.5 pt-1">
                  <UBadge
                    v-for="amount in card.others"
                    :key="amount.currency"
                    color="neutral"
                    variant="outline"
                    size="sm"
                  >
                    {{ formatMoney(amount.amountMinor, amount.currency) }}
                  </UBadge>
                </div>
              </template>
            </div>
          </UCard>
        </div>
      </template>

      <template v-if="!accountStore.error">
        <AppDataTable
          v-if="isDesktop"
          :data="filteredAccounts"
          :columns="columns"
          :loading="accountStore.isLoading"
          :empty-text="t('views.accounts.empty')"
          :get-row-id="(account: Account) => account.id"
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
              <UTooltip :text="t('common.actions.edit')">
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="md"
                  :icon="APP_ICONS.edit"
                  :aria-label="t('views.accounts.rowActions.edit', { name: row.original.name })"
                  class="rounded-lg text-default"
                  @click="startRename(row.original)"
                />
              </UTooltip>
              <UTooltip :text="t('common.actions.archive')">
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="md"
                  :icon="APP_ICONS.remove"
                  :loading="archivingAccountId === row.original.id"
                  :disabled="archivingAccountId !== null"
                  :aria-label="t('views.accounts.archive.actionLabel', { name: row.original.name })"
                  class="rounded-lg text-default hover:bg-error/10 hover:text-error dark:hover:text-error-400"
                  @click="startArchive(row.original)"
                />
              </UTooltip>
            </div>
            <span
              v-else
              class="inline-block max-w-36 text-start text-xs whitespace-normal text-muted"
            >
              {{ t('views.accounts.sharedFromOtherMember') }}
            </span>
          </template>
          <template #loading>{{ t('views.accounts.loading') }}</template>
        </AppDataTable>

        <template v-else>
          <div
            v-if="accountStore.isLoading && filteredAccounts.length === 0"
            class="space-y-2"
            :aria-label="t('views.accounts.loading')"
          >
            <USkeleton v-for="index in 4" :key="index" class="h-16 w-full rounded-xl" />
          </div>
          <p
            v-else-if="filteredAccounts.length === 0"
            class="rounded-xl border border-default bg-default px-4 py-8 text-center text-sm text-muted"
          >
            {{ t('views.accounts.empty') }}
          </p>
          <ul
            v-else
            class="divide-y divide-default overflow-hidden rounded-xl border border-default bg-default"
            :aria-busy="accountStore.isLoading"
          >
            <li v-for="account in filteredAccounts" :key="account.id">
              <AppExpandableRow
                :panel-id="`account-actions-${account.id}`"
                :expanded="Boolean(expanded[account.id])"
                @toggle="toggleRow(account)"
              >
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
                <template #details>
                  <div v-if="isOwnedAccount(account)" class="grid grid-cols-2 gap-2.5">
                    <UButton
                      block
                      color="neutral"
                      variant="soft"
                      size="md"
                      :icon="APP_ICONS.edit"
                      :label="t('common.actions.edit')"
                      :aria-label="t('views.accounts.rowActions.edit', { name: account.name })"
                      class="h-11 justify-center rounded-lg"
                      @click="startRename(account)"
                    />
                    <UButton
                      block
                      color="error"
                      variant="soft"
                      size="md"
                      :icon="APP_ICONS.remove"
                      :label="t('common.actions.archive')"
                      :loading="archivingAccountId === account.id"
                      :disabled="archivingAccountId !== null"
                      :aria-label="t('views.accounts.archive.actionLabel', { name: account.name })"
                      class="h-11 justify-center rounded-lg"
                      @click="startArchive(account)"
                    />
                  </div>
                  <p v-else class="text-sm text-muted">
                    {{ t('views.accounts.sharedFromOtherMember') }}
                  </p>
                </template>
              </AppExpandableRow>
            </li>
          </ul>
        </template>
      </template>
    </div>

    <AppFormOverlay
      :open="isCreateOpen"
      size="wide"
      :title="t('views.accounts.create.title')"
      :description="t('views.accounts.create.descriptions.main')"
      :form-id="CREATE_FORM_ID"
      :submit-label="t('views.accounts.create.submit')"
      :loading="isCreating"
      :submit-disabled="!isFormValid"
      @update:open="onCreateOpenChange"
    >
      <AccountForm
        :id="CREATE_FORM_ID"
        :key="createKey"
        v-model:valid="isFormValid"
        :server-error="createError"
        @submit="submitCreate"
      />
    </AppFormOverlay>

    <AppFormOverlay
      v-model:open="isRenameOpen"
      :title="t('views.accounts.rename.title')"
      :description="t('views.accounts.rename.description', { name: renameTargetName })"
      :form-id="RENAME_FORM_ID"
      :submit-label="t('common.actions.save')"
      :loading="isRenaming"
      :submit-disabled="!renameDraft.trim()"
    >
      <AccountRenameForm
        :id="RENAME_FORM_ID"
        v-model:name="renameDraft"
        :server-error="renameError"
        @submit="submitRename"
      />
    </AppFormOverlay>

    <AppConfirmDialog
      v-model:open="isArchiveOpen"
      :title="t('views.accounts.archive.title')"
      :description="t('views.accounts.archive.description', { name: archiveTargetName })"
      :confirm-label="t('views.accounts.archive.confirm')"
      :loading="archivingAccountId !== null"
      :return-focus-to="archiveReturnFocus"
      @confirm="confirmArchive"
    />

    <template #footer>
      <UButton
        ref="footerNewButton"
        block
        :icon="APP_ICONS.add"
        size="md"
        :label="t('views.accounts.actions.new')"
        class="h-11 justify-center rounded-lg"
        @click="openCreate"
      />
    </template>
  </AppPagePanel>
</template>
