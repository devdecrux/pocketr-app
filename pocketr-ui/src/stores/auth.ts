import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api/http'
import { primeCsrfToken } from '@/api/csrf'
import type { AuthUser } from '@/types/auth'

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

  return {
    user,
    initialized,
    isAuthenticated,
    displayName,
    setUser,
    clearUser,
    hydrateFromSession,
  }
})
