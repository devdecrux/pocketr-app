<script setup lang="ts">
import { ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import AppCreateButton from '@/components/page/AppCreateButton.vue'
import AppFiltersToggle from '@/components/page/AppFiltersToggle.vue'
import AppPageHeading from '@/components/page/AppPageHeading.vue'
import { PAGE_STACK_CLASS } from '@/styles/page'
import { useCreateButtonFocus } from '@/components/page/useCreateButtonFocus'
import AccountArchiveDialog from '@/views/accounts/AccountArchiveDialog.vue'
import AccountCreateDialog from '@/views/accounts/AccountCreateDialog.vue'
import AccountFilters from '@/views/accounts/AccountFilters.vue'
import AccountRenameDialog from '@/views/accounts/AccountRenameDialog.vue'
import AccountsList from '@/views/accounts/AccountsList.vue'
import AccountSummaryCards from '@/views/accounts/AccountSummaryCards.vue'
import { useAccountFilters } from '@/views/accounts/useAccountFilters'
import { useAccountsData } from '@/views/accounts/useAccountsData'
import AppContent from '@/layout/AppContent.vue'
import { useAccountStore } from '@/stores/account'
import type { Account } from '@/types/ledger'

const FILTERS_PANEL_ID = 'account-filters'

const { t } = useI18n()
const accountStore = useAccountStore()

const {
  typeFilter,
  currencyFilter,
  isFiltersOpen,
  activeFilterCount,
  availableCurrencies,
  filteredAccounts,
  clearFilters,
} = useAccountFilters()
const { balances, expanded, sharedAccountIds, isInitialLoad, loadAll } = useAccountsData()

const createDialog = useTemplateRef<{ open: () => void }>('createDialog')
const renameDialog = useTemplateRef<{ open: (account: Account) => void }>('renameDialog')
const archiveDialog = useTemplateRef<{ open: (account: Account) => void }>('archiveDialog')

const archivingAccountId = ref<string | null>(null)
const archiveError = ref('')
// An archived account's button is gone, so focus goes to the visible New account button.
const { headingButton, footerButton, createButton } = useCreateButtonFocus()
</script>

<template>
  <AppContent :title="t('views.accounts.title')">
    <div :class="PAGE_STACK_CLASS">
      <AppPageHeading :title="t('views.accounts.title')" :subtitle="t('views.accounts.subtitle')">
        <template #actions>
          <AppFiltersToggle
            v-model:open="isFiltersOpen"
            :panel-id="FILTERS_PANEL_ID"
            :count="activeFilterCount"
          />
          <AppCreateButton
            ref="headingButton"
            :label="t('views.accounts.actions.new')"
            :accessible-label="t('views.accounts.create.title')"
            @click="createDialog?.open()"
          />
        </template>
      </AppPageHeading>

      <AccountFilters
        v-if="isFiltersOpen"
        :id="FILTERS_PANEL_ID"
        v-model:type="typeFilter"
        v-model:currency="currencyFilter"
        :count="activeFilterCount"
        :currencies="availableCurrencies"
        @clear="clearFilters"
      />

      <FormMessage v-if="archiveError" tone="error" :message="archiveError" />
      <FormMessage v-if="accountStore.error" tone="error" :message="accountStore.error" />

      <AccountSummaryCards
        v-else
        :balances="balances"
        :shared-account-ids="sharedAccountIds"
        :currency-filter="currencyFilter"
        :available-currencies="availableCurrencies"
        :loading="isInitialLoad"
      />

      <AccountsList
        v-if="!accountStore.error"
        v-model:expanded="expanded"
        :accounts="filteredAccounts"
        :balances="balances"
        :shared-account-ids="sharedAccountIds"
        :loading="accountStore.isLoading"
        :archiving-account-id="archivingAccountId"
        @rename="renameDialog?.open($event)"
        @archive="archiveDialog?.open($event)"
      />
    </div>

    <AccountCreateDialog ref="createDialog" :reload="loadAll" />
    <AccountRenameDialog ref="renameDialog" />
    <AccountArchiveDialog
      ref="archiveDialog"
      v-model:archiving-account-id="archivingAccountId"
      v-model:error="archiveError"
      :reload="loadAll"
      :return-focus-target="createButton"
    />

    <template #footer>
      <AppCreateButton
        ref="footerButton"
        placement="footer"
        :label="t('views.accounts.actions.new')"
        :accessible-label="t('views.accounts.create.title')"
        @click="createDialog?.open()"
      />
    </template>
  </AppContent>
</template>
