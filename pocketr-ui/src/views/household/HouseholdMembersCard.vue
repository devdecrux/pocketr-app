<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { CARD_TITLE_CLASS, CARD_UI } from '@/styles/card'
import type { HouseholdMember } from '@/types/household'
import { formatTimestampDate } from '@/utils/dates'
import { initialsFromName } from '@/utils/initials'
import {
  LIST_EMPTY_CLASS,
  LIST_ROW_CLASS,
  LIST_ROW_TITLE_CLASS,
  SCROLL_AREA_CLASS,
} from '@/styles/household'
import { displayPerson, ROLE_BADGE } from './householdUi'
import { useCurrentHousehold } from './useCurrentHousehold'

defineProps<{ loaded: boolean }>()

const { t } = useI18n()
const { household } = useCurrentHousehold()

const STATUS_DOT: Record<HouseholdMember['status'], string> = {
  ACTIVE: 'bg-success',
  INVITED: 'bg-warning',
}

function memberInitials(member: HouseholdMember): string {
  const initials = initialsFromName(member.firstName, member.lastName)
  return initials === '?' ? member.email.trim().charAt(0).toUpperCase() || '?' : initials
}
</script>

<template>
  <UCard :ui="CARD_UI">
    <h3 :class="CARD_TITLE_CLASS">{{ t('views.householdSettings.tabs.members') }}</h3>

    <div
      v-if="!loaded"
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
      :aria-label="t('views.householdSettings.tabs.members')"
      :class="[SCROLL_AREA_CLASS, 'max-h-96']"
    >
      <ul class="divide-y divide-default">
        <li v-for="member in household.members" :key="member.userId" :class="LIST_ROW_CLASS">
          <UAvatar :text="memberInitials(member)" size="lg" class="shrink-0" aria-hidden="true" />
          <div class="min-w-0 flex-1">
            <p :class="LIST_ROW_TITLE_CLASS">
              {{ displayPerson(member.firstName, member.lastName, member.email) }}
            </p>
            <p
              v-if="displayPerson(member.firstName, member.lastName, member.email) !== member.email"
              class="truncate text-xs text-muted"
            >
              {{ member.email }}
            </p>
            <p v-if="member.joinedAt" class="truncate text-xs text-muted">
              {{
                t('views.householdSettings.members.joined', {
                  date: formatTimestampDate(member.joinedAt),
                })
              }}
            </p>
          </div>
          <div class="flex shrink-0 flex-col items-end gap-1 sm:flex-row sm:items-center sm:gap-3">
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
    <p v-else :class="LIST_EMPTY_CLASS">
      {{ t('views.householdSettings.members.empty') }}
    </p>
  </UCard>
</template>
