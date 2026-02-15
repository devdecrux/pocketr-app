<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, ChevronsUpDown } from 'lucide-vue-next'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useCategoryStore } from '@/stores/category'

const props = defineProps<{
  modelValue?: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void
}>()

const open = ref(false)
const categoryStore = useCategoryStore()

const selectedLabel = computed(() => {
  if (!props.modelValue) return ''
  const cat = categoryStore.categories.find((c) => c.id === props.modelValue)
  return cat?.name ?? ''
})

function onSelect(id: string): void {
  emit('update:modelValue', id === props.modelValue ? null : id)
  open.value = false
}
</script>

<template>
  <Popover v-model:open="open">
    <PopoverTrigger as-child>
      <Button
        variant="outline"
        role="combobox"
        :aria-expanded="open"
        class="w-full justify-between"
      >
        {{ selectedLabel || 'Select category...' }}
        <ChevronsUpDown class="ml-2 size-4 shrink-0 opacity-50" />
      </Button>
    </PopoverTrigger>
    <PopoverContent class="w-[--reka-popover-trigger-width] p-0">
      <Command>
        <CommandInput placeholder="Search categories..." />
        <CommandList>
          <CommandEmpty>No categories found.</CommandEmpty>
          <CommandGroup>
            <CommandItem
              v-for="cat in categoryStore.categories"
              :key="cat.id"
              :value="cat.name"
              @select="onSelect(cat.id)"
            >
              <Check
                :class="cn('mr-2 size-4', modelValue === cat.id ? 'opacity-100' : 'opacity-0')"
              />
              {{ cat.name }}
            </CommandItem>
          </CommandGroup>
        </CommandList>
      </Command>
    </PopoverContent>
  </Popover>
</template>
