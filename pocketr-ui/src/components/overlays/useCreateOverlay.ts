import { ref } from 'vue'

interface CreateOverlayOptions<R> {
  /** Creates the item; throws when it fails. */
  create: (request: R) => Promise<unknown>
  /** Runs once the overlay is closed, still busy (a reload, a `created` event). */
  afterCreate: () => void | Promise<void>
  /** The message shown for a failure. */
  errorMessage: (caught: unknown) => string | Promise<string>
}

/**
 * State of a create overlay around a form: open/close, the key that remounts the form for every new
 * item (so each starts from an empty draft), the form's validity, busy state and failure message.
 * Closing is blocked while busy.
 */
export function useCreateOverlay<R>(options: CreateOverlayOptions<R>) {
  const isOpen = ref(false)
  const formKey = ref(0)
  const isFormValid = ref(false)
  const isBusy = ref(false)
  const error = ref('')

  function open(): void {
    if (isBusy.value) return
    error.value = ''
    isFormValid.value = false
    formKey.value += 1
    isOpen.value = true
  }

  function onOpenChange(next: boolean): void {
    if (!next && isBusy.value) return
    isOpen.value = next
  }

  async function submit(request: R): Promise<void> {
    if (isBusy.value) return
    error.value = ''
    isBusy.value = true
    try {
      await options.create(request)
      isOpen.value = false
      await options.afterCreate()
    } catch (caught: unknown) {
      error.value = await options.errorMessage(caught)
    } finally {
      isBusy.value = false
    }
  }

  return { isOpen, formKey, isFormValid, isBusy, error, open, onOpenChange, submit }
}
