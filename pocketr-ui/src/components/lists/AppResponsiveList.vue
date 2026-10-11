<script setup lang="ts" generic="T extends TableData">
/**
 * One list for every breakpoint. The page hands over rows it has already loaded plus its column
 * definitions and row content; this component decides once (`useIsDesktop`) which variant to render
 * and renders only that one:
 * - desktop: `AppDataTable` (all `UTable` slots such as `<column>-cell` / `<column>-header` /
 *   `expanded` pass through; `loading-text` and `empty-text` fill its loading and empty states);
 * - phone: a list of `AppExpandableRow`s (slot `row` is the always-visible content, slot `details`
 *   the expanded panel), the skeleton loading state, the empty message and `AppPaginationBar`.
 * Phone rows may be grouped under headings via `groups`. Row expansion is shared by both variants
 * through `v-model:expanded` (row id -> open); without it the list keeps the state itself.
 */
import type { TableData, TableRow } from '@nuxt/ui'
import { computed, useSlots } from 'vue'
import AppDataTable from '@/components/lists/AppDataTable.vue'
import AppExpandableRow from '@/components/lists/AppExpandableRow.vue'
import AppPaginationBar from '@/components/lists/AppPaginationBar.vue'
import type { AppListGroup, AppTableColumn, AppTablePagination } from '@/components/lists/dataTable'
import { useIsDesktop } from '@/utils/useIsDesktop'

const props = withDefaults(
  defineProps<{
    /** Rows already loaded by the page; the list never fetches. */
    rows: T[]
    /** Desktop table columns. */
    columns: AppTableColumn<T>[]
    /** Unique, stable id of a row; also the expanded-state key. */
    rowKey: (row: T) => string
    /** Phone panel id is `<panelIdPrefix>-<row id>`. */
    panelIdPrefix: string
    loading?: boolean
    /** Desktop loading text and phone skeleton label. */
    loadingText: string
    emptyText: string
    /** Server-side pagination; omit for an unpaged list. */
    pagination?: AppTablePagination
    /** Phone headings; omit for one list without headings. */
    groups?: AppListGroup<T>[]
    /** Height class of a phone skeleton row; it mimics the row it stands in for. */
    skeletonClass?: string
    /** Desktop: sticky header. */
    sticky?: boolean
    /** Desktop: rows react to click, Enter and Space (see `row-click`). */
    clickable?: boolean
  }>(),
  { skeletonClass: 'h-16' },
)

const emit = defineEmits<{
  'row-click': [row: TableRow<T>]
  'update:page': [page: number]
  'update:page-size': [size: number]
}>()

const expanded = defineModel<Record<string, boolean>>('expanded', { default: () => ({}) })

defineSlots<{
  row(props: { item: T; group: AppListGroup<T> }): unknown
  details(props: { item: T; group: AppListGroup<T> }): unknown
  // The remaining slots are `UTable` slots, typed by `UTable` itself.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [slot: string]: (props: any) => unknown
}>()

const slots = useSlots()
const isDesktop = useIsDesktop()

const tableSlots = computed(() =>
  Object.keys(slots).filter((name) => name !== 'row' && name !== 'details'),
)

const phoneGroups = computed<AppListGroup<T>[]>(
  () => props.groups ?? [{ id: 'all', items: props.rows }],
)

// Only the first load swaps the rows for placeholders; later reloads keep them on screen.
const showSkeleton = computed(() => props.loading && props.rows.length === 0)
const showPagination = computed(() => Boolean(props.pagination) && !showSkeleton.value)

function toggle(row: T): void {
  const id = props.rowKey(row)
  expanded.value = { ...expanded.value, [id]: !expanded.value[id] }
}
</script>

<template>
  <AppDataTable
    v-if="isDesktop"
    v-model:expanded="expanded"
    :data="rows"
    :columns="columns"
    :loading="loading"
    :sticky="sticky"
    :clickable="clickable"
    :empty-text="emptyText"
    :get-row-id="rowKey"
    :pagination="pagination"
    @row-click="(row) => emit('row-click', row)"
    @update:page="(page: number) => emit('update:page', page)"
    @update:page-size="(size: number) => emit('update:page-size', size)"
  >
    <template v-for="name in tableSlots" #[name]="slotProps" :key="name">
      <slot :name="name" v-bind="slotProps ?? {}" />
    </template>
    <template #loading>{{ loadingText }}</template>
  </AppDataTable>

  <template v-else>
    <div v-if="showSkeleton" class="space-y-2" :aria-label="loadingText">
      <USkeleton v-for="index in 4" :key="index" class="w-full rounded-xl" :class="skeletonClass" />
    </div>
    <p
      v-else-if="rows.length === 0"
      class="rounded-xl border border-default bg-default px-4 py-8 text-center text-sm text-muted"
    >
      {{ emptyText }}
    </p>
    <div v-else class="flex flex-col" :aria-busy="loading">
      <section v-for="group in phoneGroups" :key="group.id" class="flex flex-col">
        <h2 v-if="group.label" class="px-1 pt-2 pb-2 text-sm font-semibold text-highlighted">
          {{ group.label }}
        </h2>
        <ul
          class="divide-y divide-default overflow-hidden rounded-xl border border-default bg-default"
        >
          <li v-for="item in group.items" :key="rowKey(item)">
            <AppExpandableRow
              :panel-id="`${panelIdPrefix}-${rowKey(item)}`"
              :expanded="Boolean(expanded[rowKey(item)])"
              @toggle="toggle(item)"
            >
              <slot name="row" :item="item" :group="group" />
              <template #details>
                <slot name="details" :item="item" :group="group" />
              </template>
            </AppExpandableRow>
          </li>
        </ul>
      </section>
    </div>
    <AppPaginationBar
      v-if="pagination && showPagination"
      :pagination="pagination"
      @update:page="(page: number) => emit('update:page', page)"
      @update:page-size="(size: number) => emit('update:page-size', size)"
    />
  </template>
</template>
