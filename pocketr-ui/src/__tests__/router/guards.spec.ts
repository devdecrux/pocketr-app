import { describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'
import { api } from '@/api/http'
import router from '@/router'
import { authGuard } from '@/router/guards'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { buildUser, signIn } from '@/__tests__/support/fixtures'

function routeTo(meta: RouteLocationNormalized['meta'], fullPath: string): RouteLocationNormalized {
  return { meta, fullPath } as RouteLocationNormalized
}

function startSession(signedIn: boolean) {
  const authStore = useAuthStore()
  authStore.initialized = true
  if (signedIn) signIn()
}

describe('route table', () => {
  const GUEST_ONLY = ['login', 'registration']
  const routes = router.getRoutes().filter((route) => route.name)

  it('keeps every other route behind sign-in', () => {
    const protectedRoutes = routes.filter((route) => !GUEST_ONLY.includes(String(route.name)))

    expect(protectedRoutes.length).toBeGreaterThan(0)
    expect(protectedRoutes.map((route) => [String(route.name), route.meta.requiresAuth])).toEqual(
      protectedRoutes.map((route) => [String(route.name), true]),
    )
  })

  it('keeps login and registration for guests only', () => {
    const found = GUEST_ONLY.map((name) => routes.find((candidate) => candidate.name === name))

    expect(found.map((route) => [route?.name, route?.meta.guestOnly])).toEqual(
      GUEST_ONLY.map((name) => [name, true]),
    )
    expect(found.map((route) => [route?.name, Boolean(route?.meta.requiresAuth)])).toEqual(
      GUEST_ONLY.map((name) => [name, false]),
    )
  })
})

describe('authGuard', () => {
  it('sends a guest on a protected URL to sign in with a redirect back', async () => {
    startSession(false)
    const validate = vi.spyOn(useHouseholdStore(), 'ensureModeValidated')

    await expect(authGuard(routeTo({ requiresAuth: true }, '/nope?x=1'))).resolves.toEqual({
      name: 'login',
      query: { redirect: '/nope?x=1' },
    })
    expect(validate).not.toHaveBeenCalled()
  })

  it('restores the session first, so a signed-in user is not sent to sign in', async () => {
    const json = vi.fn().mockResolvedValue(buildUser())
    vi.mocked(api.get).mockReturnValue({ json } as never)

    await expect(authGuard(routeTo({ requiresAuth: true }, '/accounts'))).resolves.toBe(true)

    expect(api.get).toHaveBeenCalledWith('/api/v1/user')
    expect(useAuthStore().isAuthenticated).toBe(true)
  })

  it('sends a guest whose session cannot be restored to sign in', async () => {
    vi.mocked(api.get).mockReturnValue({
      json: vi.fn().mockRejectedValue(new Error('401')),
    } as never)

    await expect(authGuard(routeTo({ requiresAuth: true }, '/accounts'))).resolves.toEqual({
      name: 'login',
      query: { redirect: '/accounts' },
    })
  })

  it('sends a signed-in user away from guest-only pages', async () => {
    startSession(true)

    await expect(authGuard(routeTo({ guestOnly: true }, '/login'))).resolves.toEqual({
      name: 'dashboard',
    })
  })

  it('lets guests open guest-only pages', async () => {
    startSession(false)

    await expect(authGuard(routeTo({ guestOnly: true }, '/login'))).resolves.toBe(true)
  })

  it('waits for household-mode validation before entering protected routes', async () => {
    startSession(true)
    let finishValidation = () => {}
    vi.spyOn(useHouseholdStore(), 'ensureModeValidated').mockReturnValue(
      new Promise<void>((resolve) => (finishValidation = resolve)),
    )

    let settled = false
    const result = authGuard(routeTo({ requiresAuth: true }, '/dashboard')).then((value) => {
      settled = true
      return value
    })
    await Promise.resolve()
    expect(settled).toBe(false)

    finishValidation()
    await expect(result).resolves.toBe(true)
  })
})
