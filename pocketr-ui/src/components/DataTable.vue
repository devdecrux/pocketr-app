<script lang="ts">
export default { inheritAttrs: false }
</script>

<script setup lang="ts" generic="TData extends RowData">
import { computed, useSlots } from 'vue'
import { FlexRender } from '@tanstack/vue-table'
import type { Cell, Row, RowData, Table } from '@tanstack/vue-table'
import type { AcceptableValue } from 'reka-ui'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

interface Pagination {
  page: number
  pageSize: number
  totalPages: number
  totalElements: number
}

interface Props {
  table: Table<TData>
  stickyHeader?: boolean
  clickable?: boolean
  emptyText?: string
  pagination?: Pagination
  pageSizeOptions?: number[]
}

interface DataTableColumnMeta {
  tdClass?: string
}

const props = withDefaults(defineProps<Props>(), {
  stickyHeader: false,
  clickable: false,
  pageSizeOptions: () => [5, 10, 15, 25, 50, 100],
})

const emit = defineEmits<{
  'row-click': [row: Row<TData>]
  'update:page': [page: number]
  'update:page-size': [size: number]
}>()
const slots = useSlots()

defineSlots<{
  expanded(props: { row: Row<TData> }): unknown
  empty(props: { colspan: number }): unknown
}>()

const theadClass = computed(() =>
  props.stickyHeader
    ? 'app-table-header sticky top-0 z-10 border-b dark:bg-muted'
    : 'app-table-header border-b dark:bg-muted/50',
)

function handlePageSizeChange(val: AcceptableValue): void {
  if (typeof val !== 'string') return
  emit('update:page-size', Number(val))
}

function tdClassForCell(cell: Cell<TData, unknown>): string | undefined {
  return (cell.column.columnDef.meta as DataTableColumnMeta | undefined)?.tdClass
}
</script>

<template>
  <div v-bind="$attrs" class="app-data-table flex flex-col">
    <div class="min-w-0 shrink-0 overflow-x-auto rounded-xl border">
      <table class="w-full text-sm">
        <thead :class="theadClass">
          <tr v-for="headerGroup in table.getHeaderGroups()" :key="headerGroup.id">
            <th
              v-for="header in headerGroup.headers"
              :key="header.id"
              class="px-4 py-2 text-right font-medium text-muted-foreground"
            >
              <FlexRender
                v-if="!header.isPlaceholder"
                :render="header.column.columnDef.header"
                :props="header.getContext()"
              />
            </th>
          </tr>
        </thead>
        <tbody>
          <template v-for="row in table.getRowModel().rows" :key="row.id">
            <tr
              class="border-b last:border-0"
              :class="clickable && 'cursor-pointer transition-colors hover:bg-muted/50'"
              @click="clickable ? emit('row-click', row) : undefined"
            >
              <td
                v-for="cell in row.getVisibleCells()"
                :key="cell.id"
                :class="
                  cn(
                    'px-4 py-3 text-right',
                    cell.column.id === 'actions' && 'w-1 whitespace-nowrap py-0',
                    tdClassForCell(cell),
                  )
                "
              >
                <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
              </td>
            </tr>
            <tr v-if="row.getIsExpanded() && slots.expanded">
              <td :colspan="row.getVisibleCells().length" class="bg-muted/30 px-4 py-3">
                <slot name="expanded" :row="row" />
              </td>
            </tr>
          </template>
          <tr v-if="table.getRowModel().rows.length === 0">
            <td
              :colspan="table.getAllColumns().length"
              class="px-4 py-8 text-center text-muted-foreground"
            >
              <slot name="empty" :colspan="table.getAllColumns().length">
                {{ emptyText ?? $t('common.states.noData') }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-if="pagination && pagination.totalPages > 0"
      class="mt-4 flex items-center justify-between gap-4"
    >
      <div class="flex items-center gap-2">
        <span class="text-xs text-muted-foreground">{{ $t('common.table.rowsPerPage') }}</span>
        <Select
          :model-value="String(pagination.pageSize)"
          @update:model-value="handlePageSizeChange"
        >
          <SelectTrigger class="h-8 w-20 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem
              v-for="opt in pageSizeOptions"
              :key="opt"
              :value="String(opt)"
              class="text-xs"
            >
              {{ opt }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex items-center gap-3">
        <span class="text-xs text-muted-foreground">
          {{ $t('common.table.pageOf', { page: pagination.page + 1, totalPages: pagination.totalPages }) }}
          &middot;
          {{ $t('common.table.total', { totalElements: pagination.totalElements }) }}
        </span>
        <div class="flex gap-1">
          <Button
            variant="outline"
            size="icon"
            class="h-8 w-8"
            :disabled="pagination.page === 0"
            @click="emit('update:page', pagination.page - 1)"
          >
            <ChevronLeft class="size-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            class="h-8 w-8"
            :disabled="pagination.page >= pagination.totalPages - 1"
            @click="emit('update:page', pagination.page + 1)"
          >
            <ChevronRight class="size-4" />
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.app-data-table [data-table-action] {
  width: 2rem;
  height: 2rem;
}

.app-data-table [data-table-action='delete'] {
  color: var(--app-action-danger-fg);
}

.app-data-table [data-table-action='delete']:hover {
  background-color: var(--app-action-danger-hover-bg);
  color: var(--app-action-danger-hover-fg);
}
</style>
