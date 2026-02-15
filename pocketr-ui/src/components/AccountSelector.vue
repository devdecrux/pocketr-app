<script setup lang="ts">
import { computed } from 'vue'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAccountStore } from '@/stores/account'
import { useModeStore } from '@/stores/mode'
import type { AccountType } from '@/types/ledger'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    allowedTypes?: AccountType[]
    currency?: string
    placeholder?: string
  }>(),
  {
    modelValue: undefined,
    allowedTypes: undefined,
    currency: undefined,
    placeholder: 'Select account',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const accountStore = useAccountStore()
const modeStore = useModeStore()

const filteredAccounts = computed(() => {
  let list = accountStore.activeAccounts
  if (props.allowedTypes) {
    const allowed = new Set(props.allowedTypes)
    list = list.filter((a) => allowed.has(a.type))
  }
  if (props.currency) {
    list = list.filter((a) => a.currency === props.currency)
  }
  return list
})

const groupedAccounts = computed(() => {
  if (!modeStore.isHousehold) {
    const byType = new Map<AccountType, typeof filteredAccounts.value>()
    for (const a of filteredAccounts.value) {
      const list = byType.get(a.type) ?? []
      list.push(a)
      byType.set(a.type, list)
    }
    return [...byType.entries()].map(([type, accounts]) => ({
      label: type,
      accounts,
    }))
  }

  // In household mode, group by owner
  const byOwner = new Map<number, typeof filteredAccounts.value>()
  for (const a of filteredAccounts.value) {
    const list = byOwner.get(a.ownerUserId) ?? []
    list.push(a)
    byOwner.set(a.ownerUserId, list)
  }
  return [...byOwner.entries()].map(([ownerId, accounts]) => ({
    label: `Owner: ${ownerId}`,
    accounts,
  }))
})
</script>

<template>
  <Select
    :model-value="modelValue"
    @update:model-value="
      (val) => {
        if (typeof val === 'string') emit('update:modelValue', val)
      }
    "
  >
    <SelectTrigger class="w-full">
      <SelectValue :placeholder="placeholder" />
    </SelectTrigger>
    <SelectContent>
      <SelectGroup v-for="group in groupedAccounts" :key="group.label">
        <SelectLabel>{{ group.label }}</SelectLabel>
        <SelectItem v-for="account in group.accounts" :key="account.id" :value="account.id">
          {{ account.name }} ({{ account.currency }})
        </SelectItem>
      </SelectGroup>
    </SelectContent>
  </Select>
</template>
