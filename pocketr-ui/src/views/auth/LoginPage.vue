<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Moon, Sun } from 'lucide-vue-next'
import { useColorMode } from '@vueuse/core'
import { primeCsrfToken } from '@/api/csrf'
import { api } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { AuthUser } from '@/types/auth'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import ThemeMenu from '@/components/ThemeMenu.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const isSessionExpired = computed(() => route.query.reason === 'session-expired')

const email = ref('')
const password = ref('')
const isAlert = ref(false)
const isSubmitting = ref(false)

useColorMode()

async function login(): Promise<void> {
  isAlert.value = false
  isSubmitting.value = true

  try {
    await primeCsrfToken()

    const body = new URLSearchParams()
    body.set('email', email.value.trim())
    body.set('password', password.value)

    await api.post('/api/v1/user/login', {
      body,
    })

    const user = await api.get('/api/v1/user').json<AuthUser>()
    authStore.setUser(user)

    const requestedRedirect = route.query.redirect
    const redirectTarget = typeof requestedRedirect === 'string' ? requestedRedirect : '/dashboard'
    await router.push(redirectTarget)
  } catch {
    isAlert.value = true
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div
    class="relative flex h-screen w-full items-center justify-center bg-background text-foreground"
  >
    <div class="absolute top-6 right-6">
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button variant="outline" size="icon" aria-label="Theme">
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
    <div
      v-if="isSessionExpired"
      class="absolute top-16 left-1/2 -translate-x-1/2 w-full max-w-sm px-4"
    >
      <div
        class="rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-700 dark:bg-amber-950/50 dark:text-amber-300"
      >
        Your session has expired. Please log in again.
      </div>
    </div>
    <Card
      class="min-w-sm max-w-sm border-[#9ccfad] bg-[#a8e0b7] text-[#2f463a] shadow-[0_10px_30px_rgba(0,0,0,0.18)] dark:border-border dark:bg-card dark:text-card-foreground"
    >
      <CardHeader>
        <CardTitle class="text-2xl text-[#1f3a2f] dark:text-card-foreground">Login</CardTitle>
        <CardDescription class="text-[#355043] dark:text-muted-foreground">
          Enter your email below to login to your account
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="login">
          <div class="grid gap-4">
            <div class="grid gap-2">
              <Label class="text-[#1f3a2f] dark:text-foreground" for="email">Email</Label>
              <Input
                id="email"
                v-model="email"
                type="email"
                autocomplete="email"
                placeholder="email@example.com"
                class="border-[#7fbf9a] focus-visible:border-[#6fb08a] focus-visible:ring-[#6fb08a]/40 dark:border-border dark:focus-visible:ring-ring/50"
                required
              />
            </div>
            <div class="grid gap-2">
              <div class="flex items-center">
                <Label class="text-[#1f3a2f] dark:text-foreground" for="password">Password</Label>
              </div>
              <Input
                id="password"
                v-model="password"
                type="password"
                autocomplete="current-password"
                class="border-[#7fbf9a] focus-visible:border-[#6fb08a] focus-visible:ring-[#6fb08a]/40 dark:border-border dark:focus-visible:ring-ring/50"
                required
              />
            </div>

            <p v-if="isAlert" class="text-sm text-red-600 dark:text-red-400">
              Invalid email or password. Please try again.
            </p>

            <Button
              type="submit"
              :disabled="isSubmitting"
              class="w-full hover:bg-[#bee4c7] disabled:cursor-not-allowed disabled:opacity-70 dark:hover:bg-primary/90"
            >
              {{ isSubmitting ? 'Logging in...' : 'Login' }}
            </Button>
          </div>
        </form>
        <div class="mt-4 text-center text-sm">
          Don't have an account?
          <RouterLink to="/registration" class="text-[#3f7f5c] underline dark:text-primary">
            Sign up
          </RouterLink>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
