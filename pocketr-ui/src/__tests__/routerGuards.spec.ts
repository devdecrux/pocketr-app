import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'
import { authGuard } from '@/router/guards'

const authStoreMock = {
  initialized: true,
  isAuthenticated: false,
  hydrateFromSession: vi.fn(async () => {}),
}

const householdStoreMock = {
  ensureModeValidated: vi.fn(async () => {}),
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStoreMock,
}))

vi.mock('@/stores/household', () => ({
  useHouseholdStore: () => householdStoreMock,
}))

function makeRoute(meta: Record<string, unknown>, fullPath: string): RouteLocationNormalized {
  return {
    meta,
    fullPath,
  } as RouteLocationNormalized
}

function deferred(): { promise: Promise<void>; resolve: () => void } {
  let resolve = () => {}
  const promise = new Promise<void>((nextResolve) => {
    resolve = nextResolve
  })
  return { promise, resolve }
}

describe('authGuard', () => {
  beforeEach(() => {
    authStoreMock.initialized = true
    authStoreMock.isAuthenticated = false
    authStoreMock.hydrateFromSession.mockReset()
    householdStoreMock.ensureModeValidated.mockReset()
    householdStoreMock.ensureModeValidated.mockResolvedValue(undefined)
  })

  it('waits for household-mode validation before entering protected routes', async () => {
    authStoreMock.isAuthenticated = true

    const blocker = deferred()
    householdStoreMock.ensureModeValidated.mockReturnValueOnce(blocker.promise)

    let settled = false
    const guardResult = authGuard(makeRoute({ requiresAuth: true }, '/dashboard'))
    void guardResult.then(() => {
      settled = true
    })

    await Promise.resolve()
    expect(householdStoreMock.ensureModeValidated).toHaveBeenCalledTimes(1)
    expect(settled).toBe(false)

    blocker.resolve()
    await expect(guardResult).resolves.toBe(true)
  })
})
