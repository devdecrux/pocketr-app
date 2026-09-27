<script setup lang="ts">
import { onMounted } from 'vue'
import { UserRound, Users } from 'lucide-vue-next'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useHouseholdStore } from '@/stores/household'
import { useViewModeSelection } from '@/composables/useViewModeSelection'

const householdStore = useHouseholdStore()
const { currentValue, selectMode: onSelect } = useViewModeSelection()

onMounted(() => householdStore.loadHouseholds())
</script>

<template>
  <Select :model-value="currentValue" @update:model-value="onSelect">
    <SelectTrigger class="app-sidebar-select-trigger w-full">
      <SelectValue :placeholder="$t('common.formHints.selectMode')" />
    </SelectTrigger>
    <SelectContent>
      <SelectItem value="individual">
        <div class="flex items-center gap-2">
          <UserRound class="size-4" />
          <span>{{ $t('components.viewMode.individual') }}</span>
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
