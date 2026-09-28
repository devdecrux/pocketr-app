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
