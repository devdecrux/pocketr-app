<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppSelect from '@/components/fields/AppSelect.vue'
import { CARD_FIELD_UI } from '@/styles/field'
import FormMessage from '@/components/fields/FormMessage.vue'
import RolloverDayField from '@/components/fields/RolloverDayField.vue'
import { CARD_TITLE_CLASS, NARROW_CARD_UI } from '@/styles/card'
import { SETTINGS_BUTTON_CLASS, SETTINGS_BUTTON_UI } from '@/styles/settings'
import { supportedLocaleLabels, supportedLocales } from '@/i18n'
import { translate } from '@/i18n/translate'
import { resolveErrorMessage } from '@/api/errors'
import { updateUserLanguage, updateUserRolloverDay } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { SupportedUserLanguage } from '@/types/auth'
import { elementOf, focusTarget, type FocusTarget } from '@/utils/focus'
import { isValidRolloverDay } from '@/utils/rollover'

const PREFERENCES_FORM_ID = 'settings-preferences'
const PREFERENCES_TITLE_ID = 'settings-preferences-title'
const ROLLOVER_FIELD_UI = {
  ...CARD_FIELD_UI,
  labelWrapper: 'justify-start',
  hint: 'flex items-center',
}

const authStore = useAuthStore()
const { t } = useI18n()

const selectedLanguage = ref<SupportedUserLanguage>(authStore.user?.language ?? 'en')
const selectedRolloverDay = ref<number | null>(authStore.user?.rolloverDay ?? 1)
const isSavingPreferences = ref(false)
const preferencesError = ref('')
const preferencesSuccess = ref('')
const rolloverFieldError = ref('')
const preferencesMessage = ref<FocusTarget>(null)

const languageItems = supportedLocales.map((locale) => ({
  label: supportedLocaleLabels[locale],
  value: locale,
}))

const hasLanguageChanged = computed(() => selectedLanguage.value !== authStore.user?.language)
const hasRolloverChanged = computed(() => selectedRolloverDay.value !== authStore.user?.rolloverDay)
const hasPreferencesChanged = computed(() => hasLanguageChanged.value || hasRolloverChanged.value)

async function savePreferences(): Promise<void> {
  if (!hasPreferencesChanged.value || isSavingPreferences.value) return

  preferencesError.value = ''
  preferencesSuccess.value = ''
  rolloverFieldError.value = ''

  const rolloverDay = selectedRolloverDay.value
  if (!isValidRolloverDay(rolloverDay)) {
    rolloverFieldError.value = translate('validation.rollover.dayRange')
    return
  }

  isSavingPreferences.value = true
  const errors: string[] = []

  // Each preference keeps its own endpoint; a failure in one does not undo the other.
  if (hasLanguageChanged.value) {
    try {
      const updatedUser = await updateUserLanguage(selectedLanguage.value)
      await authStore.setUser(updatedUser)
      selectedLanguage.value = updatedUser.language
    } catch (error: unknown) {
      errors.push(resolveErrorMessage(error, translate('errors.language.update')))
    }
  }

  if (hasRolloverChanged.value) {
    try {
      const updatedUser = await updateUserRolloverDay(rolloverDay)
      await authStore.setUser(updatedUser)
      selectedRolloverDay.value = updatedUser.rolloverDay
    } catch (error: unknown) {
      errors.push(resolveErrorMessage(error, translate('errors.rollover.update')))
    }
  }

  isSavingPreferences.value = false

  if (errors.length) {
    preferencesError.value = errors.join(' ')
  } else {
    preferencesSuccess.value = translate('views.settings.preferences.saved')
  }

  await nextTick()
  elementOf(preferencesMessage.value)?.scrollIntoView({ block: 'nearest' })
  if (errors.length) focusTarget(preferencesMessage.value)
}
</script>

<template>
  <UCard :ui="NARROW_CARD_UI">
    <h3 :id="PREFERENCES_TITLE_ID" :class="CARD_TITLE_CLASS">
      {{ t('views.settings.preferences.title') }}
    </h3>

    <form
      :id="PREFERENCES_FORM_ID"
      class="mt-3 grid gap-3 lg:mt-2"
      novalidate
      @submit.prevent="savePreferences"
    >
      <div class="grid gap-3 lg:gap-2">
        <RolloverDayField
          v-model="selectedRolloverDay"
          :label="t('views.settings.preferences.rolloverDay')"
          :help-text="t('views.settings.preferences.rolloverHelp')"
          :help-label="t('views.settings.preferences.rolloverHelpLabel')"
          help-id="settings-rollover-help"
          :error="rolloverFieldError"
          :field-ui="ROLLOVER_FIELD_UI"
        />

        <UFormField
          name="language"
          :label="t('views.settings.preferences.language')"
          :ui="CARD_FIELD_UI"
          class="min-w-0"
        >
          <AppSelect v-model="selectedLanguage" :items="languageItems" />
        </UFormField>
      </div>

      <FormMessage
        v-if="preferencesError"
        ref="preferencesMessage"
        tabindex="-1"
        tone="error"
        :message="preferencesError"
      />
      <FormMessage
        v-else-if="preferencesSuccess"
        ref="preferencesMessage"
        tone="success"
        :message="preferencesSuccess"
      />

      <UButton
        size="xl"
        type="submit"
        :label="isSavingPreferences ? t('common.feedback.saving') : t('common.actions.save')"
        :aria-describedby="PREFERENCES_TITLE_ID"
        :loading="isSavingPreferences"
        :disabled="!hasPreferencesChanged"
        :class="['justify-self-center', SETTINGS_BUTTON_CLASS]"
        :ui="SETTINGS_BUTTON_UI"
      />
    </form>
  </UCard>
</template>
