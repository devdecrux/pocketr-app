import { describe, expect, it, vi } from 'vitest'
import { useCreateOverlay } from '@/components/overlays/useCreateOverlay'
import { ActionFailed, useTargetAction } from '@/components/overlays/useTargetAction'
import { isValidRolloverDay } from '@/utils/rollover'

interface Item {
  id: string
  name: string
}

const item: Item = { id: 'a', name: 'Alpha' }

describe('useTargetAction', () => {
  function setup(overrides: Partial<Parameters<typeof useTargetAction<Item>>[0]> = {}) {
    const perform = vi.fn(async () => undefined)
    const afterClose = vi.fn(async () => undefined)
    const state = useTargetAction<Item>({
      labelOf: (target) => target.name,
      perform,
      afterClose,
      errorMessage: () => 'failed',
      ...overrides,
    })
    return { ...state, perform, afterClose }
  }

  it('opens for an item, keeps its label and closes after a successful run', async () => {
    const { isOpen, label, open, run, perform, afterClose, busyId } = setup()

    open(item)
    expect(isOpen.value).toBe(true)
    expect(label.value).toBe('Alpha')

    await run()
    expect(perform).toHaveBeenCalledWith(item, undefined)
    expect(afterClose).toHaveBeenCalledOnce()
    expect(isOpen.value).toBe(false)
    expect(label.value).toBe('Alpha')
    expect(busyId.value).toBeNull()
  })

  it('cannot be closed or repeated while busy', async () => {
    let finish: () => void = () => {}
    const perform = vi.fn(() => new Promise<void>((resolve) => (finish = resolve)))
    const { isOpen, open, run, busyId } = setup({ perform })
    open(item)

    const running = run()
    expect(busyId.value).toBe('a')
    isOpen.value = false
    expect(isOpen.value).toBe(true)
    await run()
    expect(perform).toHaveBeenCalledOnce()

    finish()
    await running
    expect(isOpen.value).toBe(false)
  })

  it('closes on failure and shows the message, or stays open for a form', async () => {
    const closing = setup({ perform: vi.fn().mockRejectedValue(new Error('no')) })
    closing.open(item)
    await closing.run()
    expect(closing.isOpen.value).toBe(false)
    expect(closing.error.value).toBe('failed')

    const form = setup({ perform: vi.fn().mockRejectedValue(new Error('no')), closeOnError: false })
    form.open(item)
    await form.run()
    expect(form.isOpen.value).toBe(true)
    expect(form.error.value).toBe('failed')
  })

  it('shows the message of an ActionFailed as is', async () => {
    const { open, run, error } = setup({
      perform: vi.fn().mockRejectedValue(new ActionFailed('Store says no')),
    })
    open(item)
    await run()
    expect(error.value).toBe('Store says no')
  })

  it('reads the focus target after a success only', async () => {
    const button = document.createElement('button')
    const returnFocusTarget = vi.fn(() => button)
    const { open, run, returnFocus } = setup({ returnFocusTarget })

    open(item)
    expect(returnFocus.value).toBeNull()
    await run()
    expect(returnFocus.value).toBe(button)
  })
})

describe('useCreateOverlay', () => {
  it('remounts the form on every open and ignores a second submit while busy', async () => {
    let finish: () => void = () => {}
    const create = vi.fn(() => new Promise<void>((resolve) => (finish = resolve)))
    const afterCreate = vi.fn()
    const state = useCreateOverlay<string>({ create, afterCreate, errorMessage: () => 'failed' })

    state.open()
    const firstKey = state.formKey.value
    expect(state.isOpen.value).toBe(true)

    const submitting = state.submit('x')
    await state.submit('y')
    state.onOpenChange(false)
    expect(create).toHaveBeenCalledOnce()
    expect(state.isOpen.value).toBe(true)

    finish()
    await submitting
    expect(state.isOpen.value).toBe(false)
    expect(afterCreate).toHaveBeenCalledOnce()

    state.open()
    expect(state.formKey.value).toBe(firstKey + 1)
  })

  it('keeps the overlay open and shows the message when creating fails', async () => {
    const state = useCreateOverlay<string>({
      create: vi.fn().mockRejectedValue(new Error('no')),
      afterCreate: vi.fn(),
      errorMessage: () => 'failed',
    })
    state.open()
    await state.submit('x')
    expect(state.isOpen.value).toBe(true)
    expect(state.error.value).toBe('failed')
    expect(state.isBusy.value).toBe(false)
  })
})

describe('isValidRolloverDay', () => {
  it.each([
    [1, true],
    [31, true],
    [0, false],
    [32, false],
    [1.5, false],
    [null, false],
  ])('%s -> %s', (day, valid) => {
    expect(isValidRolloverDay(day)).toBe(valid)
  })
})
