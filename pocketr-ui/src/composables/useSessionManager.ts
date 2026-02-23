import { onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

export function useSessionManager(): void {
  const authStore = useAuthStore()

  async function onVisibilityChange(): Promise<void> {
    if (document.visibilityState === 'visible') {
      await authStore.checkSession()
    }
  }

  onMounted(() => document.addEventListener('visibilitychange', onVisibilityChange))
  onUnmounted(() => document.removeEventListener('visibilitychange', onVisibilityChange))
}
