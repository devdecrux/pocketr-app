import { computed } from 'vue'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'

/** Settings route of the active household, only for its OWNER/ADMIN in household mode. */
export function useHouseholdSettingsPath() {
  const householdStore = useHouseholdStore()
  const modeStore = useModeStore()

  return computed(() => {
    if (!modeStore.isHousehold) return null
    const membership = householdStore.households.find((h) => h.id === modeStore.householdId)
    if (!membership || (membership.role !== 'OWNER' && membership.role !== 'ADMIN')) return null
    return `/household/${modeStore.householdId}/settings`
  })
}
