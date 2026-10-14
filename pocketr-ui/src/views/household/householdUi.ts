import type { HouseholdRole } from '@/types/household'
import { translate } from '@/i18n/translate'

export const ROLE_BADGE: Record<
  HouseholdRole,
  { color: 'primary' | 'neutral'; variant: 'soft' | 'outline' }
> = {
  OWNER: { color: 'primary', variant: 'soft' },
  ADMIN: { color: 'primary', variant: 'outline' },
  MEMBER: { color: 'neutral', variant: 'soft' },
}

export function displayPerson(
  firstName: string | null | undefined,
  lastName: string | null | undefined,
  email: string | null | undefined,
): string {
  const name = `${firstName?.trim() ?? ''} ${lastName?.trim() ?? ''}`.trim()
  return name || (email?.trim() ?? translate('common.states.unknownUser'))
}
