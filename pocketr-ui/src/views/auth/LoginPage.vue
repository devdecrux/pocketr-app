<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Moon, Sun, TriangleAlert } from 'lucide-vue-next'
import { useColorMode } from '@vueuse/core'
import { primeCsrfToken } from '@/api/csrf'
import { api } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { sanitizeInternalRedirect } from '@/utils/sanitizeRedirect'
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

    const redirectTarget = sanitizeInternalRedirect(route.query.redirect) ?? '/dashboard'
    await router.push(redirectTarget)
  } catch {
    isAlert.value = true
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="app-shell relative flex min-h-screen w-full items-center justify-center px-4 py-8">
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
    <Card class="w-full max-w-sm">
      <CardHeader>
        <CardTitle class="text-2xl">Login</CardTitle>
        <CardDescription> Enter your email below to login to your account </CardDescription>
      </CardHeader>
      <CardContent>
        <div
          v-if="isSessionExpired"
          class="mb-4 flex items-center gap-2 rounded-md border border-border bg-muted px-4 py-3 text-sm text-foreground shadow-sm"
        >
          <TriangleAlert class="h-4 w-4 shrink-0 text-destructive" />
          <span>Your session has expired. Please log in again.</span>
        </div>
        <form @submit.prevent="login">
          <div class="grid gap-4">
            <div class="grid gap-2">
              <Label for="email">Email</Label>
              <Input
                id="email"
                v-model="email"
                type="email"
                placeholder="email@example.com"
                required
              />
            </div>
            <div class="grid gap-2">
              <div class="flex items-center">
                <Label for="password">Password</Label>
              </div>
              <Input id="password" v-model="password" type="password" required />
            </div>

            <p v-if="isAlert" class="text-sm text-destructive">
              Invalid email or password. Please try again.
            </p>

            <Button type="submit" :disabled="isSubmitting" class="w-full">
              {{ isSubmitting ? 'Logging in...' : 'Login' }}
            </Button>
          </div>
        </form>
        <div class="mt-4 text-center text-sm">
          Don't have an account?
          <RouterLink to="/registration" class="text-(--app-button-fg) underline">
            Sign up
          </RouterLink>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
