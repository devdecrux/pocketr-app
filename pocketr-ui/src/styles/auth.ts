/**
 * Heading, field label and button spacing classes shared by the sign-in and registration pages. They read the
 * `--pocketr-auth-*` sizes that `AppAuthLayout` declares, so both pages always match.
 */
export const authMarkClass =
  'mx-auto flex size-(--pocketr-auth-mark) items-center justify-center rounded-full bg-(--pocketr-auth-mark-bg)'

export const authMarkIconClass = 'size-(--pocketr-auth-mark-icon) text-primary'

export const authTitleClass =
  'mt-(--pocketr-auth-title-gap) text-center text-(length:--pocketr-auth-title) leading-(--pocketr-auth-title-leading) font-bold text-highlighted'

export const authSubtitleClass =
  'mt-(--pocketr-auth-subtitle-gap) text-center text-(length:--pocketr-auth-subtitle) leading-(--pocketr-auth-subtitle-leading) text-muted'

export const authLinkRowClass =
  'mt-(--pocketr-auth-link-gap) text-center text-(length:--pocketr-auth-link) text-muted'

export const authFieldUi = {
  label:
    'text-(length:--pocketr-auth-label) leading-(--pocketr-auth-label-leading) text-highlighted',
  container: 'mt-(--pocketr-auth-label-gap)',
}

export const authLinkClass =
  'font-medium text-primary hover:text-primary hover:underline underline-offset-2'

export const authFieldsClass = 'flex flex-col gap-(--pocketr-auth-field-gap)'

// Layout only: the submit button is a standard `size="xl"` action button.
export const authSubmitClass = 'mt-0.5'
