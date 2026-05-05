<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { TriangleAlert } from 'lucide-vue-next'
import { primeCsrfToken } from '@/api/csrf'
import { api } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { sanitizeInternalRedirect } from '@/utils/sanitizeRedirect'
import type { AuthUser } from '@/types/auth'
import { AppFormField, AuthPageShell } from '@/components/app'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const isSessionExpired = computed(() => route.query.reason === 'session-expired')

const email = ref('')
const password = ref('')
const isAlert = ref(false)
const isSubmitting = ref(false)

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
  <AuthPageShell>
    <Card class="w-full max-w-sm">
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('views.auth.login.title') }}</CardTitle>
        <CardDescription>{{ $t('views.auth.login.description') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <div
          v-if="isSessionExpired"
          class="mb-4 flex items-center gap-2 rounded-md border border-border bg-muted px-4 py-3 text-sm text-foreground shadow-sm"
        >
          <TriangleAlert class="h-4 w-4 shrink-0 text-destructive" />
          <span>{{ $t('views.auth.login.errors.sessionExpired') }}</span>
        </div>
        <form @submit.prevent="login">
          <div class="grid gap-4">
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

            <p v-if="isAlert" class="text-sm text-destructive">
              {{ $t('views.auth.login.errors.invalidCredentials') }}
            </p>

            <Button type="submit" :disabled="isSubmitting" class="w-full">
              {{ isSubmitting ? $t('common.feedback.loggingIn') : $t('views.auth.login.submit') }}
            </Button>
          </div>
        </form>
        <div class="mt-4 text-center text-sm">
          {{ $t('views.auth.login.links.registerPrompt') }}
          <RouterLink to="/registration" class="text-(--app-button-fg) underline">
            {{ $t('common.actions.signUp') }}
          </RouterLink>
        </div>
      </CardContent>
    </Card>
  </AuthPageShell>
</template>
