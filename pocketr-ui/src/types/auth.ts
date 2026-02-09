export interface AuthUser {
  id: number | null
  email: string
  username: string
  firstName: string | null
  lastName: string | null
  avatar?: string | null
}
