import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { ViewMode } from '@/types/ledger'

const STORAGE_KEY = 'pocketr-view-mode'

function loadPersistedMode(): ViewMode {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as ViewMode
      if (parsed.kind === 'HOUSEHOLD' && parsed.householdId) {
        return parsed
      }
    }
  } catch {
    // ignore parse errors
  }
  return { kind: 'INDIVIDUAL' }
}

export const useModeStore = defineStore('viewMode', () => {
  const viewMode = ref<ViewMode>(loadPersistedMode())

  const isHousehold = computed(() => viewMode.value.kind === 'HOUSEHOLD')
  const householdId = computed(() =>
    viewMode.value.kind === 'HOUSEHOLD' ? viewMode.value.householdId : null,
  )
  const modeParam = computed<'INDIVIDUAL' | 'HOUSEHOLD'>(() => viewMode.value.kind)

  function switchToIndividual(): void {
    viewMode.value = { kind: 'INDIVIDUAL' }
  }

  function switchToHousehold(id: string): void {
    viewMode.value = { kind: 'HOUSEHOLD', householdId: id }
  }

  watch(
    viewMode,
    (val) => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
    },
    { deep: true },
  )

  return {
    viewMode,
    isHousehold,
    householdId,
    modeParam,
    switchToIndividual,
    switchToHousehold,
  }
})
