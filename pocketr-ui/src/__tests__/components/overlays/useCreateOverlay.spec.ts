import { describe, expect, it, vi } from 'vitest'
import { useCreateOverlay } from '@/components/overlays/useCreateOverlay'

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
