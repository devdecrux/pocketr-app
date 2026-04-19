<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api/http'
import { AppFormField, AuthPageShell } from '@/components/app'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'

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
  <AuthPageShell>
    <Card class="w-full max-w-md">
      <CardHeader>
        <CardTitle class="text-xl">Sign Up</CardTitle>
        <CardDescription> Enter your information to create an account </CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="register">
          <div class="grid gap-4">
            <div class="grid gap-4 sm:grid-cols-2">
              <AppFormField label="First name" control-id="first-name">
                <Input id="first-name" v-model="firstName" placeholder="John" required />
              </AppFormField>
              <AppFormField label="Last name" control-id="last-name">
                <Input id="last-name" v-model="lastName" placeholder="Doe" required />
              </AppFormField>
            </div>
            <AppFormField label="Email" control-id="email">
              <Input
                id="email"
                v-model="email"
                type="email"
                placeholder="email@example.com"
                required
              />
            </AppFormField>
            <AppFormField label="Password" control-id="password">
              <Input id="password" v-model="password" type="password" required />
            </AppFormField>
            <AppFormField label="Confirm password" control-id="confirm-password">
              <Input id="confirm-password" v-model="confirmPassword" type="password" required />
            </AppFormField>

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
  </AuthPageShell>
</template>
