<script setup lang="ts">
/**
 * The categories list on the shared responsive list: the desktop table (name, created, actions) and
 * the phone rows with an expandable Edit/Delete panel. Rows are sorted by name; the page owns what
 * edit and delete do.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AppResponsiveList from '@/components/lists/AppResponsiveList.vue'
import AppRowIconButton from '@/components/lists/AppRowIconButton.vue'
import AppRowPanelButton from '@/components/lists/AppRowPanelButton.vue'
import CategoryColorDot from '@/components/fields/CategoryColorDot.vue'
import type { AppTableColumn } from '@/components/lists/dataTable'
import type { CategoryTag } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { formatTimestampDate } from '@/utils/dates'

const props = defineProps<{
  categories: readonly CategoryTag[]
  loading: boolean
}>()

const emit = defineEmits<{
  edit: [category: CategoryTag]
  delete: [category: CategoryTag]
}>()

const { t } = useI18n()

const sortedCategories = computed(() =>
  [...props.categories].sort((a, b) => a.name.localeCompare(b.name)),
)

const columns = computed<AppTableColumn<CategoryTag>[]>(() => [
  {
    accessorKey: 'name',
    header: t('common.fields.category'),
    meta: { align: 'end', class: { th: 'w-full', td: 'w-full max-w-0' } },
  },
  {
    accessorKey: 'createdAt',
    header: t('common.table.created'),
    meta: { align: 'end', class: { th: 'min-w-40', td: 'min-w-40' } },
  },
  {
    id: 'actions',
    header: t('common.table.actions'),
    meta: { align: 'end', class: { th: 'min-w-28', td: 'min-w-28 py-1.5' } },
  },
])
</script>

<template>
  <AppResponsiveList
    :rows="sortedCategories"
    :columns="columns"
    :row-key="(category: CategoryTag) => category.id"
    panel-id-prefix="category-actions"
    skeleton-class="h-14"
    :loading="loading"
    :loading-text="t('views.categories.loading')"
    :empty-text="t('views.categories.empty')"
  >
    <template #name-cell="{ row }">
      <span class="flex min-w-0 items-center justify-end gap-3">
        <CategoryColorDot :color="row.original.color" />
        <span class="truncate font-medium text-highlighted">{{ row.original.name }}</span>
      </span>
    </template>
    <template #createdAt-cell="{ row }">
      <span class="whitespace-nowrap text-muted tabular-nums">
        {{ formatTimestampDate(row.original.createdAt) }}
      </span>
    </template>
    <template #actions-cell="{ row }">
      <div class="-me-2 inline-flex items-center gap-1">
        <AppRowIconButton
          :icon="APP_ICONS.edit"
          :tooltip="t('common.actions.edit')"
          :label="t('common.table.editRow', { name: row.original.name })"
          @click="emit('edit', row.original)"
        />
        <AppRowIconButton
          destructive
          :icon="APP_ICONS.remove"
          :tooltip="t('common.actions.delete')"
          :label="t('views.categories.rowActions.delete', { name: row.original.name })"
          @click="emit('delete', row.original)"
        />
      </div>
    </template>
    <template #row="{ item: category }">
      <CategoryColorDot :color="category.color" class="size-4" />
      <span class="min-w-0 flex-1">
        <span class="block truncate text-[15px] font-medium text-highlighted">
          {{ category.name }}
        </span>
        <span class="block text-[13px] text-muted tabular-nums">
          {{ formatTimestampDate(category.createdAt) }}
        </span>
      </span>
    </template>
    <template #details="{ item: category }">
      <AppRowPanelButton
        :icon="APP_ICONS.edit"
        :label="t('common.actions.edit')"
        :accessible-label="t('common.table.editRow', { name: category.name })"
        @click="emit('edit', category)"
      />
      <AppRowPanelButton
        destructive
        :icon="APP_ICONS.remove"
        :label="t('common.actions.delete')"
        :accessible-label="t('views.categories.rowActions.delete', { name: category.name })"
        @click="emit('delete', category)"
      />
    </template>
  </AppResponsiveList>
</template>
