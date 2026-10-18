import { computed, ref, type Ref } from 'vue'

/** Thrown by an action whose own message is ready to show (a store that reports its failure). */
export class ActionFailed extends Error {}

interface TargetActionOptions<T extends { id: string }, I> {
  /** Text the overlay keeps showing while it animates closed, once the target is cleared. */
  labelOf: (item: T) => string
  /** The action on the target; throws when it fails. */
  perform: (item: T, input: I) => Promise<unknown>
  /** Runs once the overlay is closed, still busy (a reload that follows the action). */
  afterClose?: () => Promise<void>
  /** The message shown for a failure that is not an `ActionFailed`. */
  errorMessage: (caught: unknown) => string | Promise<string>
  /** A confirmation closes on failure and the page shows the error; a form stays open to show it. */
  closeOnError?: boolean
  /** The opener is gone after a success (its row was removed): focus returns to this element. */
  returnFocusTarget?: () => HTMLElement | null
  /** Called when the overlay opens for an item, before it shows (resets a draft). */
  onOpen?: (item: T) => void
  /** Busy state and error owned by the page, when other parts of the page show them. */
  busyId?: Ref<string | null>
  error?: Ref<string>
}

/**
 * State of an overlay that acts on one item (a confirmation or a small form): the target, the busy
 * item, the failure message and the focus to return to. Closing is blocked while busy, and `run`
 * ignores a second call, so a double click cannot repeat the action.
 */
export function useTargetAction<T extends { id: string }, I = void>(
  options: TargetActionOptions<T, I>,
) {
  const target = ref<T | null>(null) as Ref<T | null>
  const label = ref('')
  const busyId = options.busyId ?? ref<string | null>(null)
  const error = options.error ?? ref('')
  const returnFocus = ref<HTMLElement | null>(null)

  const isOpen = computed({
    get: () => target.value !== null,
    set: (open: boolean) => {
      if (!open && busyId.value === null) target.value = null
    },
  })

  function open(item: T): void {
    if (busyId.value !== null) return
    error.value = ''
    returnFocus.value = null
    label.value = options.labelOf(item)
    options.onOpen?.(item)
    target.value = item
  }

  async function run(input: I): Promise<void> {
    const item = target.value
    if (!item || busyId.value !== null) return

    error.value = ''
    busyId.value = item.id
    try {
      await options.perform(item, input)
      // Set before the overlay closes: the focus target is read as it finishes closing.
      returnFocus.value = options.returnFocusTarget?.() ?? null
      target.value = null
      await options.afterClose?.()
    } catch (error_: unknown) {
      if (options.closeOnError ?? true) target.value = null
      error.value =
        error_ instanceof ActionFailed ? error_.message : await options.errorMessage(error_)
    } finally {
      busyId.value = null
    }
  }

  return { isOpen, label, error, busyId, returnFocus, open, run }
}
