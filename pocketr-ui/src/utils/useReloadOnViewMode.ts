import { onMounted, watch } from 'vue'
import { useModeStore } from '@/stores/mode'

/**
 * Runs `load` when the page mounts and again whenever the view mode (individual or a household)
 * changes. `onSwitch` runs first on a switch, for state that belongs to the old mode.
 */
export function useReloadOnViewMode(load: () => Promise<void>, onSwitch?: () => void): void {
  const modeStore = useModeStore()

  onMounted(load)

  watch(
    () => modeStore.viewMode,
    () => {
      onSwitch?.()
      void load()
    },
    { deep: true },
  )
}
