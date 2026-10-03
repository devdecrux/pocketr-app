/**
 * Slot classes shared by the form controls so inputs, selects and date triggers line up (40px, rounded,
 * ring). Below `lg` they are 44px tall, a full touch target, and their text is 16px: iOS would
 * otherwise zoom into a focused field, and one drawer should show every value at the same size.
 */
export const FIELD_BASE_CLASS =
  'h-10 rounded-lg bg-(--pocketr-field-bg) text-sm ring-default max-lg:h-11 max-lg:text-base'

/**
 * Width of an open select list: at least the trigger, as wide as its longest option, and no wider
 * than 24rem or the viewport; only options beyond that truncate. Replaces Nuxt UI's default of
 * exactly the trigger width, which cut long options short under a short selected value.
 */
export const SELECT_CONTENT_CLASS =
  'w-max min-w-(--reka-select-trigger-width) max-w-[min(24rem,calc(100vw-2rem))]'

export const FIELD_SELECT_UI = {
  base: FIELD_BASE_CLASS,
  leadingIcon: 'size-5 text-default',
  trailingIcon: 'size-4 text-default',
  content: SELECT_CONTENT_CLASS,
}

export const FIELD_MENU_UI = {
  base: FIELD_BASE_CLASS,
  leadingIcon: 'size-5 text-default',
  trailingIcon: 'size-4 text-default',
  content: 'w-max min-w-(--reka-combobox-trigger-width) max-w-[min(24rem,calc(100vw-2rem))]',
}

/** `UButton` classes for a popover trigger that lines up with the inputs and selects. */
export const FIELD_TRIGGER_CLASS = `${FIELD_BASE_CLASS} w-full justify-start gap-0 p-0 font-normal text-highlighted focus-visible:ring-primary`
