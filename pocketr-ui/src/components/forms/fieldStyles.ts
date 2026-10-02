/** Slot classes shared by the form controls so inputs, selects and date triggers line up (40px, rounded, ring). */
export const FIELD_BASE_CLASS = 'h-10 rounded-lg bg-(--pocketr-field-bg) text-sm ring-default'

export const FIELD_SELECT_UI = {
  base: FIELD_BASE_CLASS,
  leadingIcon: 'size-5 text-default',
  trailingIcon: 'size-4 text-default',
  content: 'min-w-(--reka-select-trigger-width)',
}

export const FIELD_MENU_UI = {
  base: FIELD_BASE_CLASS,
  leadingIcon: 'size-5 text-default',
  trailingIcon: 'size-4 text-default',
  content: 'min-w-(--reka-combobox-trigger-width)',
}

/** `UButton` classes for a popover trigger that lines up with the inputs and selects. */
export const FIELD_TRIGGER_CLASS = `${FIELD_BASE_CLASS} w-full justify-start gap-0 p-0 font-normal text-highlighted focus-visible:ring-primary`
