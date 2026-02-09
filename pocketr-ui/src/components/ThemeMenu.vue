<script setup lang="ts">
import {
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from '@/components/ui/dropdown-menu'
import { Monitor, Moon, Sun } from 'lucide-vue-next'
import { useColorMode } from '@vueuse/core'

const mode = useColorMode()
</script>

<template>
  <DropdownMenuLabel>Theme</DropdownMenuLabel>
  <DropdownMenuRadioGroup v-model="mode">
    <DropdownMenuRadioItem value="light">
      <Sun class="size-4" />
      Light
    </DropdownMenuRadioItem>
    <DropdownMenuRadioItem value="dark">
      <Moon class="size-4" />
      Dark
    </DropdownMenuRadioItem>
    <DropdownMenuRadioItem value="auto">
      <Monitor class="size-4" />
      System
    </DropdownMenuRadioItem>
  </DropdownMenuRadioGroup>
</template>
