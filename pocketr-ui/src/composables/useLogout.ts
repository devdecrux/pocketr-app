import { useRouter } from 'vue-router'
import { api } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

/** Ends the server session, clears the signed-in user and returns to the login page. */
export function useLogout() {
  const router = useRouter()
  const authStore = useAuthStore()

  async function logout(): Promise<void> {
    try {
      await api.post('/api/v1/user/logout', {
        body: new URLSearchParams(),
      })
    } finally {
      authStore.clearUser()
      await router.push('/login')
    }
  }

  return { logout }
}
