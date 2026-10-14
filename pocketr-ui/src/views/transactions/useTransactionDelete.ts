import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/api/errors'
import { deleteTxn } from '@/api/ledger'
import { useTargetAction } from '@/components/overlays/useTargetAction'
import type { LedgerTxn } from '@/types/ledger'

/**
 * Delete flow of the transactions page: the row buttons request a delete, the confirm dialog
 * confirms it. `onDeleted` reloads the list after a successful delete.
 */
export function useTransactionDelete(onDeleted: () => Promise<void>) {
  const { t } = useI18n()

  const { isOpen, label, error, busyId, open, run } = useTargetAction<LedgerTxn>({
    labelOf: (txn) => txn.description,
    perform: (txn) => deleteTxn(txn.id),
    afterClose: onDeleted,
    errorMessage: (caught) => resolveErrorMessage(caught, t('errors.transactions.delete')),
  })

  return {
    deleteTargetDescription: label,
    deleteError: error,
    deletingTxnId: busyId,
    isDeleteDialogOpen: isOpen,
    requestDelete: open,
    confirmDelete: run,
  }
}
