import type { TableColumn, TableData } from '@nuxt/ui'

export type AppTableAlign = 'start' | 'end' | 'center'

type ColumnMetaOf<T extends TableData> = NonNullable<TableColumn<T>['meta']>

/** A `UTable` column whose `meta.align` aligns its header and cells together. */
export type AppTableColumn<T extends TableData> = TableColumn<T> & {
  meta?: ColumnMetaOf<T> & { align?: AppTableAlign }
}

/** Server-side page state; `page` is zero-based, as the ledger API returns it. */
export interface AppTablePagination {
  page: number
  pageSize: number
  totalPages: number
  totalElements: number
}

/** A titled run of rows in the phone list; `label` is omitted for an ungrouped list. */
export interface AppListGroup<T> {
  id: string
  label?: string
  items: T[]
}
