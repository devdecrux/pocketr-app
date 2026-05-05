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
const alertMessage = ref('')
const isSubmitting = ref(false)

const router = useRouter()

async function register(): Promise<void> {
  isAlert.value = false
  alertMessage.value = ''

  if (password.value !== confirmPassword.value) {
    isAlert.value = true
    alertMessage.value = 'views.auth.registration.errors.passwordsMismatch'
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
    alertMessage.value = 'views.auth.registration.errors.unableToRegister'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthPageShell>
    <Card class="w-full max-w-md">
      <CardHeader>
        <CardTitle class="text-xl">{{ $t('views.auth.registration.title') }}</CardTitle>
        <CardDescription>{{ $t('views.auth.registration.description') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="register">
          <div class="grid gap-4">
            <div class="grid gap-4 sm:grid-cols-2">
              <AppFormField :label="$t('common.fields.firstName')" control-id="first-name">
                <Input
                  id="first-name"
                  v-model="firstName"
                  :placeholder="$t('common.formHints.personFirstName')"
                  required
                />
              </AppFormField>
              <AppFormField :label="$t('common.fields.lastName')" control-id="last-name">
                <Input
                  id="last-name"
                  v-model="lastName"
                  :placeholder="$t('common.formHints.personLastName')"
                  required
                />
              </AppFormField>
            </div>
            <AppFormField :label="$t('common.fields.email')" control-id="email">
              <Input
                id="email"
                v-model="email"
                type="email"
                :placeholder="$t('common.formHints.email')"
                required
              />
            </AppFormField>
            <AppFormField :label="$t('common.fields.password')" control-id="password">
              <Input id="password" v-model="password" type="password" required />
            </AppFormField>
            <AppFormField
              :label="$t('common.fields.confirmPassword')"
              control-id="confirm-password"
            >
              <Input id="confirm-password" v-model="confirmPassword" type="password" required />
            </AppFormField>

            <p v-if="isAlert" class="text-sm text-destructive">
              {{ $t(alertMessage) }}
            </p>

            <Button type="submit" class="w-full" :disabled="isSubmitting">
              {{
                isSubmitting
                  ? $t('common.feedback.creatingAccount')
                  : $t('views.auth.registration.submit')
              }}
            </Button>
          </div>
        </form>
        <div class="mt-4 text-center text-sm">
          {{ $t('views.auth.registration.links.loginPrompt') }}
          <RouterLink to="/login" class="text-(--app-button-fg) underline">
            {{ $t('common.actions.signIn') }}
          </RouterLink>
        </div>
      </CardContent>
    </Card>
  </AuthPageShell>
</template>
