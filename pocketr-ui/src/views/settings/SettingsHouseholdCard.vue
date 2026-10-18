<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { CARD_FIELD_UI } from '@/styles/field'
import FormMessage from '@/components/fields/FormMessage.vue'
import AppConfirmDialog from '@/components/overlays/AppConfirmDialog.vue'
import { ActionFailed, useTargetAction } from '@/components/overlays/useTargetAction'
import {
  CARD_BUTTON_GROUP_CLASS,
  CARD_SUBJECT_CLASS,
  CARD_TITLE_CLASS,
  NARROW_CARD_UI,
} from '@/styles/card'
import {
  SETTINGS_BUTTON_CLASS,
  SETTINGS_BUTTON_UI,
  SETTINGS_COMPACT_BUTTON_UI,
} from '@/styles/settings'
import { translate } from '@/i18n/translate'
import { resolveErrorMessage } from '@/api/errors'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import type { HouseholdSummary } from '@/types/household'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { type FocusTarget, focusWhenRendered } from '@/utils/focus'

const householdStore = useHouseholdStore()
const modeStore = useModeStore()
const router = useRouter()
const { t } = useI18n()

const householdsLoaded = ref(false)
const householdName = ref('')
const householdNameError = ref('')
const householdError = ref('')
const isCreatingHousehold = ref(false)
const inviteActionError = ref('')
const householdHeading = ref<HTMLElement | null>(null)
const householdMessage = ref<FocusTarget>(null)

const activeHouseholds = computed(() => householdStore.activeHouseholds)
const pendingInvites = computed(() => householdStore.pendingInvites)

const {
  isOpen: isLeaveDialogOpen,
  label: leaveTargetName,
  error: leaveError,
  busyId: leavingHouseholdId,
  open: requestLeave,
  run: confirmLeave,
} = useTargetAction<HouseholdSummary>({
  labelOf: (household) => household.name,
  perform: async (household) => {
    // The store reports a failed leave through its return value and `error`.
    if (!(await householdStore.leaveHousehold(household.id))) {
      throw new ActionFailed(householdStore.error ?? translate('errors.households.leave'))
    }
    modeStore.switchToIndividual()
  },
  errorMessage: (caught) => resolveErrorMessage(caught, translate('errors.households.leave')),
})
const isLeavingHousehold = computed(() => leavingHouseholdId.value !== null)

onMounted(async () => {
  await householdStore.loadHouseholds()
  householdsLoaded.value = true
})

function focusHouseholdMessage(): Promise<void> {
  return focusWhenRendered(() => householdMessage.value)
}

async function createHousehold(): Promise<void> {
  householdNameError.value = ''
  householdError.value = ''

  const trimmed = householdName.value.trim()
  if (trimmed.length < 3) {
    householdNameError.value = translate('validation.household.nameMinLength')
    return
  }

  isCreatingHousehold.value = true

  try {
    const household = await householdStore.createHousehold({ name: trimmed })

    if (household) {
      householdName.value = ''
      modeStore.switchToHousehold(household.id)
      await router.push({ name: 'household-settings', params: { householdId: household.id } })
    } else {
      householdError.value = householdStore.error ?? translate('errors.households.create')
    }
  } catch (error: unknown) {
    householdError.value = resolveErrorMessage(error, translate('errors.households.create'))
  } finally {
    isCreatingHousehold.value = false
  }

  if (householdError.value) await focusHouseholdMessage()
}

async function handleAcceptInvite(householdId: string): Promise<void> {
  inviteActionError.value = ''

  if (activeHouseholds.value.length > 0) {
    inviteActionError.value = translate('views.settings.household.acceptInviteBlocked')
    return
  }

  const success = await householdStore.acceptInvite(householdId)
  if (success) {
    modeStore.switchToHousehold(householdId)
    await router.push({ name: 'household-settings', params: { householdId } })
  } else {
    inviteActionError.value = householdStore.error ?? translate('errors.households.acceptInvite')
    await focusHouseholdMessage()
  }
}

// On failure the dialog returns focus to its Leave trigger and the alert announces the error.
async function leaveHousehold(): Promise<void> {
  await confirmLeave()
  if (!leaveError.value) await focusWhenRendered(() => householdHeading.value)
}
</script>

<template>
  <UCard :ui="NARROW_CARD_UI">
    <h3
      ref="householdHeading"
      tabindex="-1"
      :class="[CARD_TITLE_CLASS, 'text-center outline-none']"
    >
      {{ t('views.settings.household.title') }}
    </h3>

    <div
      v-if="!householdsLoaded"
      class="mt-3 flex flex-col items-center gap-2 lg:mt-2"
      aria-hidden="true"
    >
      <USkeleton class="size-14 rounded-full lg:size-10" />
      <USkeleton class="h-4 w-2/3" />
      <USkeleton class="h-3 w-1/3" />
    </div>

    <template v-else>
      <ul v-if="activeHouseholds.length" class="mt-3 grid gap-5 lg:mt-2 lg:gap-3">
        <li
          v-for="household in activeHouseholds"
          :key="household.id"
          class="flex min-w-0 flex-col items-center gap-2 text-center"
        >
          <span
            class="flex size-14 shrink-0 items-center justify-center rounded-full bg-(--pocketr-icon-bg) lg:size-10 dark:bg-(--pocketr-avatar-bg)"
            aria-hidden="true"
          >
            <UIcon :name="APP_ICONS.household" class="size-6 text-default lg:size-5" />
          </span>
          <p :class="CARD_SUBJECT_CLASS" :title="household.name">
            {{ household.name }}
          </p>
          <UBadge
            color="primary"
            variant="soft"
            :label="t(`display.householdRoles.${household.role}`)"
            class="rounded-full px-2.5"
          />
          <div :class="[CARD_BUTTON_GROUP_CLASS, 'mt-1']">
            <UTooltip :text="t('views.settings.household.manage')">
              <UButton
                size="xl"
                :to="{ name: 'household-settings', params: { householdId: household.id } }"
                :icon="APP_ICONS.settings"
                color="neutral"
                variant="outline"
                :label="t('views.settings.household.manage')"
                :class="SETTINGS_BUTTON_CLASS"
                :ui="SETTINGS_COMPACT_BUTTON_UI"
              />
            </UTooltip>
            <UTooltip :text="t('views.settings.household.leave')">
              <UButton
                size="xl"
                :icon="APP_ICONS.signOut"
                color="error"
                variant="solid"
                :label="t('views.settings.household.leave')"
                :disabled="isLeavingHousehold"
                :class="SETTINGS_BUTTON_CLASS"
                :ui="SETTINGS_COMPACT_BUTTON_UI"
                @click="requestLeave(household)"
              />
            </UTooltip>
          </div>
        </li>
      </ul>

      <div v-if="pendingInvites.length" class="mt-3 grid gap-2 text-center lg:mt-2">
        <h4 class="text-sm font-medium text-highlighted">
          {{ t('views.settings.household.pendingInvitations') }}
        </h4>
        <ul class="grid gap-2">
          <li
            v-for="invite in pendingInvites"
            :key="invite.id"
            class="flex min-w-0 flex-col items-center gap-2 rounded-lg border border-default p-2.5"
          >
            <span
              class="max-w-full truncate text-sm font-medium text-highlighted"
              :title="invite.name"
            >
              {{ invite.name }}
            </span>
            <UBadge
              color="warning"
              variant="soft"
              :label="t('display.memberStatuses.INVITED')"
              class="rounded-full px-2.5 text-(color:--pocketr-warning-fg)"
            />
            <UButton
              size="xl"
              :label="t('common.actions.accept')"
              :disabled="activeHouseholds.length > 0"
              :class="SETTINGS_BUTTON_CLASS"
              :ui="SETTINGS_BUTTON_UI"
              @click="handleAcceptInvite(invite.id)"
            />
          </li>
        </ul>
      </div>

      <form
        v-if="!householdStore.hasHousehold"
        class="mt-3 grid gap-3 text-center lg:mt-2"
        novalidate
        @submit.prevent="createHousehold"
      >
        <p class="text-sm text-muted lg:text-xs">
          {{ t('views.settings.household.createDescription') }}
        </p>
        <UFormField
          name="householdName"
          :label="t('common.fields.householdName')"
          :error="householdNameError || undefined"
          :ui="CARD_FIELD_UI"
          class="text-start"
        >
          <UInput
            id="household-name"
            v-model="householdName"
            :icon="APP_ICONS.name"
            type="text"
            :placeholder="t('common.formHints.householdName')"
            class="w-full"
          />
        </UFormField>
        <UButton
          size="xl"
          type="submit"
          :label="
            isCreatingHousehold
              ? t('common.feedback.creating')
              : t('views.settings.household.createAction')
          "
          :loading="isCreatingHousehold"
          :disabled="householdName.trim().length < 3"
          :class="['justify-self-center', SETTINGS_BUTTON_CLASS]"
          :ui="SETTINGS_BUTTON_UI"
        />
      </form>

      <FormMessage
        v-if="leaveError || inviteActionError || householdError"
        ref="householdMessage"
        tabindex="-1"
        tone="error"
        :message="leaveError || inviteActionError || householdError"
        class="mt-4"
      />
    </template>
  </UCard>

  <AppConfirmDialog
    v-model:open="isLeaveDialogOpen"
    :title="t('views.settings.household.leaveTitle', { name: leaveTargetName })"
    :description="t('views.settings.household.leaveDescription')"
    :confirm-label="
      isLeavingHousehold ? t('common.feedback.leaving') : t('views.settings.household.leave')
    "
    :loading="isLeavingHousehold"
    @confirm="leaveHousehold"
  />
</template>
