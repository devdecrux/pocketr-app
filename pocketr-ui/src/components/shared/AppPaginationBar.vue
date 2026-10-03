<script setup lang="ts">
import { SELECT_CONTENT_CLASS } from '@/components/forms/fieldStyles'
/**
 * Server-side pagination footer: the visible range ("1–6 of 6") on the left, a page-size select and
 * previous/next buttons on the right. Pages are zero-based, as the ledger API returns them. Renders
 * nothing while there are no pages.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AppTablePagination } from '@/types/dataTable'

const props = withDefaults(
  defineProps<{ pagination: AppTablePagination; pageSizeOptions?: number[] }>(),
  { pageSizeOptions: () => [5, 10, 15, 25, 50, 100] },
)

const emit = defineEmits<{
  'update:page': [page: number]
  'update:page-size': [size: number]
}>()

const { t } = useI18n()

const pageSizeItems = computed(() =>
  props.pageSizeOptions.map((size) => ({ label: String(size), value: size })),
)

const rangeStart = computed(() => props.pagination.page * props.pagination.pageSize + 1)
const rangeEnd = computed(() =>
  Math.min((props.pagination.page + 1) * props.pagination.pageSize, props.pagination.totalElements),
)
const isFirstPage = computed(() => props.pagination.page <= 0)
const isLastPage = computed(() => props.pagination.page >= props.pagination.totalPages - 1)
</script>

<template>
  <div
    v-if="pagination.totalPages > 0"
    class="flex flex-wrap items-center justify-between gap-x-4 gap-y-3"
  >
    <p class="text-sm text-muted tabular-nums">
      {{
        t('common.table.range', {
          from: rangeStart,
          to: rangeEnd,
          total: pagination.totalElements,
        })
      }}
    </p>
    <div class="flex items-center gap-2">
      <span class="text-sm text-muted">{{ t('common.table.rowsPerPage') }}</span>
      <USelect
        :model-value="pagination.pageSize"
        :items="pageSizeItems"
        :aria-label="t('common.table.rowsPerPage')"
        color="neutral"
        size="md"
        class="w-[68px] lg:w-auto lg:max-w-32 lg:min-w-[68px]"
        :ui="{
          base: 'h-[38px] rounded-lg bg-default text-sm ring-default max-lg:h-11 max-lg:text-base',
          trailingIcon: 'size-4 text-default',
          content: SELECT_CONTENT_CLASS,
        }"
        @update:model-value="(size: number) => emit('update:page-size', size)"
      />
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-chevron-left"
        :aria-label="t('common.table.previousPage')"
        :disabled="isFirstPage"
        class="size-[38px] justify-center rounded-lg max-lg:size-11"
        @click="emit('update:page', pagination.page - 1)"
      />
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-chevron-right"
        :aria-label="t('common.table.nextPage')"
        :disabled="isLastPage"
        class="size-[38px] justify-center rounded-lg max-lg:size-11"
        @click="emit('update:page', pagination.page + 1)"
      />
    </div>
  </div>
</template>
