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
  SidebarSeparator,
} from '@/components/ui/sidebar'
import {
  ArrowUpDown,
  ChevronsUpDown,
  LayoutDashboard,
  LogOut,
  Palette,
  RotateCw,
  Shapes,
  User,
  Users,
  WalletMinimal,
} from 'lucide-vue-next'
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
import ModeSwitcher from '@/components/ModeSwitcher.vue'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { initialsFromName } from '@/utils/initials'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import { api } from '@/api/http'

const routes = [
  {
    nameKey: 'components.sidebar.routes.dashboard',
    path: '/dashboard',
    icon: LayoutDashboard,
    enabled: true,
  },
  {
    nameKey: 'components.sidebar.routes.transactions',
    path: '/transactions',
    icon: ArrowUpDown,
    enabled: true,
  },
  {
    nameKey: 'components.sidebar.routes.accounts',
    path: '/accounts',
    icon: WalletMinimal,
    enabled: true,
  },
  {
    nameKey: 'components.sidebar.routes.categories',
    path: '/categories',
    icon: Shapes,
    enabled: true,
  },
  {
    nameKey: 'components.sidebar.routes.subscriptions',
    path: '/subscriptions',
    icon: RotateCw,
    enabled: false,
  },
]

const householdStore = useHouseholdStore()
const modeStore = useModeStore()

const householdSettingsPath = computed(() => {
  if (!modeStore.isHousehold) return null
  const membership = householdStore.households.find((h) => h.id === modeStore.householdId)
  if (!membership || (membership.role !== 'OWNER' && membership.role !== 'ADMIN')) return null
  return `/household/${modeStore.householdId}/settings`
})

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
          <SidebarMenuButton as-child class="app-sidebar-brand-button h-auto">
            <RouterLink class="flex w-full items-start" to="/dashboard">
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-semibold">{{ $t('app.name') }}</span>
                <span class="app-sidebar-subtitle truncate text-xs">{{ $t('app.tagline') }}</span>
              </div>
            </RouterLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>

    <SidebarSeparator class="app-sidebar-divider" />

    <SidebarGroup>
      <SidebarGroupContent class="px-2">
        <ModeSwitcher />
      </SidebarGroupContent>
    </SidebarGroup>

    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="route in routes" :key="route.path">
              <SidebarMenuButton v-if="route.enabled" as-child class="app-sidebar-nav-button">
                <RouterLink :to="route.path">
                  <span class="app-sidebar-nav-icon">
                    <component :is="route.icon" class="size-4" />
                  </span>
                  <span>{{ $t(route.nameKey) }}</span>
                </RouterLink>
              </SidebarMenuButton>
            </SidebarMenuItem>
            <SidebarMenuItem v-if="householdSettingsPath">
              <SidebarMenuButton as-child class="app-sidebar-nav-button">
                <RouterLink :to="householdSettingsPath">
                  <span class="app-sidebar-nav-icon">
                    <Users class="size-4" />
                  </span>
                  <span>{{ $t('components.sidebar.household') }}</span>
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
              <SidebarMenuButton class="app-sidebar-user-button" size="lg">
                <Avatar class="app-sidebar-avatar h-10 w-10 rounded-lg border">
                  <AvatarImage v-if="authStore.user?.avatar" :src="authStore.user.avatar" />
                  <AvatarFallback class="app-sidebar-avatar rounded-lg border">
                    {{ initialsFromName(authStore.user?.firstName, authStore.user?.lastName) }}
                  </AvatarFallback>
                </Avatar>
                <div class="grid flex-1 text-left text-sm leading-tight">
                  <span class="truncate font-semibold">
                    {{ authStore.displayName }}
                  </span>
                  <span class="app-sidebar-meta truncate text-xs">{{ authStore.user?.email }}</span>
                </div>
                <ChevronsUpDown class="ml-auto size-4" />
              </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuGroup>
                <DropdownMenuItem
                  class="font-semibold text-[var(--app-sidebar-danger-fg)] focus:text-[var(--app-sidebar-danger-fg)]"
                  @click="logout"
                >
                  <LogOut class="text-[var(--app-sidebar-danger-fg)]" />
                  {{ $t('common.actions.logOut') }}
                </DropdownMenuItem>
                <DropdownMenuItem @click="router.push('/settings')">
                  <User />
                  {{ $t('common.actions.settings') }}
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuSub>
                <DropdownMenuSubTrigger class="flex items-center gap-2">
                  <Palette class="size-4" />
                  {{ $t('components.theme.label') }}
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
