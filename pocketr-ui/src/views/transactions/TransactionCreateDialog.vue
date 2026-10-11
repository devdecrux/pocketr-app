<script setup lang="ts">
/**
 * Create-transaction dialog: the overlay around `TransactionForm` plus the submit flow. The page
 * opens it through the exposed `open()` (from the heading button on desktop and the footer button
 * on phones) and reloads its list on `created`.
 */
import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/api/errors'
import { createTxn } from '@/api/ledger'
import AppFormOverlay from '@/components/overlays/AppFormOverlay.vue'
import { useCreateOverlay } from '@/components/overlays/useCreateOverlay'
import TransactionForm from '@/views/transactions/TransactionForm.vue'
import type { CreateTxnRequest } from '@/types/ledger'

const CREATE_FORM_ID = 'create-transaction-form'

const emit = defineEmits<{ created: [] }>()

const { t } = useI18n()

const { isOpen, formKey, isFormValid, isBusy, error, open, onOpenChange, submit } =
  useCreateOverlay<CreateTxnRequest>({
    create: createTxn,
    afterCreate: () => emit('created'),
    errorMessage: (caught) => resolveErrorMessage(caught, t('errors.transactions.create')),
  })

defineExpose({ open })
</script>

<template>
  <AppFormOverlay
    :open="isOpen"
    size="lg"
    :title="t('views.transactions.create.title')"
    :description="t('views.transactions.create.description')"
    :form-id="CREATE_FORM_ID"
    :submit-label="t('common.actions.create')"
    :loading="isBusy"
    :submit-disabled="!isFormValid"
    @update:open="onOpenChange"
  >
    <TransactionForm
      :id="CREATE_FORM_ID"
      :key="formKey"
      v-model:valid="isFormValid"
      :server-error="error"
      @submit="submit"
    />
  </AppFormOverlay>
</template>
