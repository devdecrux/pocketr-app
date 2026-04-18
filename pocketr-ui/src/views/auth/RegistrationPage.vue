<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Moon, Sun } from 'lucide-vue-next'
import { useColorMode } from '@vueuse/core'
import { api } from '@/api/http'
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

const firstName = ref('')
const lastName = ref('')
const password = ref('')
const confirmPassword = ref('')
const email = ref('')
const isAlert = ref(false)
const alertMessage = ref('Unable to register user')
const isSubmitting = ref(false)

const router = useRouter()

useColorMode()

async function register(): Promise<void> {
  isAlert.value = false
  alertMessage.value = 'Unable to register user'

  if (password.value !== confirmPassword.value) {
    isAlert.value = true
    alertMessage.value = 'Passwords do not match'
    return
  }

  isSubmitting.value = true

  try {
    await api.post('/api/v1/user/register', {
      json: {
        password: password.value,
        email: email.value.trim(),
        firstName: firstName.value.trim(),
        lastName: lastName.value.trim(),
      },
    })

    await router.push('/login')
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
    <Card class="w-full max-w-md">
      <CardHeader>
        <CardTitle class="text-xl">Sign Up</CardTitle>
        <CardDescription> Enter your information to create an account </CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="register">
          <div class="grid gap-4">
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="grid gap-2">
                <Label for="first-name">First name</Label>
                <Input id="first-name" v-model="firstName" placeholder="John" required />
              </div>
              <div class="grid gap-2">
                <Label for="last-name">Last name</Label>
                <Input id="last-name" v-model="lastName" placeholder="Doe" required />
              </div>
            </div>
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
              <Label for="password">Password</Label>
              <Input id="password" v-model="password" type="password" required />
            </div>
            <div class="grid gap-2">
              <Label for="confirm-password">Confirm password</Label>
              <Input id="confirm-password" v-model="confirmPassword" type="password" required />
            </div>

            <p v-if="isAlert" class="text-sm text-destructive">
              {{ alertMessage }}
            </p>

            <Button type="submit" class="w-full" :disabled="isSubmitting">
              {{ isSubmitting ? 'Creating account...' : 'Create an account' }}
            </Button>
          </div>
        </form>
        <div class="mt-4 text-center text-sm">
          Already have an account?
          <RouterLink to="/login" class="text-(--app-button-fg) underline"> Sign in </RouterLink>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
