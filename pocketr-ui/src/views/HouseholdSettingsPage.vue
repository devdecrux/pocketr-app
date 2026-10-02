<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import FormMessage from '@/components/forms/FormMessage.vue'
import AppPagePanel from '@/components/layout/AppPagePanel.vue'
import { translate } from '@/i18n/translate'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import type { HouseholdMember, HouseholdRole, MembershipStatus } from '@/types/household'
import type { Account, AccountType } from '@/types/ledger'
import { initialsFromName } from '@/utils/initials'

type HouseholdTab = 'members' | 'accounts'

const route = useRoute()
const { t } = useI18n()
const householdStore = useHouseholdStore()
const accountStore = useAccountStore()
const authStore = useAuthStore()
const isDesktop = useMediaQuery('(min-width: 1024px)')

const householdId = computed(() => route.params.householdId as string)

// The store keeps the last household it loaded, so only a matching one belongs to this page.
const household = computed(() =>
  householdStore.currentHousehold?.id === householdId.value
    ? householdStore.currentHousehold
    : null,
)
const myRole = computed(() => (household.value ? householdStore.currentMembership?.role : null))

const isLoaded = ref(false)
const loadError = ref('')
const sharesLoadError = ref('')

onMounted(async () => {
  await Promise.all([
    householdStore.loadHousehold(householdId.value),
    householdStore.loadSharedAccounts(householdId.value),
    accountStore.load(),
  ])
  if (!household.value) {
    loadError.value = householdStore.error ?? translate('errors.households.loadDetails')
  } else if (householdStore.error) {
    sharesLoadError.value = householdStore.error
  }
  isLoaded.value = true
})

const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

function formatDate(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : dateFormatter.format(date)
}

function displayPerson(
  firstName: string | null | undefined,
  lastName: string | null | undefined,
  email: string | null | undefined,
): string {
  const name = `${firstName?.trim() ?? ''} ${lastName?.trim() ?? ''}`.trim()
  return name || (email?.trim() ?? translate('common.states.unknownUser'))
}

function memberInitials(member: HouseholdMember): string {
  const initials = initialsFromName(member.firstName, member.lastName)
  return initials === '?' ? member.email.trim().charAt(0).toUpperCase() || '?' : initials
}

const ROLE_BADGE: Record<
  HouseholdRole,
  { color: 'primary' | 'neutral'; variant: 'soft' | 'outline' }
> = {
  OWNER: { color: 'primary', variant: 'soft' },
  ADMIN: { color: 'primary', variant: 'outline' },
  MEMBER: { color: 'neutral', variant: 'soft' },
}

const STATUS_DOT: Record<MembershipStatus, string> = {
  ACTIVE: 'bg-success',
  INVITED: 'bg-warning',
}

/* Tabs (below lg) */

const activeTab = ref<HouseholdTab>('members')
const tabItems = computed(() => [
  { label: t('views.householdSettings.tabs.members'), value: 'members' },
  { label: t('views.householdSettings.tabs.accounts'), value: 'accounts' },
])

function onTabChange(value: string | number): void {
  activeTab.value = value === 'accounts' ? 'accounts' : 'members'
}

/* Rollover day */

const rolloverForm = ref<HTMLFormElement | null>(null)
const selectedRolloverDay = ref<number | null>(1)
const isSavingRollover = ref(false)
const rolloverFieldError = ref('')
const rolloverError = ref('')
const rolloverSuccess = ref('')

watch(
  () => household.value?.rolloverDay,
  (rolloverDay) => {
    if (rolloverDay) selectedRolloverDay.value = rolloverDay
  },
  { immediate: true },
)

const hasRolloverChanged = computed(
  () => selectedRolloverDay.value !== household.value?.rolloverDay,
)

async function focusRolloverInput(): Promise<void> {
  await nextTick()
  rolloverForm.value?.querySelector('input')?.focus()
}

async function saveRolloverDay(): Promise<void> {
  if (!householdStore.isOwnerOrAdmin || isSavingRollover.value || !hasRolloverChanged.value) return

  rolloverFieldError.value = ''
  rolloverError.value = ''
  rolloverSuccess.value = ''

  const rolloverDay = selectedRolloverDay.value
  if (
    rolloverDay === null ||
    !Number.isInteger(rolloverDay) ||
    rolloverDay < 1 ||
    rolloverDay > 31
  ) {
    rolloverFieldError.value = translate('validation.rollover.dayRange')
    await focusRolloverInput()
    return
  }

  isSavingRollover.value = true
  try {
    const success = await householdStore.updateRolloverDay(householdId.value, rolloverDay)
    if (success) {
      selectedRolloverDay.value = householdStore.currentHousehold?.rolloverDay ?? 1
      rolloverSuccess.value = translate('views.householdSettings.rollover.updated')
    } else {
      rolloverError.value = householdStore.error ?? translate('errors.households.updateRollover')
    }
  } finally {
    isSavingRollover.value = false
  }
  // Save is disabled again once the day matches, so focus goes back to the field.
  await focusRolloverInput()
}

/* Invite */

const inviteForm = ref<HTMLFormElement | null>(null)
const inviteEmail = ref('')
const isInviting = ref(false)
const inviteFieldError = ref('')
const inviteError = ref('')
const inviteSuccess = ref('')

async function focusInviteInput(): Promise<void> {
  await nextTick()
  inviteForm.value?.querySelector('input')?.focus()
}

async function handleInvite(): Promise<void> {
  if (isInviting.value) return

  inviteFieldError.value = ''
  inviteError.value = ''
  inviteSuccess.value = ''

  const email = inviteEmail.value.trim()
  if (!email) {
    inviteFieldError.value = translate('validation.household.emailRequired')
    await focusInviteInput()
    return
  }

  isInviting.value = true
  try {
    const success = await householdStore.inviteMember(householdId.value, { email })
    if (success) {
      inviteSuccess.value = translate('views.householdSettings.invite.success', { email })
      inviteEmail.value = ''
    } else {
      inviteError.value = householdStore.error ?? translate('errors.households.sendInvitation')
    }
  } catch (error: unknown) {
    const payload =
      error instanceof HTTPError
        ? await error.response.json<{ message?: string }>().catch(() => null)
        : null
    inviteError.value = payload?.message?.trim() || translate('errors.households.sendInvitation')
  } finally {
    isInviting.value = false
  }
  await focusInviteInput()
}

/* Account sharing */

const sharedAtById = computed(
  () => new Map(householdStore.sharedAccounts.map((share) => [share.accountId, share.sharedAt])),
)

function isAccountShared(accountId: string): boolean {
  return sharedAtById.value.has(accountId)
}

// Own accounts, excluding equity; archived ones only while still shared so they can be unshared.
const myAccounts = computed(() => {
  const userId = authStore.user?.id
  if (userId == null) return []
  return accountStore.accounts.filter(
    (account) =>
      account.ownerUserId === userId &&
      account.type !== 'EQUITY' &&
      (account.status === 'ACTIVE' || isAccountShared(account.id)),
  )
})

const myAccountsByType = computed(() => {
  const groups = new Map<AccountType, Account[]>()
  for (const account of myAccounts.value) {
    const list = groups.get(account.type) ?? []
    list.push(account)
    groups.set(account.type, list)
  }
  return groups
})

const yourAccountsHeading = ref<HTMLElement | null>(null)
const sharingAccountId = ref<string | null>(null)
const shareError = ref('')

function shareButtonId(accountId: string): string {
  return `household-share-${accountId}`
}

async function toggleShareAccount(account: Account): Promise<void> {
  if (sharingAccountId.value === account.id) return

  const wasShared = isAccountShared(account.id)
  shareError.value = ''
  sharingAccountId.value = account.id
  try {
    const success = wasShared
      ? await householdStore.unshareAccount(householdId.value, account.id)
      : await householdStore.shareAccount(householdId.value, account.id)
    if (!success) {
      shareError.value =
        householdStore.error ??
        translate(wasShared ? 'errors.households.unshareAccount' : 'errors.households.shareAccount')
    }
  } finally {
    sharingAccountId.value = null
  }

  // An unshared archived account leaves the list, so its button can no longer take focus back.
  await nextTick()
  const button = document.getElementById(shareButtonId(account.id))
  if (button) {
    button.focus()
    button.closest('li')?.scrollIntoView({ block: 'nearest' })
  } else {
    yourAccountsHeading.value?.focus()
  }
}

const cardUi = { root: 'flex min-w-0 flex-col rounded-xl', body: 'flex flex-1 flex-col p-4 sm:p-4' }
const cardTitleClass = 'text-base font-semibold text-highlighted'
const cardDescriptionClass = 'mt-0.5 text-sm text-muted'
const scrollAreaClass =
  'mt-3 overflow-y-auto overscroll-contain border-t border-default outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-inset'
const accountsScrollClass = [scrollAreaClass, 'max-h-72']
const tileClass =
  'flex size-9 shrink-0 items-center justify-center rounded-full bg-(--pocketr-icon-bg) text-default dark:bg-(--pocketr-avatar-bg)'
const fieldUi = { label: 'text-sm text-highlighted' }
const inputUi = { base: 'h-10 rounded-lg bg-(--pocketr-field-bg) text-sm' }
const actionButtonClass = 'w-full justify-center rounded-lg lg:w-auto'
</script>

<template>
  <AppPagePanel :title="t('views.householdSettings.title')">
    <div class="flex min-w-0 flex-col gap-3 lg:gap-4">
      <div class="min-w-0">
        <USkeleton
          v-if="!isLoaded && !household"
          class="h-8 w-56 max-w-full rounded-lg"
          aria-hidden="true"
        />
        <div v-else class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
          <component
            :is="isDesktop ? 'h2' : 'h1'"
            class="min-w-0 truncate text-[22px] leading-8 font-bold text-highlighted lg:text-2xl"
          >
            {{ household?.name ?? t('views.householdSettings.title') }}
          </component>
          <UBadge
            v-if="myRole"
            :color="ROLE_BADGE[myRole].color"
            :variant="ROLE_BADGE[myRole].variant"
            :label="t(`display.householdRoles.${myRole}`)"
            class="shrink-0 rounded-full px-2.5"
          />
        </div>
        <p v-if="household" class="text-sm text-muted">
          {{
            t('views.householdSettings.header.created', { date: formatDate(household.createdAt) })
          }}
        </p>
      </div>

      <FormMessage v-if="loadError" tone="error" :message="loadError" />

      <template v-else>
        <UTabs
          v-if="!isDesktop"
          :model-value="activeTab"
          :items="tabItems"
          :content="false"
          variant="link"
          color="primary"
          class="w-full"
          :ui="{ list: 'w-full', trigger: 'flex-1 justify-center py-2.5 text-sm' }"
          @update:model-value="onTabChange"
        />

        <div class="grid gap-3 lg:grid-cols-2 lg:gap-4">
          <div
            v-show="isDesktop || activeTab === 'members'"
            :role="isDesktop ? undefined : 'tabpanel'"
            :aria-label="isDesktop ? undefined : t('views.householdSettings.tabs.members')"
            class="grid min-w-0 gap-3 lg:contents"
          >
            <UCard :ui="cardUi" class="lg:col-start-1 lg:row-start-1">
              <form
                ref="rolloverForm"
                class="flex flex-1 flex-col gap-3"
                novalidate
                @submit.prevent="saveRolloverDay"
              >
                <FormMessage v-if="rolloverError" tone="error" :message="rolloverError" />
                <FormMessage
                  v-else-if="rolloverSuccess"
                  tone="success"
                  :message="rolloverSuccess"
                />

                <div class="flex flex-1 flex-col gap-3 lg:flex-row lg:items-end lg:gap-4">
                  <UFormField
                    name="rolloverDay"
                    :label="t('views.householdSettings.rollover.day')"
                    :error="rolloverFieldError || undefined"
                    :ui="{
                      root: 'flex flex-col lg:self-stretch',
                      label: cardTitleClass,
                      labelWrapper: 'justify-start',
                      hint: 'flex items-center',
                      container: 'mt-3 lg:mt-auto lg:pt-3',
                    }"
                    class="min-w-0"
                  >
                    <template #hint>
                      <UTooltip
                        v-if="isDesktop"
                        :text="t('views.householdSettings.rollover.help')"
                        :content="{ side: 'top' }"
                        :ui="{ content: 'h-auto max-w-60 py-1.5', text: 'whitespace-normal' }"
                      >
                        <UButton
                          icon="i-lucide-info"
                          color="neutral"
                          variant="ghost"
                          size="xs"
                          :aria-label="t('views.householdSettings.rollover.helpLabel')"
                          class="-my-1"
                        />
                      </UTooltip>
                      <!-- Tooltips do not open on touch, so small screens get a tap-to-open popover. -->
                      <UPopover v-else :content="{ side: 'top' }" arrow>
                        <UButton
                          icon="i-lucide-info"
                          color="neutral"
                          variant="ghost"
                          size="xs"
                          :aria-label="t('views.householdSettings.rollover.helpLabel')"
                          class="-my-1"
                        />
                        <template #content>
                          <p class="max-w-64 p-3 text-xs text-default">
                            {{ t('views.householdSettings.rollover.help') }}
                          </p>
                        </template>
                      </UPopover>
                    </template>
                    <UInputNumber
                      v-model="selectedRolloverDay"
                      :min="1"
                      :max="31"
                      :step="1"
                      color="neutral"
                      class="w-28"
                      aria-describedby="household-rollover-help"
                      :disabled="!householdStore.isOwnerOrAdmin"
                      :ui="inputUi"
                    />
                  </UFormField>
                  <p id="household-rollover-help" class="sr-only">
                    {{ t('views.householdSettings.rollover.help') }}
                  </p>

                  <UButton
                    v-if="householdStore.isOwnerOrAdmin"
                    type="submit"
                    size="md"
                    :label="
                      isSavingRollover ? t('common.feedback.saving') : t('common.actions.save')
                    "
                    :loading="isSavingRollover"
                    :disabled="!hasRolloverChanged"
                    :class="[actionButtonClass, 'lg:ms-auto']"
                  />
                </div>
              </form>
            </UCard>

            <UCard :ui="cardUi" class="lg:col-span-2 lg:row-start-2">
              <h3 :class="cardTitleClass">{{ t('views.householdSettings.members.title') }}</h3>

              <div
                v-if="!isLoaded"
                role="status"
                :aria-label="t('views.householdSettings.members.loading')"
                class="mt-3 space-y-3"
              >
                <USkeleton v-for="index in 3" :key="index" class="h-10 w-full rounded-lg" />
              </div>
              <div
                v-else-if="household?.members.length"
                role="region"
                tabindex="0"
                :aria-label="t('views.householdSettings.members.title')"
                :class="[scrollAreaClass, 'max-h-96']"
              >
                <ul class="divide-y divide-default">
                  <li
                    v-for="member in household.members"
                    :key="member.userId"
                    class="flex min-w-0 items-center gap-3 py-2.5"
                  >
                    <UAvatar
                      :text="memberInitials(member)"
                      :ui="{
                        root: 'size-9 shrink-0 bg-(--pocketr-avatar-bg)',
                        fallback: 'text-sm font-normal text-highlighted',
                      }"
                      aria-hidden="true"
                    />
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium text-highlighted">
                        {{ displayPerson(member.firstName, member.lastName, member.email) }}
                      </p>
                      <p
                        v-if="
                          displayPerson(member.firstName, member.lastName, member.email) !==
                          member.email
                        "
                        class="truncate text-xs text-muted"
                      >
                        {{ member.email }}
                      </p>
                      <p v-if="member.joinedAt" class="truncate text-xs text-muted">
                        {{
                          t('views.householdSettings.members.joined', {
                            date: formatDate(member.joinedAt),
                          })
                        }}
                      </p>
                    </div>
                    <div
                      class="flex shrink-0 flex-col items-end gap-1 sm:flex-row sm:items-center sm:gap-3"
                    >
                      <UBadge
                        :color="ROLE_BADGE[member.role].color"
                        :variant="ROLE_BADGE[member.role].variant"
                        :label="t(`display.householdRoles.${member.role}`)"
                        class="rounded-full px-2.5"
                      />
                      <span class="flex items-center gap-1.5 text-xs text-muted sm:min-w-16">
                        <span
                          :class="['size-2 shrink-0 rounded-full', STATUS_DOT[member.status]]"
                          aria-hidden="true"
                        />
                        {{ t(`display.memberStatuses.${member.status}`) }}
                      </span>
                    </div>
                  </li>
                </ul>
              </div>
              <p v-else class="mt-3 py-6 text-center text-sm text-muted">
                {{ t('views.householdSettings.members.empty') }}
              </p>
            </UCard>

            <UCard
              v-if="householdStore.isOwnerOrAdmin"
              :ui="cardUi"
              class="lg:col-start-2 lg:row-start-1"
            >
              <h3 :class="cardTitleClass">{{ t('views.householdSettings.invite.title') }}</h3>
              <p :class="cardDescriptionClass">
                {{ t('views.householdSettings.invite.description') }}
              </p>

              <form
                ref="inviteForm"
                class="mt-auto grid gap-3 pt-3"
                novalidate
                @submit.prevent="handleInvite"
              >
                <FormMessage v-if="inviteError" tone="error" :message="inviteError" />
                <FormMessage v-else-if="inviteSuccess" tone="success" :message="inviteSuccess" />

                <div class="flex flex-col gap-3 lg:flex-row lg:items-start">
                  <UFormField
                    name="inviteEmail"
                    :label="t('common.fields.email')"
                    :error="inviteFieldError || undefined"
                    :ui="{ ...fieldUi, label: 'sr-only', container: 'mt-0' }"
                    class="min-w-0 flex-1"
                  >
                    <UInput
                      v-model="inviteEmail"
                      type="email"
                      autocomplete="off"
                      icon="i-lucide-mail"
                      :placeholder="t('views.householdSettings.invite.placeholder')"
                      class="w-full"
                      :ui="inputUi"
                    />
                  </UFormField>
                  <UButton
                    type="submit"
                    size="md"
                    :label="
                      isInviting
                        ? t('common.feedback.sending')
                        : t('views.householdSettings.invite.sendAction')
                    "
                    :loading="isInviting"
                    :disabled="!inviteEmail.trim()"
                    :class="[actionButtonClass, 'lg:h-10']"
                  />
                </div>
              </form>
            </UCard>
          </div>

          <div
            v-show="isDesktop || activeTab === 'accounts'"
            :role="isDesktop ? undefined : 'tabpanel'"
            :aria-label="isDesktop ? undefined : t('views.householdSettings.tabs.accounts')"
            class="grid min-w-0 gap-3 lg:contents"
          >
            <UCard :ui="cardUi" class="lg:col-start-1 lg:row-start-3">
              <h3 :class="cardTitleClass">
                {{ t('views.householdSettings.accounts.sharedTitle') }}
              </h3>
              <p :class="cardDescriptionClass">
                {{ t('views.householdSettings.accounts.sharedDescription') }}
              </p>

              <FormMessage
                v-if="sharesLoadError"
                tone="error"
                :message="sharesLoadError"
                class="mt-3"
              />
              <div v-else-if="!isLoaded" class="mt-3 space-y-3" aria-hidden="true">
                <USkeleton v-for="index in 2" :key="index" class="h-10 w-full rounded-lg" />
              </div>
              <div
                v-else-if="householdStore.sharedAccounts.length"
                role="region"
                tabindex="0"
                :aria-label="t('views.householdSettings.accounts.sharedTitle')"
                :class="accountsScrollClass"
              >
                <ul class="divide-y divide-default">
                  <li
                    v-for="share in householdStore.sharedAccounts"
                    :key="share.accountId"
                    class="flex min-w-0 items-center gap-3 py-2.5"
                  >
                    <span :class="tileClass" aria-hidden="true">
                      <UIcon name="i-lucide-wallet" class="size-4.5" />
                    </span>
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium text-highlighted">
                        {{ share.accountName }}
                      </p>
                      <p class="truncate text-xs text-muted">
                        {{
                          t('views.householdSettings.accounts.sharedBy', {
                            name: displayPerson(
                              share.ownerFirstName,
                              share.ownerLastName,
                              share.ownerEmail,
                            ),
                          })
                        }}
                      </p>
                    </div>
                    <span class="shrink-0 text-xs whitespace-nowrap text-muted tabular-nums">
                      {{ formatDate(share.sharedAt) }}
                    </span>
                  </li>
                </ul>
              </div>
              <p v-else class="mt-3 py-6 text-center text-sm text-muted">
                {{ t('views.householdSettings.accounts.sharedEmpty') }}
              </p>
            </UCard>

            <UCard :ui="cardUi" class="lg:col-start-2 lg:row-start-3">
              <h3 ref="yourAccountsHeading" tabindex="-1" :class="[cardTitleClass, 'outline-none']">
                {{ t('views.householdSettings.accounts.yoursTitle') }}
              </h3>
              <p :class="cardDescriptionClass">
                {{ t('views.householdSettings.accounts.yoursDescription') }}
              </p>

              <FormMessage v-if="shareError" tone="error" :message="shareError" class="mt-3" />
              <FormMessage
                v-if="accountStore.error"
                tone="error"
                :message="accountStore.error"
                class="mt-3"
              />
              <div v-else-if="!isLoaded" class="mt-3 space-y-3" aria-hidden="true">
                <USkeleton v-for="index in 3" :key="index" class="h-10 w-full rounded-lg" />
              </div>
              <p
                v-else-if="myAccounts.length === 0"
                class="mt-3 py-6 text-center text-sm text-muted"
              >
                {{ t('views.householdSettings.accounts.emptyMine') }}
              </p>
              <div v-else :class="accountsScrollClass">
                <section
                  v-for="[type, accounts] in myAccountsByType"
                  :key="type"
                  class="border-default not-first:border-t"
                >
                  <h4
                    class="sticky top-0 z-10 border-b border-default bg-default py-1.5 text-xs font-medium text-muted uppercase"
                  >
                    {{ t(`display.accountTypes.${type}`) }}
                  </h4>
                  <ul class="divide-y divide-default">
                    <li
                      v-for="account in accounts"
                      :key="account.id"
                      class="flex min-w-0 items-center gap-3 py-2"
                    >
                      <span :class="tileClass" aria-hidden="true">
                        <UIcon name="i-lucide-wallet" class="size-4.5" />
                      </span>
                      <div class="min-w-0 flex-1">
                        <p class="flex min-w-0 items-center gap-2">
                          <span class="truncate text-sm font-medium text-highlighted">
                            {{ account.name }}
                          </span>
                          <UBadge
                            v-if="account.status === 'ARCHIVED'"
                            color="neutral"
                            variant="outline"
                            size="sm"
                            :label="t('common.states.archived')"
                            class="shrink-0 rounded-full"
                          />
                        </p>
                        <p class="truncate text-xs text-muted">
                          <span class="sm:hidden">{{ account.currency }} · </span>
                          <template v-if="isAccountShared(account.id)">
                            {{
                              t('views.householdSettings.accounts.sharedAt', {
                                date: formatDate(sharedAtById.get(account.id)),
                              })
                            }}
                          </template>
                          <span v-else class="sm:hidden">
                            {{ t('views.householdSettings.accounts.privateBadge') }}
                          </span>
                        </p>
                      </div>
                      <span class="hidden w-10 shrink-0 text-xs text-muted sm:block">
                        {{ account.currency }}
                      </span>
                      <span
                        class="hidden min-w-18 shrink-0 items-center gap-1.5 text-xs text-muted sm:flex"
                      >
                        <span
                          :class="[
                            'size-2 shrink-0 rounded-full',
                            isAccountShared(account.id) ? 'bg-success' : 'bg-(--ui-text-dimmed)',
                          ]"
                          aria-hidden="true"
                        />
                        {{
                          isAccountShared(account.id)
                            ? t('views.householdSettings.accounts.sharedBadge')
                            : t('views.householdSettings.accounts.privateBadge')
                        }}
                      </span>
                      <UButton
                        :id="shareButtonId(account.id)"
                        size="md"
                        :color="isAccountShared(account.id) ? 'error' : 'primary'"
                        :variant="isAccountShared(account.id) ? 'solid' : 'outline'"
                        :label="
                          isAccountShared(account.id)
                            ? t('common.actions.unshare')
                            : t('common.actions.share')
                        "
                        :aria-label="
                          t(
                            isAccountShared(account.id)
                              ? 'views.householdSettings.accounts.unshareAction'
                              : 'views.householdSettings.accounts.shareAction',
                            { name: account.name },
                          )
                        "
                        :loading="sharingAccountId === account.id"
                        class="shrink-0 justify-center rounded-lg"
                        @click="toggleShareAccount(account)"
                      />
                    </li>
                  </ul>
                </section>
              </div>
            </UCard>
          </div>
        </div>
      </template>
    </div>
  </AppPagePanel>
</template>
