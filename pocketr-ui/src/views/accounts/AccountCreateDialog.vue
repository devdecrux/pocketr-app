<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { createAccount } from '@/api/accounts'
import AppFormOverlay from '@/components/overlays/AppFormOverlay.vue'
import { useCreateOverlay } from '@/components/overlays/useCreateOverlay'
import AccountForm from '@/views/accounts/AccountForm.vue'
import type { CreateAccountRequest } from '@/types/ledger'

const props = defineProps<{
  /** Runs once the account exists; the dialog stays busy until it settles. */
  reload: () => Promise<void>
}>()

const FORM_ID = 'create-account-form'

const { t } = useI18n()

const { isOpen, formKey, isFormValid, isBusy, error, open, onOpenChange, submit } =
  useCreateOverlay<CreateAccountRequest>({
    create: createAccount,
    afterCreate: props.reload,
    errorMessage: () => t('errors.accounts.create'),
  })

defineExpose({ open })
</script>

<template>
  <AppFormOverlay
    :open="isOpen"
    size="wide"
    :title="t('views.accounts.create.title')"
    :description="t('views.accounts.create.descriptions.main')"
    :form-id="FORM_ID"
    :submit-label="t('common.actions.create')"
    :loading="isBusy"
    :submit-disabled="!isFormValid"
    @update:open="onOpenChange"
  >
    <AccountForm
      :id="FORM_ID"
      :key="formKey"
      v-model:valid="isFormValid"
      :server-error="error"
      @submit="submit"
    />
  </AppFormOverlay>
</template>
