/** Title of a card on a page (Settings, Household). */
export const CARD_TITLE_CLASS = 'text-base font-semibold text-highlighted'

/** Line of text under a card title. */
export const CARD_DESCRIPTION_CLASS = 'mt-0.5 text-sm text-muted'

/** Page card that stretches its content to the card height (Household). Radius comes from the app theme. */
export const CARD_UI = {
  root: 'flex min-w-0 flex-col',
  body: 'flex flex-1 flex-col p-4 sm:p-4',
}

/**
 * Page card for the narrow desktop columns of Settings (five to a row): the cards fill the row
 * height and keep tighter padding from `lg`, so it stays a variant of `CARD_UI` on purpose.
 */
export const NARROW_CARD_UI = { root: 'min-w-0 lg:h-full', body: 'p-4 sm:p-4 lg:p-3' }

/** Action button of a page card: full width on phones (one per row), content width from `lg`. */
export const CARD_BUTTON_CLASS = 'max-lg:w-full'

/** Holds the action buttons of a card: one per row on phones, side by side from `lg`. */
export const CARD_BUTTON_GROUP_CLASS =
  'grid max-w-full gap-2 max-lg:w-full lg:flex lg:flex-wrap lg:justify-center'

/** Name of a card's subject (household, signed-in user) in a narrow, centred card. */
export const CARD_SUBJECT_CLASS =
  'max-w-full truncate text-base font-semibold text-highlighted lg:text-sm'
