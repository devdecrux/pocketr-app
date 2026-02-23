import ky from 'ky'
import { getActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

function getCookieValue(name: string): string | null {
  if (typeof document === 'undefined') {
    return null
  }

  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith(`${name}=`))

  if (!cookie) {
    return null
  }

  return cookie.split('=').slice(1).join('=')
}

export const api = ky.create({
  credentials: 'include',
  hooks: {
    beforeRequest: [
      (request) => {
        const xsrfToken = getCookieValue('XSRF-TOKEN')

        if (xsrfToken) {
          request.headers.set('X-XSRF-TOKEN', decodeURIComponent(xsrfToken))
        }
      },
    ],
    afterResponse: [
      (_request, _options, response) => {
        if (response.status === 401) {
          const pinia = getActivePinia()
          if (pinia) {
            const authStore = useAuthStore(pinia)
            if (authStore.initialized && authStore.isAuthenticated) {
              void authStore.handleSessionExpired()
            }
          }
        }
        return response
      },
    ],
  },
  retry: 0,
})
