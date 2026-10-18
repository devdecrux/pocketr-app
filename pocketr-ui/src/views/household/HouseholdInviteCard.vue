<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/api/errors'
import { CARD_FIELD_UI } from '@/styles/field'
import FormMessage from '@/components/fields/FormMessage.vue'
import { CARD_BUTTON_CLASS, CARD_DESCRIPTION_CLASS, CARD_TITLE_CLASS, CARD_UI } from '@/styles/card'
import { translate } from '@/i18n/translate'
import { useHouseholdStore } from '@/stores/household'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { focusFirstField } from '@/utils/focus'
import { useCurrentHousehold } from './useCurrentHousehold'

const { t } = useI18n()
const householdStore = useHouseholdStore()
const { householdId } = useCurrentHousehold()

const inviteForm = ref<HTMLFormElement | null>(null)
const inviteEmail = ref('')
const isInviting = ref(false)
const inviteFieldError = ref('')
const inviteError = ref('')
const inviteSuccess = ref('')

async function focusInviteInput(): Promise<void> {
  await nextTick()
  focusFirstField(inviteForm.value)
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
    inviteError.value = resolveErrorMessage(error, translate('errors.households.sendInvitation'))
  } finally {
    isInviting.value = false
  }
  await focusInviteInput()
}
</script>

<template>
  <UCard :ui="CARD_UI">
    <h3 :class="CARD_TITLE_CLASS">{{ t('views.householdSettings.invite.title') }}</h3>
    <p :class="CARD_DESCRIPTION_CLASS">
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
          :ui="{ ...CARD_FIELD_UI, label: 'sr-only', container: 'mt-0' }"
          class="min-w-0 flex-1"
        >
          <UInput
            v-model="inviteEmail"
            type="email"
            autocomplete="off"
            :icon="APP_ICONS.email"
            :placeholder="t('views.householdSettings.invite.placeholder')"
            class="w-full"
          />
        </UFormField>
        <UButton
          type="submit"
          size="xl"
          :label="
            isInviting
              ? t('common.feedback.sending')
              : t('views.householdSettings.invite.sendAction')
          "
          :loading="isInviting"
          :disabled="!inviteEmail.trim()"
          :class="CARD_BUTTON_CLASS"
        />
      </div>
    </form>
  </UCard>
</template>
