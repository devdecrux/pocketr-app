<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api/http'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const firstName = ref('')
const lastName = ref('')
const password = ref('')
const confirmPassword = ref('')
const email = ref('')
const isAlert = ref(false)
const alertMessage = ref('Unable to register user')
const isSubmitting = ref(false)

const router = useRouter()

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
  <div class="flex h-screen w-full items-center justify-center">
    <Card class="mx-auto min-w-sm max-w-sm">
      <CardHeader>
        <CardTitle class="text-xl">Sign Up</CardTitle>
        <CardDescription>Enter your information to create an account</CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="register">
          <div class="grid gap-4">
            <div class="grid grid-cols-2 gap-4">
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
                autocomplete="email"
                placeholder="email@example.com"
                required
              />
            </div>
            <div class="grid gap-2">
              <Label for="password">Password</Label>
              <Input
                id="password"
                v-model="password"
                type="password"
                autocomplete="new-password"
                required
              />
            </div>
            <div class="grid gap-2">
              <Label for="confirm-password">Confirm password</Label>
              <Input
                id="confirm-password"
                v-model="confirmPassword"
                type="password"
                autocomplete="new-password"
                required
              />
            </div>

            <p v-if="isAlert" class="text-sm text-red-600 dark:text-red-400">
              {{ alertMessage }}
            </p>

            <Button type="submit" class="w-full" :disabled="isSubmitting">
              {{ isSubmitting ? 'Creating account...' : 'Create an account' }}
            </Button>
          </div>
        </form>
        <div class="mt-4 text-center text-sm">
          Already have an account?
          <RouterLink to="/login" class="underline">Sign in</RouterLink>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
