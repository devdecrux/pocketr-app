<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import AppPageHeading from '@/components/page/AppPageHeading.vue'
import AppContent from '@/layout/AppContent.vue'
import { translate } from '@/i18n/translate'
import { useAccountStore } from '@/stores/account'
import { useHouseholdStore } from '@/stores/household'
import { formatTimestampDate } from '@/utils/dates'
import { useIsDesktop } from '@/utils/useIsDesktop'
import HouseholdInviteCard from './HouseholdInviteCard.vue'
import HouseholdMembersCard from './HouseholdMembersCard.vue'
import HouseholdMyAccountsCard from './HouseholdMyAccountsCard.vue'
import HouseholdRolloverCard from './HouseholdRolloverCard.vue'
import HouseholdSharedAccountsCard from './HouseholdSharedAccountsCard.vue'
import { ROLE_BADGE } from './householdUi'
import { useCurrentHousehold } from './useCurrentHousehold'

type HouseholdTab = 'members' | 'accounts'

const { t } = useI18n()
const householdStore = useHouseholdStore()
const accountStore = useAccountStore()
const isDesktop = useIsDesktop()
const { householdId, household } = useCurrentHousehold()

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

/* Tabs (below lg) */

const activeTab = ref<HouseholdTab>('members')
const tabItems = computed(() => [
  { label: t('views.householdSettings.tabs.members'), value: 'members' },
  { label: t('views.accounts.title'), value: 'accounts' },
])

function onTabChange(value: string | number): void {
  activeTab.value = value === 'accounts' ? 'accounts' : 'members'
}
</script>

<template>
  <AppContent :title="t('views.householdSettings.title')">
    <div class="flex min-w-0 flex-col gap-3 lg:gap-4">
      <AppPageHeading
        :title="household?.name ?? t('views.householdSettings.title')"
        :subtitle="
          household
            ? t('views.householdSettings.header.created', {
                date: formatTimestampDate(household.createdAt),
              })
            : undefined
        "
        :loading="!isLoaded && !household"
      >
        <template #badge>
          <UBadge
            v-if="myRole"
            :color="ROLE_BADGE[myRole].color"
            :variant="ROLE_BADGE[myRole].variant"
            :label="t(`display.householdRoles.${myRole}`)"
            class="shrink-0 rounded-full px-2.5"
          />
        </template>
      </AppPageHeading>

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
            <HouseholdRolloverCard class="lg:col-start-1 lg:row-start-1" />
            <HouseholdMembersCard :loaded="isLoaded" class="lg:col-span-2 lg:row-start-2" />
            <HouseholdInviteCard
              v-if="householdStore.isOwnerOrAdmin"
              class="lg:col-start-2 lg:row-start-1"
            />
          </div>

          <div
            v-show="isDesktop || activeTab === 'accounts'"
            :role="isDesktop ? undefined : 'tabpanel'"
            :aria-label="isDesktop ? undefined : t('views.accounts.title')"
            class="grid min-w-0 gap-3 lg:contents"
          >
            <HouseholdSharedAccountsCard
              :loaded="isLoaded"
              :load-error="sharesLoadError"
              class="lg:col-start-1 lg:row-start-3"
            />
            <HouseholdMyAccountsCard :loaded="isLoaded" class="lg:col-start-2 lg:row-start-3" />
          </div>
        </div>
      </template>
    </div>
  </AppContent>
</template>
