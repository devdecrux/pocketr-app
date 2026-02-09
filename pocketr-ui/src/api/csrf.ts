import { api } from '@/api/http'

export async function primeCsrfToken(): Promise<void> {
  if (!import.meta.env.DEV) {
    return
  }

  try {
    await api.get('/api/v1/internal/csrf-token')
  } catch (error) {
    console.warn('Unable to preload CSRF token.', error)
  }
}
