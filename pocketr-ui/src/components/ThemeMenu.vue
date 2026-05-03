<script setup lang="ts">
import {
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from '@/components/ui/dropdown-menu'
import { Monitor, Moon, Sun } from 'lucide-vue-next'
import { useAppTheme } from '@/composables/useAppTheme'

const { mode, options } = useAppTheme()

const themeIcons = {
  light: Sun,
  dark: Moon,
  auto: Monitor,
} as const
</script>

<template>
  <DropdownMenuLabel>{{ $t('components.theme.label') }}</DropdownMenuLabel>
  <DropdownMenuRadioGroup v-model="mode">
    <DropdownMenuRadioItem v-for="option in options" :key="option.value" :value="option.value">
      <component :is="themeIcons[option.value]" class="size-4" />
      {{ $t(option.labelKey) }}
    </DropdownMenuRadioItem>
  </DropdownMenuRadioGroup>
</template>
