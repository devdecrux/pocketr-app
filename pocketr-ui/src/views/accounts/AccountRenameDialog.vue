<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { updateAccount } from '@/api/accounts'
import AppFormOverlay from '@/components/overlays/AppFormOverlay.vue'
import { useTargetAction } from '@/components/overlays/useTargetAction'
import AccountRenameForm from '@/views/accounts/AccountRenameForm.vue'
import { useAccountStore } from '@/stores/account'
import type { Account } from '@/types/ledger'

const FORM_ID = 'rename-account-form'

const { t } = useI18n()
const accountStore = useAccountStore()

const draft = ref('')

const {
  isOpen,
  label: accountName,
  error,
  busyId,
  open,
  run,
} = useTargetAction<Account, string>({
  labelOf: (account) => account.name,
  perform: (account, name) => updateAccount(account.id, { name }),
  afterClose: () => accountStore.load(),
  errorMessage: () => t('errors.accounts.rename'),
  // The form stays open so the message shows next to the name.
  closeOnError: false,
  onOpen: (account) => {
    draft.value = account.name
  },
})

defineExpose({ open })
</script>

<template>
  <AppFormOverlay
    v-model:open="isOpen"
    :title="t('views.accounts.rename.title')"
    :description="t('views.accounts.rename.description', { name: accountName })"
    :form-id="FORM_ID"
    :submit-label="t('common.actions.save')"
    :loading="busyId !== null"
    :submit-disabled="!draft.trim()"
  >
    <AccountRenameForm :id="FORM_ID" v-model:name="draft" :server-error="error" @submit="run" />
  </AppFormOverlay>
</template>
