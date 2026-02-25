import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api/http'
import { primeCsrfToken } from '@/api/csrf'
import type { AuthUser } from '@/types/auth'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const displayName = computed(() => {
    if (!user.value) {
      return ''
    }

    const firstName = user.value.firstName?.trim() ?? ''
    const lastName = user.value.lastName?.trim() ?? ''
    const fullName = `${firstName} ${lastName}`.trim()

    return fullName || user.value.email
  })

  function setUser(nextUser: AuthUser): void {
    user.value = nextUser
  }

  function clearUser(): void {
    user.value = null
    void primeCsrfToken()
  }

  async function hydrateFromSession(): Promise<void> {
    if (initialized.value) {
      return
    }

    try {
      const nextUser = await api.get('/api/v1/user').json<AuthUser>()
      setUser(nextUser)
    } catch {
      clearUser()
    } finally {
      initialized.value = true
    }
  }

  async function handleSessionExpired(): Promise<void> {
    if (!isAuthenticated.value) return
    clearUser()
    const { resetAllDomainStores } = await import('@/utils/resetStores')
    await resetAllDomainStores()
    await router.replace({ name: 'login', query: { reason: 'session-expired' } })
  }

  async function checkSession(): Promise<void> {
    if (!initialized.value || !isAuthenticated.value) return
    try {
      await api.get('/api/v1/user')
    } catch {
      // 401 already handled by afterResponse hook → handleSessionExpired()
      // Network/other errors intentionally swallowed
    }
  }

  return {
    user,
    initialized,
    isAuthenticated,
    displayName,
    setUser,
    clearUser,
    hydrateFromSession,
    handleSessionExpired,
    checkSession,
  }
})
