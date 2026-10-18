import { describe, expect, it, vi } from 'vitest'
import { api } from '@/api/http'
import router from '@/router'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { buildAccount, buildUser, signIn } from '@/__tests__/support/fixtures'

describe('auth store', () => {
  it('restores the signed-in user from the session once', async () => {
    vi.mocked(api.get).mockReturnValue({
      json: vi.fn().mockResolvedValue(buildUser({ firstName: 'Sam' })),
    } as never)
    const store = useAuthStore()

    await store.hydrateFromSession()
    await store.hydrateFromSession()

    expect(store.user?.firstName).toBe('Sam')
    expect(store.initialized).toBe(true)
    expect(api.get).toHaveBeenCalledOnce()
  })

  it('stays signed out when the session cannot be restored', async () => {
    vi.mocked(api.get).mockReturnValue({
      json: vi.fn().mockRejectedValue(new Error('401')),
    } as never)
    const store = useAuthStore()

    await store.hydrateFromSession()

    expect(store.isAuthenticated).toBe(false)
    expect(store.initialized).toBe(true)
  })

  it('on session expiry clears the user and cached data and sends them to sign in', async () => {
    signIn()
    useAccountStore().accounts = [buildAccount()]
    const store = useAuthStore()

    await store.handleSessionExpired()

    expect(store.user).toBeNull()
    expect(useAccountStore().accounts).toEqual([])
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query).toEqual({ reason: 'session-expired' })
  })
})
