import { api } from '@/api/http'
import type { AuthUser, SupportedUserLanguage } from '@/types/auth'

const BASE = '/api/v1/user'

export function updateUserLanguage(language: SupportedUserLanguage): Promise<AuthUser> {
  return api.patch(`${BASE}/language`, { json: { language } }).json<AuthUser>()
}
