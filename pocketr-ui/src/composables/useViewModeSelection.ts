import { computed } from 'vue'
import { useAccountStore } from '@/stores/account'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'

export const INDIVIDUAL_MODE_VALUE = 'individual'
const HOUSEHOLD_MODE_PREFIX = 'household:'

/** Individual / household view-mode selection shared by the legacy and Nuxt UI mode selectors. */
export function useViewModeSelection() {
  const modeStore = useModeStore()
  const accountStore = useAccountStore()
  const ledgerStore = useLedgerStore()

  const currentValue = computed(() => {
    if (modeStore.viewMode.kind === 'HOUSEHOLD') {
      return `${HOUSEHOLD_MODE_PREFIX}${modeStore.viewMode.householdId}`
    }
    return INDIVIDUAL_MODE_VALUE
  })

  // Select components emit loosely typed values; only string values are mode selections.
  async function selectMode(value: unknown): Promise<void> {
    if (typeof value !== 'string') return
    if (value === INDIVIDUAL_MODE_VALUE) {
      modeStore.switchToIndividual()
    } else if (value.startsWith(HOUSEHOLD_MODE_PREFIX)) {
      modeStore.switchToHousehold(value.slice(HOUSEHOLD_MODE_PREFIX.length))
    }
    await Promise.all([accountStore.load(), ledgerStore.load()])
  }

  return {
    currentValue,
    selectMode,
    householdValue: (id: string) => `${HOUSEHOLD_MODE_PREFIX}${id}`,
  }
}
