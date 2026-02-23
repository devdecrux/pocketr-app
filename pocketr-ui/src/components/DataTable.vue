<script lang="ts">
export default { inheritAttrs: false }
</script>

<script setup lang="ts" generic="TData extends RowData">
import { computed, useSlots } from 'vue'
import { FlexRender } from '@tanstack/vue-table'
import type { Row, RowData, Table } from '@tanstack/vue-table'
import type {} from '@/types/tanstack-table'
import type { AcceptableValue } from 'reka-ui'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
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

const props = withDefaults(defineProps<Props>(), {
  stickyHeader: false,
  clickable: false,
  emptyText: 'No data.',
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
    ? 'sticky top-0 z-10 bg-muted border-b'
    : 'bg-muted/50 border-b',
)

function handlePageSizeChange(val: AcceptableValue): void {
  if (typeof val !== 'string') return
  emit('update:page-size', Number(val))
}
</script>

<template>
  <div v-bind="$attrs" class="overflow-auto flex flex-col">
    <div class="rounded-md border overflow-hidden">
      <table class="w-full text-sm">
        <thead :class="theadClass">
          <tr
            v-for="headerGroup in table.getHeaderGroups()"
            :key="headerGroup.id"
          >
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
                :class="cn('px-4 py-3 text-right', cell.column.columnDef.meta?.tdClass)"
              >
                <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
              </td>
            </tr>
            <tr v-if="row.getIsExpanded() && slots.expanded">
              <td
                :colspan="row.getVisibleCells().length"
                class="bg-muted/30 px-4 py-3"
              >
                <slot name="expanded" :row="row" />
              </td>
            </tr>
          </template>
          <tr v-if="table.getRowModel().rows.length === 0">
            <td
              :colspan="table.getAllColumns().length"
              class="px-4 py-8 text-center text-muted-foreground"
            >
              <slot name="empty" :colspan="table.getAllColumns().length">{{ emptyText }}</slot>
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
        <span class="text-xs text-muted-foreground">Rows per page</span>
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
          Page {{ pagination.page + 1 }} of {{ pagination.totalPages }} &middot;
          {{ pagination.totalElements }} total
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
