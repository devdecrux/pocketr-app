<script setup lang="ts" generic="T extends TableData">
/**
 * Pocketr table on `UTable`: shared styling, optional sticky header, clickable rows (click, Enter or
 * Space on the focused row), empty and loading states, and optional server-side pagination with a
 * page-size select (`AppPaginationBar`). Every `UTable` slot (`<column>-cell`, `<column>-header`, `expanded`, ...) and
 * other `UTable` prop or listener (for example `get-row-id`, `v-model:expanded`) passes through;
 * `class` and `style` go to the wrapper.
 */
import type { TableData, TableRow } from '@nuxt/ui'
import { computed, useAttrs, useSlots } from 'vue'
import { useI18n } from 'vue-i18n'
import AppPaginationBar from '@/components/shared/AppPaginationBar.vue'
import type { AppTableAlign, AppTableColumn, AppTablePagination } from '@/types/dataTable'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    data: T[]
    columns: AppTableColumn<T>[]
    loading?: boolean
    sticky?: boolean
    clickable?: boolean
    emptyText?: string
    pagination?: AppTablePagination
    pageSizeOptions?: number[]
  }>(),
  { pageSizeOptions: () => [5, 10, 15, 25, 50, 100] },
)

const emit = defineEmits<{
  'row-click': [row: TableRow<T>]
  'update:page': [page: number]
  'update:page-size': [size: number]
}>()

const slots = useSlots()
const attrs = useAttrs()
const { t } = useI18n()

const wrapperAttrs = computed(() => ({ class: attrs.class, style: attrs.style }))
const tableAttrs = computed(() =>
  Object.fromEntries(Object.entries(attrs).filter(([key]) => key !== 'class' && key !== 'style')),
)

const ALIGN_CLASS: Record<AppTableAlign, string> = {
  start: 'text-start',
  end: 'text-end',
  center: 'text-center',
}

type ClassValue<A extends unknown[]> = string | ((...args: A) => string) | undefined

function withAlign<A extends unknown[]>(alignClass: string, value: ClassValue<A>): ClassValue<A> {
  if (typeof value === 'function') return (...args: A) => `${alignClass} ${value(...args)}`
  return value ? `${alignClass} ${value}` : alignClass
}

const tableColumns = computed(() =>
  props.columns.map((column) => {
    const align = column.meta?.align
    if (!align) return column
    const alignClass = ALIGN_CLASS[align]
    return {
      ...column,
      meta: {
        ...column.meta,
        class: {
          th: withAlign(alignClass, column.meta?.class?.th),
          td: withAlign(alignClass, column.meta?.class?.td),
        },
      },
    } as AppTableColumn<T>
  }),
)

const passThroughSlots = computed(() =>
  Object.keys(slots).filter((name) => name !== 'empty' && name !== 'loading'),
)

function onSelect(_event: Event, row: TableRow<T>): void {
  emit('row-click', row)
}
</script>

<template>
  <div v-bind="wrapperAttrs" class="flex min-w-0 flex-col gap-3">
    <!-- The rounded frame clips the table (and holds the pagination footer), so the scrolling table keeps square edges. -->
    <div class="overflow-hidden rounded-xl border border-default bg-default">
      <UTable
        v-bind="tableAttrs"
        :data="data"
        :columns="tableColumns"
        :loading="loading"
        :sticky="sticky ? 'header' : false"
        :on-select="clickable ? onSelect : undefined"
        :ui="{
          root: 'bg-default',
          // A real row border draws the header line; a sticky header needs Nuxt UI's separator instead.
          thead: sticky ? '' : '[&>tr]:border-b [&>tr]:border-default',
          separator: sticky ? '' : 'hidden',
          th: 'px-4 py-3 text-sm font-semibold text-highlighted',
          td: 'px-4 py-3 text-sm text-default',
          tbody:
            '[&>tr]:border-default [&>tr[data-expanded=true]]:border-b-0 [&>tr[data-expanded=true]+tr>td]:pt-2 [&>tr[data-expanded=true]+tr>td]:pb-2 [&>tr[data-expanded=true]+tr>td]:whitespace-normal',
          empty: 'py-10 text-center text-sm text-muted whitespace-normal',
          loading: 'py-10 text-center text-sm text-muted',
        }"
      >
        <template v-for="name in passThroughSlots" #[name]="slotProps" :key="name">
          <slot :name="name" v-bind="slotProps ?? {}" />
        </template>
        <template #empty>
          <slot name="empty">{{ emptyText ?? t('common.states.noData') }}</slot>
        </template>
        <template v-if="slots.loading" #loading>
          <slot name="loading" />
        </template>
      </UTable>
      <AppPaginationBar
        v-if="pagination && pagination.totalPages > 0"
        class="border-t border-default px-4 py-[18px]"
        :pagination="pagination"
        :page-size-options="pageSizeOptions"
        @update:page="(page: number) => emit('update:page', page)"
        @update:page-size="(size: number) => emit('update:page-size', size)"
      />
    </div>
  </div>
</template>
