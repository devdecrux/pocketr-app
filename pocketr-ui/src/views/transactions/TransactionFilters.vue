<script setup lang="ts">
/** Contents of the transactions filter panel: description search, date range, account, category. */
import { useI18n } from 'vue-i18n'
import AppDateRangePicker from '@/components/fields/AppDateRangePicker.vue'
import AppSelect from '@/components/fields/AppSelect.vue'
import AppFilterPanel from '@/components/page/AppFilterPanel.vue'
import { useAccountOptions } from '@/components/fields/useAccountOptions'
import { useCategoryOptions } from '@/components/fields/useCategoryOptions'
import { APP_ICONS } from '@/utils/appearance/appIcons'

defineProps<{ id: string; count: number }>()

defineEmits<{ clear: [] }>()

const search = defineModel<string>('search', { required: true })
const dateFrom = defineModel<string | undefined>('dateFrom', { required: true })
const dateTo = defineModel<string | undefined>('dateTo', { required: true })
const accountId = defineModel<string>('accountId', { required: true })
const categoryId = defineModel<string | null>('categoryId', { required: true })

const { t } = useI18n()
const { accountSelect } = useAccountOptions()
const { categorySelect } = useCategoryOptions()
</script>

<template>
  <AppFilterPanel :id="id" :count="count" @clear="$emit('clear')">
    <UInput
      v-model="search"
      :icon="APP_ICONS.search"
      type="search"
      autocomplete="off"
      size="lg"
      :aria-label="t('common.fields.search')"
      :placeholder="t('views.transactions.formHints.searchDescriptions')"
      class="w-full lg:w-48 lg:shrink-0"
    />
    <div class="min-w-0 lg:w-fit lg:min-w-[232px] lg:shrink-0">
      <AppDateRangePicker v-model:from="dateFrom" v-model:to="dateTo" />
    </div>
    <AppSelect
      v-model="accountId"
      v-bind="
        accountSelect({
          includeArchived: true,
          allLabel: t('views.transactions.formHints.allAccounts'),
        })
      "
      auto-width
      :aria-label="t('common.fields.account')"
    />
    <AppSelect
      v-model="categoryId"
      v-bind="categorySelect(t('views.transactions.formHints.allCategories'))"
      auto-width
      :aria-label="t('common.fields.category')"
    />
  </AppFilterPanel>
</template>
