/** Scrollable list area of a household card. */
export const SCROLL_AREA_CLASS =
  'mt-3 overflow-y-auto overscroll-contain border-t border-default outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-inset'

/** A row of the member and account lists. */
export const LIST_ROW_CLASS = 'flex min-w-0 items-center gap-3 py-2.5'

/** Title of a row of the member and account lists. */
export const LIST_ROW_TITLE_CLASS = 'truncate text-sm font-medium text-highlighted'

/** Text shown in place of an empty list. */
export const LIST_EMPTY_CLASS = 'mt-3 py-6 text-center text-sm text-muted'

/** Scroll area of the account lists, capped in height. */
export const ACCOUNTS_SCROLL_CLASS = [SCROLL_AREA_CLASS, 'max-h-72']

/** Round icon tile in front of an account row. */
export const ACCOUNT_TILE_CLASS =
  'flex size-9 shrink-0 items-center justify-center rounded-full bg-(--pocketr-icon-bg) text-default dark:bg-(--pocketr-avatar-bg)'
