export function initialsFromName(firstName?: string | null, lastName?: string | null) {
  const firstInitial = firstName?.trim().charAt(0) ?? ''
  const lastInitial = lastName?.trim().charAt(0) ?? ''
  const initials = `${firstInitial}${lastInitial}`.toUpperCase()
  return initials || '?'
}
