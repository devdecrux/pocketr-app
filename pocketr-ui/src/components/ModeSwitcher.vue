<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { UserRound, Users } from 'lucide-vue-next'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useModeStore } from '@/stores/mode'
import { useAccountStore } from '@/stores/account'
import { useLedgerStore } from '@/stores/ledger'
import { useHouseholdStore } from '@/stores/household'

const modeStore = useModeStore()
const accountStore = useAccountStore()
const ledgerStore = useLedgerStore()
const householdStore = useHouseholdStore()

onMounted(() => householdStore.loadHouseholds())

const currentValue = computed(() => {
  if (modeStore.viewMode.kind === 'HOUSEHOLD') {
    return `household:${modeStore.viewMode.householdId}`
  }
  return 'individual'
})

// reka-ui Select emits AcceptableValue (string | number | bigint | Record | null),
// but our values are always strings, so we guard with typeof check
async function onSelect(
  value: string | number | bigint | Record<string, unknown> | null,
): Promise<void> {
  if (typeof value !== 'string') return
  if (value === 'individual') {
    modeStore.switchToIndividual()
  } else if (value.startsWith('household:')) {
    const id = value.slice('household:'.length)
    modeStore.switchToHousehold(id)
  }
  await Promise.all([accountStore.load(), ledgerStore.load()])
}
</script>

<template>
  <Select :model-value="currentValue" @update:model-value="onSelect">
    <SelectTrigger class="w-full">
      <SelectValue placeholder="Select mode" />
    </SelectTrigger>
    <SelectContent>
      <SelectItem value="individual">
        <div class="flex items-center gap-2">
          <UserRound class="size-4" />
          <span>Individual</span>
        </div>
      </SelectItem>
      <SelectItem v-for="h in householdStore.households" :key="h.id" :value="`household:${h.id}`">
        <div class="flex items-center gap-2">
          <Users class="size-4" />
          <span>{{ h.name }}</span>
        </div>
      </SelectItem>
    </SelectContent>
  </Select>
</template>
