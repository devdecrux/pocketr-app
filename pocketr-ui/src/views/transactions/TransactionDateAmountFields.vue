<script setup lang="ts">
/** The date and amount fields every transaction type starts with; two cells of the form grid. */
import { useI18n } from 'vue-i18n'
import AppDateField from '@/components/fields/AppDateField.vue'
import MoneyInput from '@/components/fields/MoneyInput.vue'

defineProps<{
  /** The input ids are `<idPrefix>-date` and `<idPrefix>-amount`. */
  idPrefix: string
  minorUnit: number
  currencyCode: string
}>()

const date = defineModel<string>('date', { required: true })
const amount = defineModel<number>('amount', { required: true })

const { t } = useI18n()
</script>

<template>
  <UFormField :label="t('common.fields.date')" :for="`${idPrefix}-date`">
    <AppDateField :id="`${idPrefix}-date`" v-model="date" />
  </UFormField>
  <UFormField :label="t('common.fields.amount')" :for="`${idPrefix}-amount`">
    <MoneyInput
      :id="`${idPrefix}-amount`"
      v-model="amount"
      :minor-unit="minorUnit"
      :currency-code="currencyCode"
    />
  </UFormField>
</template>
