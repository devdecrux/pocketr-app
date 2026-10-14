<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AppSelect from '@/components/fields/AppSelect.vue'
import AppFilterPanel from '@/components/page/AppFilterPanel.vue'
import { ALL_FILTER } from '@/views/accounts/useAccountFilters'
import type { AccountType } from '@/types/ledger'

const props = defineProps<{
  id: string
  count: number
  currencies: string[]
}>()

const emit = defineEmits<{ clear: [] }>()

const typeFilter = defineModel<string>('type', { required: true })
const currencyFilter = defineModel<string>('currency', { required: true })

const { t } = useI18n()

const accountTypes: AccountType[] = ['ASSET', 'LIABILITY', 'INCOME', 'EXPENSE']

const typeItems = computed(() =>
  accountTypes.map((type) => ({ value: type, label: t(`display.accountTypes.${type}`) })),
)

const currencyItems = computed(() => props.currencies.map((code) => ({ value: code, label: code })))
</script>

<template>
  <AppFilterPanel :id="id" :count="count" @clear="emit('clear')">
    <AppSelect
      v-model="typeFilter"
      :items="typeItems"
      :all-label="t('views.accounts.filters.allTypes')"
      :all-value="ALL_FILTER"
      :aria-label="t('common.formHints.filterType')"
      auto-width
    />
    <AppSelect
      v-model="currencyFilter"
      :items="currencyItems"
      :all-label="t('views.accounts.filters.allCurrencies')"
      :all-value="ALL_FILTER"
      :aria-label="t('common.formHints.filterCurrency')"
      auto-width
    />
  </AppFilterPanel>
</template>
