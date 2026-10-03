<script setup lang="ts">
/**
 * Shared overlay shell for create/edit forms and confirmations: a modal on desktop, a bottom drawer
 * below `lg`. Closes through X, Cancel or Escape (and swipe on mobile), never through a backdrop tap.
 *
 * With `formId`, the footer submit button submits that form from outside it, so the form component
 * in the default slot stays the single owner of validation for both breakpoints. Without `formId`
 * the submit button emits `submit` (confirmations).
 */
import type { ButtonProps } from '@nuxt/ui'
import { createReusableTemplate, useMediaQuery } from '@vueuse/core'
import { computed, useSlots } from 'vue'
import { useI18n } from 'vue-i18n'

const open = defineModel<boolean>('open', { required: true })

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    submitLabel: string
    cancelLabel?: string
    formId?: string
    submitColor?: 'primary' | 'error'
    loading?: boolean
    submitDisabled?: boolean
    /**
     * Desktop modal width: `lg` suits forms with a two-column field grid, `wide` single-column forms
     * with roomier mobile margins (create account).
     */
    size?: 'md' | 'wide' | 'lg'
    /** Focus target on close when the opener is gone, for example a menu item. */
    returnFocusTo?: HTMLElement | null
  }>(),
  { submitColor: 'primary', size: 'md' },
)

const emit = defineEmits<{ submit: [] }>()

defineSlots<{
  default?: () => unknown
  footer?: (props: { close: () => void }) => unknown
}>()

const slots = useSlots()
const { t } = useI18n()
const [DefineBody, ReuseBody] = createReusableTemplate()
const [DefineFooter, ReuseFooter] = createReusableTemplate()
const isDesktop = useMediaQuery('(min-width: 1024px)')

function close(): void {
  open.value = false
}

function onSubmitClick(): void {
  if (!props.formId) emit('submit')
}

// Nuxt UI's own close label is not localised; the X is labelled in the app language instead.
const closeButton = computed(
  () =>
    ({
      color: 'neutral',
      variant: 'ghost',
      'aria-label': t('common.actions.close'),
    }) as ButtonProps,
)

const FIELD_SELECTOR =
  'input:not([type="hidden"]):not([type="radio"]):not([disabled]), textarea:not([disabled]), select:not([disabled])'

function focusFirstField(container: HTMLElement | null): boolean {
  const field = container?.querySelector<HTMLElement>(FIELD_SELECTOR)
  field?.focus()
  return Boolean(field)
}

// vaul (UDrawer) closes on an outside pointerdown before the content's own handler runs, so a
// drawer close request raised by that same event is ignored once the handler has flagged it.
let outsidePointerDown = false

function onDrawerOpenChange(value: boolean): void {
  if (value) {
    open.value = true
    return
  }
  queueMicrotask(() => {
    if (outsidePointerDown) outsidePointerDown = false
    else open.value = false
  })
}

const contentProps = {
  onPointerDownOutside: (event: Event) => {
    event.preventDefault()
    outsidePointerDown = true
    queueMicrotask(() => {
      outsidePointerDown = false
    })
  },
  onInteractOutside: (event: Event) => event.preventDefault(),
  onCloseAutoFocus: (event: Event) => {
    if (props.returnFocusTo?.isConnected) {
      event.preventDefault()
      props.returnFocusTo.focus()
    }
  },
  // The desktop modal starts at the first field; overlays without one keep the default (the X). The
  // mobile drawer keeps focus on the dialog itself: focusing a field would raise the on-screen
  // keyboard (and the viewport resize it causes) while the drawer is still sliding up.
  onOpenAutoFocus: (event: Event) => {
    const content = event.target as HTMLElement | null
    if (!isDesktop.value) {
      event.preventDefault()
      content?.focus({ preventScroll: true })
      return
    }
    if (focusFirstField(content)) event.preventDefault()
  },
}

// Nuxt UI centres the modal with `top-1/2 -translate-y-1/2`; a modal of odd height then sits on a
// half pixel and the lower ring of every field in it renders faint. Centring with auto margins keeps
// the content on the layout grid, which the browser snaps to whole pixels.
const MODAL_CENTERED = 'inset-0 m-auto h-fit translate-x-0 translate-y-0'

const MODAL_WIDTH = { md: 'max-w-[440px]', wide: 'max-w-[485px]', lg: 'max-w-[536px]' }

// Forms and confirmations share one look: same header, padding, typography and footer, with no
// divider between the parts. A confirmation has no body, so its footer carries the top spacing.
const hasBody = computed(() => Boolean(slots.default))

const modalUi = computed(() => ({
  overlay: 'bg-(--pocketr-scrim)',
  content: `${MODAL_WIDTH[props.size]} ${MODAL_CENTERED} divide-y-0 rounded-xl`,
  header: 'min-h-0 items-start ps-5 pe-14 pt-5 pb-0 sm:ps-6 sm:pt-6',
  title: 'text-lg font-bold text-highlighted',
  description: 'mt-0.5 text-sm text-muted',
  close: 'top-4 end-4 sm:top-5 sm:end-5',
  body: `px-5 py-4 sm:px-6 ${props.size === 'lg' ? 'sm:pt-6 sm:pb-10' : 'sm:py-5'}`,
  footer: `justify-end gap-2 px-5 pb-5 sm:px-6 sm:pb-6 ${hasBody.value ? 'pt-0' : 'pt-5'}`,
}))

const DRAWER_PADDING = {
  md: { container: 'px-4', footer: '-mx-4 px-4' },
  wide: { container: 'px-6', footer: '-mx-6 px-6' },
  lg: { container: 'px-[22px]', footer: '-mx-[22px] px-[22px]' },
}

const drawerUi = computed(() => ({
  overlay: 'bg-(--pocketr-scrim)',
  container: `gap-3 ${DRAWER_PADDING[props.size].container} pt-3 pb-0`,
  header: 'items-start',
  body: props.size === 'lg' ? 'pb-9' : '',
  title: 'text-lg font-bold text-highlighted',
  description:
    props.size === 'lg'
      ? 'mt-0.5 text-[11.5px] tracking-tight text-muted'
      : 'mt-0.5 text-sm text-muted',
  footer: `${DRAWER_PADDING[props.size].footer} flex-row gap-2.5 border-t border-default pt-3 pb-[max(1rem,env(safe-area-inset-bottom))]`,
}))

// Mobile footers give the submit action the remaining width.
const actionClass = {
  cancel: 'h-11 justify-center rounded-lg px-5 max-lg:basis-2/5',
  submit: 'h-11 justify-center rounded-lg px-5 max-lg:flex-1',
}
</script>

<template>
  <DefineBody>
    <slot />
  </DefineBody>

  <DefineFooter>
    <slot name="footer" :close="close">
      <UButton
        color="neutral"
        variant="ghost"
        size="md"
        :label="cancelLabel ?? t('common.actions.cancel')"
        :disabled="loading"
        :class="actionClass.cancel"
        @click="close"
      />
      <UButton
        :type="formId ? 'submit' : 'button'"
        :form="formId"
        :color="submitColor"
        size="md"
        :label="submitLabel"
        :loading="loading"
        :disabled="submitDisabled"
        :class="actionClass.submit"
        @click="onSubmitClick"
      />
    </slot>
  </DefineFooter>

  <UModal
    v-if="isDesktop"
    v-model:open="open"
    :title="title"
    :description="description"
    :close="closeButton"
    :content="contentProps"
    :ui="modalUi"
  >
    <template v-if="slots.default" #body>
      <ReuseBody />
    </template>
    <template #footer>
      <ReuseFooter />
    </template>
  </UModal>

  <UDrawer
    v-else
    :open="open"
    @update:open="onDrawerOpenChange"
    :title="title"
    :description="description"
    :close="closeButton"
    :content="contentProps"
    :ui="drawerUi"
  >
    <template v-if="slots.default" #body>
      <ReuseBody />
    </template>
    <template #footer>
      <ReuseFooter />
    </template>
  </UDrawer>
</template>
