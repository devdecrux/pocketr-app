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
import AccountSelect from '@/components/forms/AccountSelect.vue'
import AppDateField from '@/components/forms/AppDateField.vue'
import CategorySelect from '@/components/forms/CategorySelect.vue'
import FormMessage from '@/components/forms/FormMessage.vue'
import MoneyInput from '@/components/forms/MoneyInput.vue'
import { FIELD_BASE_CLASS } from '@/components/forms/fieldStyles'
import { TXN_TABS, type TxnTab, useTransactionDraft } from '@/composables/useTransactionDraft'
import { useModeStore } from '@/stores/mode'
import type { CreateTxnRequest } from '@/types/ledger'

const props = defineProps<{ id: string; serverError?: string | null }>()

const valid = defineModel<boolean>('valid', { default: false })

const emit = defineEmits<{ submit: [request: CreateTxnRequest] }>()

const { t } = useI18n()
const modeStore = useModeStore()
const draft = useTransactionDraft()
const { activeTab, expense, income, transfer, debtPayment } = draft

watchEffect(() => {
  valid.value = draft.isValid.value
})

const TAB_LABEL_KEYS: Record<TxnTab, string> = {
  expense: 'expense',
  income: 'income',
  transfer: 'transfer',
  'debt-payment': 'debtPayment',
}

const tabItems = TXN_TABS.map((tab) => ({
  value: tab,
  label: t(`views.transactions.create.tabs.${TAB_LABEL_KEYS[tab]}`),
}))

function onSubmit(): void {
  const { error, request } = draft.strategyResult.value
  if (error || !request) return
  emit('submit', request)
}

const fieldId = (name: string) => `${props.id}-${name}`

const labelUi = { label: 'text-sm font-medium text-highlighted', container: 'mt-1.5' }
// Fields are 36px tall in the narrow (mobile) drawer and 40px in the modal.
const compactField = '@max-[28rem]:h-9'
const wideFieldClass = '@min-[28rem]:col-span-2'
</script>

<template>
  <form :id="id" novalidate class="@container flex flex-col gap-4" @submit.prevent="onSubmit">
    <FormMessage v-if="serverError" tone="error" :message="serverError" />

    <UTabs
      v-model="activeTab"
      :items="tabItems"
      role="group"
      :aria-label="t('views.transactions.create.typeLabel')"
      color="primary"
      variant="pill"
      size="md"
      :unmount-on-hide="true"
      :ui="{
        root: 'gap-3.5 @min-[28rem]:gap-[23px]',
        list: 'gap-0 rounded-lg border border-default bg-(--pocketr-field-bg) p-0.5',
        indicator: 'inset-y-0.5 rounded-md bg-primary shadow-none',
        trigger:
          'h-8 px-1 text-xs @min-[28rem]:h-9 font-normal text-highlighted not-first:border-s not-first:border-default data-[state=active]:border-transparent data-[state=active]:font-medium data-[state=active]:text-inverted data-[state=inactive]:text-highlighted @min-[28rem]:text-sm',
        content: 'outline-none',
      }"
    >
      <template #content="{ item }">
        <div class="grid grid-cols-1 gap-x-5 gap-y-3 @min-[28rem]:grid-cols-2 @min-[28rem]:gap-y-5">
          <FormMessage
            v-if="item.value === 'transfer' && modeStore.isHousehold"
            tone="info"
            :message="t('views.transactions.notices.householdTransfers')"
            :class="wideFieldClass"
          />

          <template v-if="item.value === 'expense'">
            <UFormField
              :label="t('common.fields.date')"
              :for="fieldId('expense-date')"
              :ui="labelUi"
            >
              <AppDateField
                :id="fieldId('expense-date')"
                :trigger-class="compactField"
                v-model="expense.date"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.amount')"
              :for="fieldId('expense-amount')"
              :ui="labelUi"
            >
              <MoneyInput
                :id="fieldId('expense-amount')"
                :trigger-class="compactField"
                v-model="expense.amount"
                :minor-unit="draft.minorUnit.value"
                :currency-code="draft.currency.value"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.payFromAssetOrLiability')"
              :for="fieldId('expense-pay-from')"
              :ui="labelUi"
            >
              <AccountSelect
                :id="fieldId('expense-pay-from')"
                :trigger-class="compactField"
                v-model="expense.payFrom"
                :allowed-types="['ASSET', 'LIABILITY']"
                :placeholder="t('views.transactions.formHints.selectPayFromAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.expenseAccount')"
              :for="fieldId('expense-account')"
              :ui="labelUi"
            >
              <AccountSelect
                :id="fieldId('expense-account')"
                :trigger-class="compactField"
                v-model="expense.account"
                :allowed-types="['EXPENSE']"
                icon="i-lucide-credit-card"
                :placeholder="t('views.transactions.formHints.selectExpenseAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.category')"
              :for="fieldId('expense-category')"
              :ui="labelUi"
            >
              <CategorySelect
                :id="fieldId('expense-category')"
                :trigger-class="compactField"
                v-model="expense.category"
                :none-label="t('views.transactions.formHints.noCategory')"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.description')"
              :for="fieldId('expense-description')"
              :ui="labelUi"
            >
              <UInput
                :id="fieldId('expense-description')"
                v-model="expense.description"
                icon="i-lucide-file-text"
                autocomplete="off"
                size="lg"
                class="w-full"
                :placeholder="t('views.transactions.formHints.whatWasThisFor')"
                :ui="{ base: [FIELD_BASE_CLASS, compactField], leadingIcon: 'size-5 text-default' }"
              />
            </UFormField>
          </template>

          <template v-else-if="item.value === 'income'">
            <UFormField
              :label="t('common.fields.date')"
              :for="fieldId('income-date')"
              :ui="labelUi"
            >
              <AppDateField
                :id="fieldId('income-date')"
                :trigger-class="compactField"
                v-model="income.date"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.amount')"
              :for="fieldId('income-amount')"
              :ui="labelUi"
            >
              <MoneyInput
                :id="fieldId('income-amount')"
                :trigger-class="compactField"
                v-model="income.amount"
                :minor-unit="draft.minorUnit.value"
                :currency-code="draft.currency.value"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.depositTo')"
              :for="fieldId('income-deposit')"
              :ui="labelUi"
            >
              <AccountSelect
                :id="fieldId('income-deposit')"
                :trigger-class="compactField"
                v-model="income.deposit"
                :allowed-types="['ASSET']"
                :placeholder="t('views.transactions.formHints.selectDepositAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.incomeAccount')"
              :for="fieldId('income-account')"
              :ui="labelUi"
            >
              <AccountSelect
                :id="fieldId('income-account')"
                :trigger-class="compactField"
                v-model="income.account"
                :allowed-types="['INCOME']"
                icon="i-lucide-credit-card"
                :placeholder="t('views.transactions.formHints.selectIncomeAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.description')"
              :for="fieldId('income-description')"
              :ui="labelUi"
              :class="wideFieldClass"
            >
              <UInput
                :id="fieldId('income-description')"
                v-model="income.description"
                icon="i-lucide-file-text"
                autocomplete="off"
                size="lg"
                class="w-full"
                :placeholder="t('views.transactions.formHints.incomeSource')"
                :ui="{ base: [FIELD_BASE_CLASS, compactField], leadingIcon: 'size-5 text-default' }"
              />
            </UFormField>
          </template>

          <template v-else-if="item.value === 'transfer'">
            <UFormField
              :label="t('common.fields.date')"
              :for="fieldId('transfer-date')"
              :ui="labelUi"
            >
              <AppDateField
                :id="fieldId('transfer-date')"
                :trigger-class="compactField"
                v-model="transfer.date"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.amount')"
              :for="fieldId('transfer-amount')"
              :ui="labelUi"
            >
              <MoneyInput
                :id="fieldId('transfer-amount')"
                :trigger-class="compactField"
                v-model="transfer.amount"
                :minor-unit="draft.minorUnit.value"
                :currency-code="draft.currency.value"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.fromAccount')"
              :for="fieldId('transfer-from')"
              :ui="labelUi"
            >
              <AccountSelect
                :id="fieldId('transfer-from')"
                :trigger-class="compactField"
                v-model="transfer.from"
                :allowed-types="['ASSET']"
                :placeholder="t('views.transactions.formHints.selectSourceAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.toAccount')"
              :for="fieldId('transfer-to')"
              :ui="labelUi"
            >
              <AccountSelect
                :id="fieldId('transfer-to')"
                :trigger-class="compactField"
                v-model="transfer.to"
                :allowed-types="['ASSET']"
                :placeholder="t('views.transactions.formHints.selectDestinationAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.description')"
              :for="fieldId('transfer-description')"
              :ui="labelUi"
              :class="wideFieldClass"
            >
              <UInput
                :id="fieldId('transfer-description')"
                v-model="transfer.description"
                icon="i-lucide-file-text"
                autocomplete="off"
                size="lg"
                class="w-full"
                :placeholder="t('views.transactions.formHints.transferReason')"
                :ui="{ base: [FIELD_BASE_CLASS, compactField], leadingIcon: 'size-5 text-default' }"
              />
            </UFormField>
            <FormMessage
              v-if="draft.isCrossUserTransfer.value"
              tone="warning"
              :message="t('views.transactions.notices.crossUserTransfer')"
              :class="wideFieldClass"
            />
          </template>

          <template v-else>
            <UFormField
              :label="t('common.fields.date')"
              :for="fieldId('debt-payment-date')"
              :ui="labelUi"
            >
              <AppDateField
                :id="fieldId('debt-payment-date')"
                :trigger-class="compactField"
                v-model="debtPayment.date"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.amount')"
              :for="fieldId('debt-payment-amount')"
              :ui="labelUi"
            >
              <MoneyInput
                :id="fieldId('debt-payment-amount')"
                :trigger-class="compactField"
                v-model="debtPayment.amount"
                :minor-unit="draft.minorUnit.value"
                :currency-code="draft.currency.value"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.payFromAsset')"
              :for="fieldId('debt-payment-pay-from')"
              :ui="labelUi"
            >
              <AccountSelect
                :id="fieldId('debt-payment-pay-from')"
                :trigger-class="compactField"
                v-model="debtPayment.payFrom"
                :allowed-types="['ASSET']"
                :placeholder="t('views.transactions.formHints.selectAssetAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('views.transactions.fields.debtPaymentLiabilityAccount')"
              :for="fieldId('debt-payment-liability')"
              :ui="labelUi"
            >
              <AccountSelect
                :id="fieldId('debt-payment-liability')"
                :trigger-class="compactField"
                v-model="debtPayment.liabilityAccount"
                :allowed-types="['LIABILITY']"
                icon="i-lucide-credit-card"
                :placeholder="t('views.transactions.formHints.selectLiabilityAccount')"
              />
            </UFormField>
            <UFormField
              :label="t('common.fields.description')"
              :for="fieldId('debt-payment-description')"
              :ui="labelUi"
              :class="wideFieldClass"
            >
              <UInput
                :id="fieldId('debt-payment-description')"
                v-model="debtPayment.description"
                icon="i-lucide-file-text"
                autocomplete="off"
                size="lg"
                class="w-full"
                :placeholder="t('views.transactions.formHints.debtPaymentNote')"
                :ui="{ base: [FIELD_BASE_CLASS, compactField], leadingIcon: 'size-5 text-default' }"
              />
            </UFormField>
          </template>
        </div>
      </template>
    </UTabs>
  </form>
</template>
