import { CARD_BUTTON_CLASS } from '@/styles/card'

// The desktop cards are narrow, so a label that cannot fit wraps instead of being cut off; the button
// then grows from the 40px action height.
export const SETTINGS_BUTTON_CLASS = `${CARD_BUTTON_CLASS} max-w-full lg:h-auto lg:min-h-10`
export const SETTINGS_BUTTON_UI = { label: 'lg:whitespace-normal lg:text-center' }
export const SETTINGS_COMPACT_BUTTON_UI = { label: 'lg:max-2xl:sr-only' }
