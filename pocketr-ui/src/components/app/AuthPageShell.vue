<script setup lang="ts">
import { Moon, Sun } from 'lucide-vue-next'
import ThemeMenu from '@/components/ThemeMenu.vue'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
</script>

<template>
  <div class="app-shell relative flex min-h-screen w-full items-center justify-center px-4 py-8">
    <div class="absolute top-6 right-6">
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button variant="outline" size="icon" :aria-label="$t('components.theme.label')">
            <Sun class="h-4 w-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
            <Moon
              class="absolute h-4 w-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100"
            />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <ThemeMenu />
        </DropdownMenuContent>
      </DropdownMenu>
    </div>

    <slot />
  </div>
</template>
