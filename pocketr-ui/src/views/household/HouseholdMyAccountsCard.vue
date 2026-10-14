<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import { CARD_DESCRIPTION_CLASS, CARD_TITLE_CLASS, CARD_UI } from '@/styles/card'
import { translate } from '@/i18n/translate'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import type { Account, AccountType } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { formatTimestampDate } from '@/utils/dates'
import {
  ACCOUNT_TILE_CLASS,
  ACCOUNTS_SCROLL_CLASS,
  LIST_EMPTY_CLASS,
  LIST_ROW_TITLE_CLASS,
} from '@/styles/household'
import { useCurrentHousehold } from './useCurrentHousehold'

defineProps<{ loaded: boolean }>()

const { t } = useI18n()
const householdStore = useHouseholdStore()
const accountStore = useAccountStore()
const authStore = useAuthStore()
const { householdId } = useCurrentHousehold()

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
</script>

<template>
  <UCard :ui="CARD_UI">
    <h3 ref="yourAccountsHeading" tabindex="-1" :class="[CARD_TITLE_CLASS, 'outline-none']">
      {{ t('views.householdSettings.accounts.yoursTitle') }}
    </h3>
    <p :class="CARD_DESCRIPTION_CLASS">
      {{ t('views.householdSettings.accounts.yoursDescription') }}
    </p>

    <FormMessage v-if="shareError" tone="error" :message="shareError" class="mt-3" />
    <FormMessage
      v-if="accountStore.error"
      tone="error"
      :message="accountStore.error"
      class="mt-3"
    />
    <div v-else-if="!loaded" class="mt-3 space-y-3" aria-hidden="true">
      <USkeleton v-for="index in 3" :key="index" class="h-10 w-full rounded-lg" />
    </div>
    <p v-else-if="myAccounts.length === 0" :class="LIST_EMPTY_CLASS">
      {{ t('views.householdSettings.accounts.emptyMine') }}
    </p>
    <div v-else :class="ACCOUNTS_SCROLL_CLASS">
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
            <span :class="ACCOUNT_TILE_CLASS" aria-hidden="true">
              <UIcon :name="APP_ICONS.accounts" class="size-4.5" />
            </span>
            <div class="min-w-0 flex-1">
              <p class="flex min-w-0 items-center gap-2">
                <span :class="LIST_ROW_TITLE_CLASS">
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
                      date: formatTimestampDate(sharedAtById.get(account.id)),
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
            <span class="hidden min-w-18 shrink-0 items-center gap-1.5 text-xs text-muted sm:flex">
              <span
                :class="[
                  'size-2 shrink-0 rounded-full',
                  isAccountShared(account.id) ? 'bg-success' : 'bg-(--ui-text-dimmed)',
                ]"
                aria-hidden="true"
              />
              {{
                isAccountShared(account.id)
                  ? t('common.table.shared')
                  : t('views.householdSettings.accounts.privateBadge')
              }}
            </span>
            <UButton
              :id="shareButtonId(account.id)"
              size="xl"
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
              class="shrink-0"
              @click="toggleShareAccount(account)"
            />
          </li>
        </ul>
      </section>
    </div>
  </UCard>
</template>
