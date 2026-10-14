<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import AppConfirmDialog from '@/components/overlays/AppConfirmDialog.vue'
import AppCreateButton from '@/components/page/AppCreateButton.vue'
import AppFiltersToggle from '@/components/page/AppFiltersToggle.vue'
import AppPageHeading from '@/components/page/AppPageHeading.vue'
import { PAGE_STACK_CLASS } from '@/styles/page'
import TransactionCreateDialog from '@/views/transactions/TransactionCreateDialog.vue'
import TransactionFilters from '@/views/transactions/TransactionFilters.vue'
import TransactionsList from '@/views/transactions/TransactionsList.vue'
import { useTransactionDelete } from '@/views/transactions/useTransactionDelete'
import { useTransactionsList } from '@/views/transactions/useTransactionsList'
import AppContent from '@/layout/AppContent.vue'
import { useLedgerStore } from '@/stores/ledger'

const FILTERS_PANEL_ID = 'transaction-filters'

const { t } = useI18n()
const ledgerStore = useLedgerStore()

const {
  filterDateFrom,
  filterDateTo,
  filterAccountId,
  filterCategoryId,
  filterSearch,
  activeFilterCount,
  clearFilters,
  expanded,
  loadData,
  goToPage,
  changePageSize,
  filteredTransactions,
  pagination,
} = useTransactionsList()

const {
  deleteTargetDescription,
  deleteError,
  deletingTxnId,
  isDeleteDialogOpen,
  requestDelete,
  confirmDelete,
} = useTransactionDelete(loadData)

// The filters stay out of the way until asked for.
const isFiltersOpen = ref(false)

const createDialog = ref<InstanceType<typeof TransactionCreateDialog> | null>(null)

function openCreate(): void {
  createDialog.value?.open()
}
</script>

<template>
  <AppContent :title="t('views.transactions.title')">
    <div :class="PAGE_STACK_CLASS">
      <AppPageHeading
        :title="t('views.transactions.title')"
        :subtitle="t('views.transactions.subtitle')"
      >
        <template #actions>
          <AppFiltersToggle
            v-model:open="isFiltersOpen"
            :panel-id="FILTERS_PANEL_ID"
            :count="activeFilterCount"
          />
          <AppCreateButton
            :label="t('views.transactions.actions.new')"
            :accessible-label="t('views.transactions.create.title')"
            @click="openCreate"
          />
        </template>
      </AppPageHeading>

      <TransactionFilters
        v-if="isFiltersOpen"
        :id="FILTERS_PANEL_ID"
        v-model:search="filterSearch"
        v-model:date-from="filterDateFrom"
        v-model:date-to="filterDateTo"
        v-model:account-id="filterAccountId"
        v-model:category-id="filterCategoryId"
        :count="activeFilterCount"
        @clear="clearFilters"
      />

      <FormMessage v-if="deleteError" tone="error" :message="deleteError" />
      <FormMessage v-if="ledgerStore.error" tone="error" :message="ledgerStore.error" />

      <TransactionsList
        v-else
        v-model:expanded="expanded"
        :rows="filteredTransactions"
        :pagination="pagination"
        :deleting-id="deletingTxnId"
        @delete="requestDelete"
        @update:page="goToPage"
        @update:page-size="changePageSize"
      />
    </div>

    <TransactionCreateDialog ref="createDialog" @created="loadData()" />

    <AppConfirmDialog
      v-model:open="isDeleteDialogOpen"
      :title="t('views.transactions.confirmDelete', { description: deleteTargetDescription })"
      :description="t('views.transactions.delete.description')"
      :confirm-label="t('views.transactions.delete.confirm')"
      :loading="deletingTxnId !== null"
      @confirm="confirmDelete"
    />

    <template #footer>
      <AppCreateButton
        placement="footer"
        :label="t('views.transactions.actions.new')"
        :accessible-label="t('views.transactions.create.title')"
        @click="openCreate"
      />
    </template>
  </AppContent>
</template>
