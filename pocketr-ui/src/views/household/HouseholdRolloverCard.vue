<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import RolloverDayField from '@/components/fields/RolloverDayField.vue'
import { CARD_BUTTON_CLASS, CARD_TITLE_CLASS, CARD_UI } from '@/styles/card'
import { translate } from '@/i18n/translate'
import { useHouseholdStore } from '@/stores/household'
import { focusFirstField } from '@/utils/focus'
import { isValidRolloverDay } from '@/utils/rollover'
import { useCurrentHousehold } from './useCurrentHousehold'

const ROLLOVER_FIELD_UI = {
  root: 'flex flex-col lg:self-stretch',
  label: CARD_TITLE_CLASS,
  labelWrapper: 'justify-start',
  hint: 'flex items-center',
  container: 'mt-3 lg:mt-auto lg:pt-3',
}

const { t } = useI18n()
const householdStore = useHouseholdStore()
const { householdId, household } = useCurrentHousehold()

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
  focusFirstField(rolloverForm.value)
}

async function saveRolloverDay(): Promise<void> {
  if (!householdStore.isOwnerOrAdmin || isSavingRollover.value || !hasRolloverChanged.value) return

  rolloverFieldError.value = ''
  rolloverError.value = ''
  rolloverSuccess.value = ''

  const rolloverDay = selectedRolloverDay.value
  if (!isValidRolloverDay(rolloverDay)) {
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
</script>

<template>
  <UCard :ui="CARD_UI">
    <form
      ref="rolloverForm"
      class="flex flex-1 flex-col gap-3"
      novalidate
      @submit.prevent="saveRolloverDay"
    >
      <FormMessage v-if="rolloverError" tone="error" :message="rolloverError" />
      <FormMessage v-else-if="rolloverSuccess" tone="success" :message="rolloverSuccess" />

      <div class="flex flex-1 flex-col gap-3 lg:flex-row lg:items-end lg:gap-4">
        <RolloverDayField
          v-model="selectedRolloverDay"
          :label="t('views.settings.preferences.rolloverDay')"
          :help-text="t('views.householdSettings.rollover.help')"
          :help-label="t('views.householdSettings.rollover.helpLabel')"
          help-id="household-rollover-help"
          :error="rolloverFieldError"
          :disabled="!householdStore.isOwnerOrAdmin"
          :field-ui="ROLLOVER_FIELD_UI"
          input-class="lg:w-28"
        />

        <UButton
          v-if="householdStore.isOwnerOrAdmin"
          type="submit"
          size="xl"
          :label="isSavingRollover ? t('common.feedback.saving') : t('common.actions.save')"
          :loading="isSavingRollover"
          :disabled="!hasRolloverChanged"
          :class="[CARD_BUTTON_CLASS, 'lg:ms-auto']"
        />
      </div>
    </form>
  </UCard>
</template>
