import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHouseholdStore } from '@/stores/household'

// The store keeps the last household it loaded, so only a matching one belongs to this page.
export function useCurrentHousehold() {
  const route = useRoute()
  const householdStore = useHouseholdStore()

  const householdId = computed(() => route.params.householdId as string)
  const household = computed(() =>
    householdStore.currentHousehold?.id === householdId.value
      ? householdStore.currentHousehold
      : null,
  )

  return { householdId, household }
}
