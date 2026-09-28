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

type OverlayVariant = 'form' | 'confirm'

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
    variant?: OverlayVariant
    /** Focus target on close when the opener is gone, for example a menu item. */
    returnFocusTo?: HTMLElement | null
  }>(),
  { variant: 'form', submitColor: 'primary' },
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
  // A form starts at its first field; overlays without one keep the default (the X).
  onOpenAutoFocus: (event: Event) => {
    if (focusFirstField(event.target as HTMLElement | null)) event.preventDefault()
  },
}

const modalUi = computed(() =>
  props.variant === 'confirm'
    ? {
        overlay: 'bg-(--pocketr-scrim)',
        content: 'max-w-md',
        title: 'text-base font-semibold text-highlighted',
        description: 'text-sm text-muted',
        footer: 'justify-end gap-2',
      }
    : {
        overlay: 'bg-(--pocketr-scrim)',
        content: 'max-w-[440px] divide-y-0 rounded-xl',
        header: 'min-h-0 items-start ps-5 pe-14 pt-5 pb-0 sm:ps-6 sm:pt-6',
        title: 'text-lg font-bold text-highlighted',
        description: 'mt-0.5 text-sm text-muted',
        close: 'top-4 end-4 sm:top-5 sm:end-5',
        body: 'px-5 py-4 sm:px-6 sm:py-5',
        footer: 'justify-end gap-2 px-5 pt-0 pb-5 sm:px-6 sm:pb-6',
      },
)

const drawerUi = computed(() =>
  props.variant === 'confirm'
    ? {
        overlay: 'bg-(--pocketr-scrim)',
        title: 'text-base font-semibold text-highlighted',
        description: 'text-sm text-muted',
        footer: 'grid grid-cols-2 gap-2 pb-[max(1rem,env(safe-area-inset-bottom))]',
      }
    : {
        overlay: 'bg-(--pocketr-scrim)',
        container: 'gap-3 px-4 pt-3 pb-0',
        header: 'items-start',
        title: 'text-lg font-bold text-highlighted',
        description: 'mt-0.5 text-sm text-muted',
        footer:
          '-mx-4 flex-row gap-2.5 border-t border-default px-4 pt-3 pb-[max(1rem,env(safe-area-inset-bottom))]',
      },
)

// Mobile form footers give the submit action the remaining width; confirmations keep their layout.
const actionClass = computed(() =>
  props.variant === 'form'
    ? {
        cancel: 'justify-center rounded-lg max-lg:basis-2/5',
        submit: 'justify-center rounded-lg max-lg:flex-1',
      }
    : { cancel: 'justify-center rounded-lg', submit: 'justify-center rounded-lg' },
)
</script>

<template>
  <DefineBody>
    <slot />
  </DefineBody>

  <DefineFooter>
    <slot name="footer" :close="close">
      <UButton
        color="neutral"
        variant="outline"
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
