<script setup lang="ts">
/**
 * Edit-transaction dialog: the overlay around `TransactionForm` started from the transaction the
 * row asked to edit, plus the save flow. The page opens it through the exposed `open(txn)` and
 * reloads its list on `saved`. The form stays open on a failure so the message shows in it.
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/api/errors'
import { updateTxn } from '@/api/ledger'
import AppFormOverlay from '@/components/overlays/AppFormOverlay.vue'
import { useTargetAction } from '@/components/overlays/useTargetAction'
import TransactionForm from '@/views/transactions/TransactionForm.vue'
import type { CreateTxnRequest, LedgerTxn } from '@/types/ledger'

const EDIT_FORM_ID = 'edit-transaction-form'

const emit = defineEmits<{ saved: [] }>()

const { t } = useI18n()

// The form is remounted for every opened transaction so its draft starts as that transaction.
const editing = ref<LedgerTxn | null>(null)
const formKey = ref(0)
const isFormValid = ref(false)

const { isOpen, error, busyId, open, run } = useTargetAction<LedgerTxn, CreateTxnRequest>({
  labelOf: (txn) => txn.description,
  perform: (txn, request) => updateTxn(txn.id, request),
  afterClose: async () => emit('saved'),
  errorMessage: (caught) => resolveErrorMessage(caught, t('errors.transactions.update')),
  closeOnError: false,
  onOpen: (txn) => {
    editing.value = txn
    isFormValid.value = false
    formKey.value += 1
  },
})

defineExpose({ open })
</script>

<template>
  <AppFormOverlay
    v-model:open="isOpen"
    size="lg"
    :title="t('views.transactions.edit.title')"
    :description="t('views.transactions.edit.description')"
    :form-id="EDIT_FORM_ID"
    :submit-label="t('common.actions.save')"
    :loading="busyId !== null"
    :submit-disabled="!isFormValid"
  >
    <TransactionForm
      v-if="editing"
      :id="EDIT_FORM_ID"
      :key="formKey"
      v-model:valid="isFormValid"
      :initial="editing"
      :server-error="error"
      @submit="run"
    />
  </AppFormOverlay>
</template>
