import type { RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'

export async function authGuard(to: RouteLocationNormalized) {
  const authStore = useAuthStore()

  if (!authStore.initialized) {
    await authStore.hydrateFromSession()
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAuth && authStore.isAuthenticated) {
    const householdStore = useHouseholdStore()
    await householdStore.ensureModeValidated()
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { name: 'dashboard' }
  }

  return true
}
