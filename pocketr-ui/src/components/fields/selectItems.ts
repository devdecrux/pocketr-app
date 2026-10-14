/** An entry of an `AppSelect` list. `color` adds a category-style dot, `trailing` a muted note. */
export interface AppSelectOption {
  value: string | number
  label: string
  icon?: string
  color?: string | null
  trailing?: string
  description?: string
  disabled?: boolean
}

/** A group heading inside an `AppSelect` list. */
interface AppSelectLabel {
  type: 'label'
  label: string
}

export type AppSelectEntry = AppSelectOption | AppSelectLabel

/** A flat list, or groups that are separated by a rule. */
export type AppSelectItems = AppSelectEntry[] | AppSelectEntry[][]

export type AppSelectValue = string | number | null | undefined

export function isSelectOption(entry: AppSelectEntry): entry is AppSelectOption {
  return !('type' in entry && entry.type === 'label')
}

export function isGroupedItems(items: AppSelectItems): items is AppSelectEntry[][] {
  return items.length > 0 && Array.isArray(items[0])
}
