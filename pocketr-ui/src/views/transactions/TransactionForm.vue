<script setup lang="ts">
/**
 * Create-transaction form, shared by the desktop modal and the mobile drawer. A segmented control
 * switches between the four transaction types; every type keeps its own fields while the user
 * switches. Validation and request building go through the `txnStrategies`. The submit button lives
 * in the overlay footer and targets this form through `id`; it is enabled through `valid`.
 *
 * The draft starts empty each time the form mounts, so the page remounts it for every new
 * transaction.
 */
import { watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import AppSelect from '@/components/fields/AppSelect.vue'
import AppTypeTabs from '@/components/fields/AppTypeTabs.vue'
import FormMessage from '@/components/fields/FormMessage.vue'
import { useAccountOptions } from '@/components/fields/useAccountOptions'
import { useCategoryOptions } from '@/components/fields/useCategoryOptions'
import TransactionDateAmountFields from '@/views/transactions/TransactionDateAmountFields.vue'
import TransactionDescriptionField from '@/views/transactions/TransactionDescriptionField.vue'
import {
  TXN_TABS,
  type TxnTab,
  useTransactionDraft,
} from '@/views/transactions/useTransactionDraft'
import { useModeStore } from '@/stores/mode'
import type { CreateTxnRequest } from '@/types/ledger'

const props = defineProps<{ id: string; serverError?: string | null }>()

const valid = defineModel<boolean>('valid', { default: false })

const emit = defineEmits<{ submit: [request: CreateTxnRequest] }>()

const { t } = useI18n()
const modeStore = useModeStore()
const { accountSelect } = useAccountOptions()
const { categorySelect } = useCategoryOptions()
const draft = useTransactionDraft()
const { activeTab, expense, income, transfer, debtPayment } = draft

watchEffect(() => {
  valid.value = draft.isValid.value
})

/** The tabs are named like the transaction kinds they create. */
const TAB_KINDS: Record<TxnTab, string> = {
  expense: 'EXPENSE',
  income: 'INCOME',
  transfer: 'TRANSFER',
  'debt-payment': 'DEBT_PAYMENT',
}

const tabItems = TXN_TABS.map((tab) => ({
  value: tab,
  label: t(`display.transactionKinds.${TAB_KINDS[tab]}`),
}))

function onSubmit(): void {
  const { error, request } = draft.strategyResult.value
  if (error || !request) return
  emit('submit', request)
}

const fieldId = (name: string) => `${props.id}-${name}`

const wideFieldClass = 'lg:col-span-2'
</script>

<template>
  <form :id="id" novalidate class="flex flex-col gap-4" @submit.prevent="onSubmit">
    <FormMessage v-if="serverError" tone="error" :message="serverError" />

    <AppTypeTabs
      v-model="activeTab"
      :items="tabItems"
      :group-label="t('views.transactions.create.typeLabel')"
    >
      <template #content="{ item }">
        <div class="grid grid-cols-1 gap-x-5 gap-y-3 lg:grid-cols-2 lg:gap-y-5">
          <FormMessage
            v-if="item.value === 'transfer' && modeStore.isHousehold"
            tone="info"
            :message="t('views.transactions.notices.householdTransfers')"
            :class="wideFieldClass"
          />

          <template v-if="item.value === 'expense'">
            <TransactionDateAmountFields
              v-model:date="expense.date"
              v-model:amount="expense.amount"
              :id-prefix="fieldId('expense')"
              :minor-unit="draft.minorUnit.value"
              :currency-code="draft.currency.value"
            />
            <UFormField
              :label="t('views.transactions.fields.payFrom')"
              :for="fieldId('expense-pay-from')"
            >
              <AppSelect
                :id="fieldId('expense-pay-from')"
                v-model="expense.payFrom"
                v-bind="accountSelect({ allowedTypes: ['ASSET', 'LIABILITY'] })"
                :placeholder="t('views.transactions.formHints.selectPayFromAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.expenseAccount')"
              :for="fieldId('expense-account')"
            >
              <AppSelect
                :id="fieldId('expense-account')"
                v-model="expense.account"
                v-bind="accountSelect({ allowedTypes: ['EXPENSE'] })"
                :placeholder="t('views.transactions.formHints.selectExpenseAccount')"
              />
            </UFormField>
            <UFormField :label="t('common.fields.category')" :for="fieldId('expense-category')">
              <AppSelect
                :id="fieldId('expense-category')"
                v-model="expense.category"
                v-bind="categorySelect(t('views.transactions.formHints.noCategory'))"
              />
            </UFormField>
            <TransactionDescriptionField
              v-model="expense.description"
              :id="fieldId('expense-description')"
              :placeholder="t('views.transactions.formHints.whatWasThisFor')"
            />
          </template>

          <template v-else-if="item.value === 'income'">
            <TransactionDateAmountFields
              v-model:date="income.date"
              v-model:amount="income.amount"
              :id-prefix="fieldId('income')"
              :minor-unit="draft.minorUnit.value"
              :currency-code="draft.currency.value"
            />
            <UFormField
              :label="t('views.transactions.fields.depositTo')"
              :for="fieldId('income-deposit')"
            >
              <AppSelect
                :id="fieldId('income-deposit')"
                v-model="income.deposit"
                v-bind="accountSelect({ allowedTypes: ['ASSET'] })"
                :placeholder="t('views.transactions.formHints.selectDepositAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.incomeAccount')"
              :for="fieldId('income-account')"
            >
              <AppSelect
                :id="fieldId('income-account')"
                v-model="income.account"
                v-bind="accountSelect({ allowedTypes: ['INCOME'] })"
                :placeholder="t('views.transactions.formHints.selectIncomeAccount')"
              />
            </UFormField>
            <TransactionDescriptionField
              v-model="income.description"
              :id="fieldId('income-description')"
              :placeholder="t('views.transactions.formHints.incomeSource')"
              :class="wideFieldClass"
            />
          </template>

          <template v-else-if="item.value === 'transfer'">
            <TransactionDateAmountFields
              v-model:date="transfer.date"
              v-model:amount="transfer.amount"
              :id-prefix="fieldId('transfer')"
              :minor-unit="draft.minorUnit.value"
              :currency-code="draft.currency.value"
            />
            <UFormField
              :label="t('views.transactions.fields.fromAccount')"
              :for="fieldId('transfer-from')"
            >
              <AppSelect
                :id="fieldId('transfer-from')"
                v-model="transfer.from"
                v-bind="accountSelect({ allowedTypes: ['ASSET'] })"
                :placeholder="t('views.transactions.formHints.selectSourceAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.toAccount')"
              :for="fieldId('transfer-to')"
            >
              <AppSelect
                :id="fieldId('transfer-to')"
                v-model="transfer.to"
                v-bind="accountSelect({ allowedTypes: ['ASSET'] })"
                :placeholder="t('views.transactions.formHints.selectDestinationAccount')"
              />
            </UFormField>
            <TransactionDescriptionField
              v-model="transfer.description"
              :id="fieldId('transfer-description')"
              :placeholder="t('views.transactions.formHints.transferReason')"
              :class="wideFieldClass"
            />
            <FormMessage
              v-if="draft.isCrossUserTransfer.value"
              tone="warning"
              :message="t('views.transactions.notices.crossUserTransfer')"
              :class="wideFieldClass"
            />
          </template>

          <template v-else>
            <TransactionDateAmountFields
              v-model:date="debtPayment.date"
              v-model:amount="debtPayment.amount"
              :id-prefix="fieldId('debt-payment')"
              :minor-unit="draft.minorUnit.value"
              :currency-code="draft.currency.value"
            />
            <UFormField
              :label="t('views.transactions.fields.payFrom')"
              :for="fieldId('debt-payment-pay-from')"
            >
              <AppSelect
                :id="fieldId('debt-payment-pay-from')"
                v-model="debtPayment.payFrom"
                v-bind="accountSelect({ allowedTypes: ['ASSET'] })"
                :placeholder="t('views.transactions.formHints.selectAssetAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.debtPaymentLiabilityAccount')"
              :for="fieldId('debt-payment-liability')"
            >
              <AppSelect
                :id="fieldId('debt-payment-liability')"
                v-model="debtPayment.liabilityAccount"
                v-bind="accountSelect({ allowedTypes: ['LIABILITY'] })"
                :placeholder="t('views.transactions.formHints.selectLiabilityAccount')"
              />
            </UFormField>
            <TransactionDescriptionField
              v-model="debtPayment.description"
              :id="fieldId('debt-payment-description')"
              :placeholder="t('views.transactions.formHints.debtPaymentNote')"
              :class="wideFieldClass"
            />
          </template>
        </div>
      </template>
    </AppTypeTabs>
  </form>
</template>
