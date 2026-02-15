<script setup lang="ts">
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  type SidebarProps,
} from '@/components/ui/sidebar'
import {
  ArrowUpDown,
  ChevronsUpDown,
  Home,
  LogOut,
  Palette,
  RotateCw,
  User,
  WalletMinimal,
} from 'lucide-vue-next'
import { Separator } from '@/components/ui/separator'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import ThemeMenu from '@/components/ThemeMenu.vue'
import { useRouter } from 'vue-router'
import { initialsFromName } from '@/utils/initials'
import { useAuthStore } from '@/stores/auth'
import { api } from '@/api/http'

const routes = [
  { name: 'Dashboard', path: '/dashboard', icon: Home, enabled: true },
  { name: 'Transactions', path: '/transactions', icon: ArrowUpDown, enabled: true },
  { name: 'Accounts', path: '/accounts', icon: WalletMinimal, enabled: true },
  { name: 'Subscriptions', path: '/subscriptions', icon: RotateCw, enabled: false },
]

const props = withDefaults(defineProps<SidebarProps>(), {
  collapsible: 'icon',
})

const router = useRouter()
const authStore = useAuthStore()

async function logout(): Promise<void> {
  try {
    await api.post('/api/v1/user/logout', {
      body: new URLSearchParams(),
    })
  } finally {
    authStore.clearUser()
    await router.push('/login')
  }
}
</script>

<template>
  <Sidebar v-bind="props">
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton>
            <RouterLink to="/dashboard">
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-semibold">Pocketr</span>
                <span class="truncate text-xs">Your path to financial zen</span>
              </div>
            </RouterLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>

    <Separator class="bg-[#8fc79c] dark:bg-border" />

    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="route in routes" :key="route.path">
              <SidebarMenuButton v-if="route.enabled" as-child>
                <RouterLink :to="route.path">
                  <component :is="route.icon" />
                  <span>{{ route.name }}</span>
                </RouterLink>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>

    <SidebarFooter v-if="authStore.user != null">
      <SidebarMenu>
        <SidebarMenuItem>
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <SidebarMenuButton size="lg">
                <Avatar class="h-10 w-10 rounded-lg border border-border">
                  <AvatarImage v-if="authStore.user?.avatar" :src="authStore.user.avatar" />
                  <AvatarFallback class="rounded-lg border">
                    {{ initialsFromName(authStore.user?.firstName, authStore.user?.lastName) }}
                  </AvatarFallback>
                </Avatar>
                <div class="grid flex-1 text-left text-sm leading-tight">
                  <span class="truncate font-semibold">
                    {{ authStore.displayName }}
                  </span>
                  <span class="truncate text-xs">{{ authStore.user?.email }}</span>
                </div>
                <ChevronsUpDown class="ml-auto size-4" />
              </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuGroup>
                <DropdownMenuItem
                  class="font-semibold text-destructive focus:text-destructive dark:text-red-400 dark:focus:text-red-400"
                  @click="logout"
                >
                  <LogOut class="text-destructive dark:text-red-400" />
                  Log out
                </DropdownMenuItem>
                <DropdownMenuItem @click="router.push('/settings')">
                  <User />
                  Settings
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuSub>
                <DropdownMenuSubTrigger class="flex items-center gap-2">
                  <Palette class="size-4" />
                  Theme
                </DropdownMenuSubTrigger>
                <DropdownMenuSubContent>
                  <ThemeMenu />
                </DropdownMenuSubContent>
              </DropdownMenuSub>
            </DropdownMenuContent>
          </DropdownMenu>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarFooter>
  </Sidebar>
</template>
