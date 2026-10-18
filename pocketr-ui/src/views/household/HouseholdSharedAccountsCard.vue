<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import { CARD_DESCRIPTION_CLASS, CARD_TITLE_CLASS, CARD_UI } from '@/styles/card'
import { useHouseholdStore } from '@/stores/household'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { formatTimestampDate } from '@/utils/dates'
import {
  ACCOUNT_TILE_CLASS,
  ACCOUNTS_SCROLL_CLASS,
  LIST_EMPTY_CLASS,
  LIST_ROW_CLASS,
  LIST_ROW_TITLE_CLASS,
} from '@/styles/household'
import { displayPerson } from './householdUi'

defineProps<{ loaded: boolean; loadError: string }>()

const { t } = useI18n()
const householdStore = useHouseholdStore()
</script>

<template>
  <UCard :ui="CARD_UI">
    <h3 :class="CARD_TITLE_CLASS">
      {{ t('views.householdSettings.accounts.sharedTitle') }}
    </h3>
    <p :class="CARD_DESCRIPTION_CLASS">
      {{ t('views.householdSettings.accounts.sharedDescription') }}
    </p>

    <FormMessage v-if="loadError" tone="error" :message="loadError" class="mt-3" />
    <div v-else-if="!loaded" class="mt-3 space-y-3" aria-hidden="true">
      <USkeleton v-for="index in 2" :key="index" class="h-10 w-full rounded-lg" />
    </div>
    <section
      v-else-if="householdStore.sharedAccounts.length"
      :aria-label="t('views.householdSettings.accounts.sharedTitle')"
      :class="ACCOUNTS_SCROLL_CLASS"
    >
      <ul class="divide-y divide-default">
        <li
          v-for="share in householdStore.sharedAccounts"
          :key="share.accountId"
          :class="LIST_ROW_CLASS"
        >
          <span :class="ACCOUNT_TILE_CLASS" aria-hidden="true">
            <UIcon :name="APP_ICONS.accounts" class="size-4.5" />
          </span>
          <div class="min-w-0 flex-1">
            <p :class="LIST_ROW_TITLE_CLASS">
              {{ share.accountName }}
            </p>
            <p class="truncate text-xs text-muted">
              {{
                t('views.householdSettings.accounts.sharedBy', {
                  name: displayPerson(share.ownerFirstName, share.ownerLastName, share.ownerEmail),
                })
              }}
            </p>
          </div>
          <span class="shrink-0 text-xs whitespace-nowrap text-muted tabular-nums">
            {{ formatTimestampDate(share.sharedAt) }}
          </span>
        </li>
      </ul>
    </section>
    <p v-else :class="LIST_EMPTY_CLASS">
      {{ t('views.householdSettings.accounts.sharedEmpty') }}
    </p>
  </UCard>
</template>
