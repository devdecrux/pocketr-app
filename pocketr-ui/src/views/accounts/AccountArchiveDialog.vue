<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/api/errors'
import AppConfirmDialog from '@/components/overlays/AppConfirmDialog.vue'
import { useTargetAction } from '@/components/overlays/useTargetAction'
import { useAccountStore } from '@/stores/account'
import type { Account } from '@/types/ledger'

const props = defineProps<{
  /** Runs once the account is archived; the dialog stays busy until it settles. */
  reload: () => Promise<void>
  /** An archived account's button is gone, so focus returns to the page's New account button. */
  returnFocusTarget: () => HTMLElement | null
}>()

/** The account being archived, so its row can show a busy button. */
const archivingAccountId = defineModel<string | null>('archivingAccountId', { required: true })
/** The message for an archive that failed, shown by the page. */
const error = defineModel<string>('error', { required: true })

const { t } = useI18n()
const accountStore = useAccountStore()

const { isOpen, label, returnFocus, open, run } = useTargetAction<Account>({
  labelOf: (account) => account.name,
  perform: (account) => accountStore.archiveAccount(account.id),
  afterClose: props.reload,
  errorMessage: (caught) => resolveErrorMessage(caught, t('errors.accounts.archive')),
  returnFocusTarget: () => props.returnFocusTarget(),
  busyId: archivingAccountId,
  error,
})

defineExpose({ open })
</script>

<template>
  <AppConfirmDialog
    v-model:open="isOpen"
    :title="t('views.accounts.archive.title')"
    :description="t('views.accounts.archive.description', { name: label })"
    :confirm-label="t('views.accounts.archive.confirm')"
    :loading="archivingAccountId !== null"
    :return-focus-to="returnFocus"
    @confirm="run()"
  />
</template>
