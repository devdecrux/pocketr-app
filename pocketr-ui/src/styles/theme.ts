import { FIELD_BASE_CLASS, MENU_CONTENT_CLASS, SELECT_CONTENT_CLASS } from './field'

/** Every size of a Nuxt UI component, so a compound variant applies whichever `size` is set. */
const ALL_SIZES = ['xs', 'sm', 'md', 'lg', 'xl']

/**
 * Pocketr's defaults for Nuxt UI, passed as the `ui` option of the `@nuxt/ui/vite` plugin. Slot
 * defaults are compound variants because those come after the component's size and variant classes,
 * as a `:ui` binding does; a plain `slots` entry would lose to them. This file runs in the Vite
 * config (Node), so it must not import anything browser-only.
 */
export const uiTheme = {
  colors: {
    primary: 'cyan',
    secondary: 'amber',
    warning: 'amber',
  },
  // Every page card has the same corner radius (Nuxt UI's default is `rounded-lg`).
  card: { slots: { root: 'rounded-xl' } },
  // Text fields: 40px, 44px with 16px text below `lg`. `variant: 'outline'` leaves the borderless
  // search field inside a searchable select list alone.
  input: {
    compoundVariants: [
      {
        variant: 'outline',
        size: ALL_SIZES,
        class: { base: FIELD_BASE_CLASS, leadingIcon: 'size-5 text-default' },
      },
    ],
  },
  inputNumber: {
    compoundVariants: [{ variant: 'outline', size: ALL_SIZES, class: { base: FIELD_BASE_CLASS } }],
  },
  // Every select shares the icon colours and the open list width; `AppSelect` takes the
  // field size (`FIELD_SELECT_UI`) in every variant.
  select: {
    compoundVariants: [
      {
        size: ALL_SIZES,
        class: {
          leadingIcon: 'text-default',
          trailingIcon: 'size-4 text-default',
          content: SELECT_CONTENT_CLASS,
        },
      },
    ],
  },
  selectMenu: {
    compoundVariants: [
      {
        size: ALL_SIZES,
        class: {
          leadingIcon: 'text-default',
          trailingIcon: 'size-4 text-default',
          content: MENU_CONTENT_CLASS,
        },
      },
    ],
  },
  // Labels and spacing of the fields in the create / edit drawers and modals. Fields on a page card
  // and in the auth card override them (`CARD_FIELD_UI`, `authFieldUi`).
  formField: {
    compoundVariants: [
      {
        orientation: 'vertical',
        class: { label: 'text-sm font-medium text-highlighted', container: 'mt-1.5' },
      },
    ],
  },
  // The 36px user avatar (transaction creators, members, the mobile header) is the `lg` size.
  avatar: {
    compoundVariants: [
      {
        color: 'neutral',
        class: { root: 'bg-(--pocketr-avatar-bg)', fallback: 'font-normal text-highlighted' },
      },
      { size: 'lg', class: { fallback: 'text-sm' } },
    ],
  },
  // `size: 'xl'` is the action button size: 44px below `lg`, 40px from `lg`, rounded, label centred
  // (a full-width `block` button too). Icon-only action buttons are squares of that height. The other
  // sizes stay Nuxt UI's, for the menu, trigger and helper buttons.
  button: {
    compoundVariants: [
      {
        size: 'xl',
        class: {
          base: 'h-11 justify-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm lg:h-10',
          leadingIcon: 'size-5',
          trailingIcon: 'size-5',
        },
      },
      { size: 'xl', square: true, class: { base: 'size-11 p-0 lg:size-10' } },
      // Secondary buttons (Filters, Cancel, Change photo, Manage, pagination) share the look of a
      // field: the field background, a default border and regular highlighted text.
      {
        color: 'neutral',
        variant: 'outline',
        class: { base: 'bg-(--pocketr-field-bg) font-normal text-highlighted ring-default' },
      },
    ],
  },
}
