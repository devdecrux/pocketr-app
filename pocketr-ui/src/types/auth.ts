export type SupportedUserLanguage = 'en' | 'bg' | 'de'

export interface AuthUser {
  id: number | null
  email: string
  firstName: string | null
  lastName: string | null
  language: SupportedUserLanguage
  avatar?: string | null
}
