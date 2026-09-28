<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { primeCsrfToken } from '@/api/csrf'
import { api } from '@/api/http'
import FormMessage from '@/components/forms/FormMessage.vue'
import PasswordInput from '@/components/forms/PasswordInput.vue'
import AppAuthLayout from '@/components/layout/AppAuthLayout.vue'
import {
  authFieldsClass,
  authFieldUi,
  authIconInputUi,
  authLinkRowClass,
  authMarkClass,
  authMarkIconClass,
  authSubmitClass,
  authSubtitleClass,
  authTitleClass,
} from '@/components/layout/authForm'
import { useAuthStore } from '@/stores/auth'
import { sanitizeInternalRedirect } from '@/utils/sanitizeRedirect'
import type { AuthUser } from '@/types/auth'

const { t } = useI18n()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const isSessionExpired = computed(() => route.query.reason === 'session-expired')

const email = ref('')
const password = ref('')
const isAlert = ref(false)
const isSubmitting = ref(false)
const passwordInput = useTemplateRef<{ inputRef: HTMLInputElement | null; hide: () => void }>(
  'passwordInput',
)

/**
 * One message for both fields: shown (and announced) once in the form message slot. Each field keeps
 * a visually hidden copy so `aria-invalid` fields are described by it.
 */
const credentialsError = computed(() =>
  isAlert.value ? t('views.auth.login.errors.invalidCredentials') : undefined,
)

async function login(event: SubmitEvent): Promise<void> {
  const form = event.currentTarget
  if (!(form instanceof HTMLFormElement)) return

  // Read the submitted DOM values: browser/password-manager autofill may not have synced v-model yet.
  const formData = new FormData(form)
  const body = new URLSearchParams({
    email: String(formData.get('email') ?? '').trim(),
    password: String(formData.get('password') ?? ''),
  })

  isAlert.value = false
  isSubmitting.value = true

  try {
    await primeCsrfToken()

    await api.post('/api/v1/user/login', {
      body,
    })

    const user = await api.get('/api/v1/user').json<AuthUser>()
    authStore.setUser(user)
    passwordInput.value?.hide()

    const redirectTarget = sanitizeInternalRedirect(route.query.redirect) ?? '/dashboard'
    await router.push(redirectTarget)
  } catch {
    isAlert.value = true
  } finally {
    isSubmitting.value = false
  }

  // The disabled (loading) submit button drops focus; return keyboard users to the form.
  if (isAlert.value && (!document.activeElement || document.activeElement === document.body)) {
    passwordInput.value?.inputRef?.focus()
  }
}
</script>

<template>
  <AppAuthLayout :motto="false">
    <div :class="authMarkClass" aria-hidden="true">
      <UIcon name="i-lucide-user" :class="authMarkIconClass" />
    </div>

    <h1 :class="authTitleClass">
      {{ t('views.auth.login.title') }}
    </h1>
    <p :class="authSubtitleClass">
      {{ t('components.authLayout.motto') }}
    </p>

    <form
      class="mt-[44px] sm:mt-(--pocketr-auth-form-gap)"
      :class="authFieldsClass"
      @submit.prevent="login"
    >
      <FormMessage v-if="credentialsError" tone="error" :message="credentialsError" />
      <FormMessage
        v-else-if="isSessionExpired"
        tone="warning"
        :message="t('views.auth.login.errors.sessionExpired')"
      />
      <UFormField
        :label="t('common.fields.email')"
        name="email"
        :error="credentialsError"
        :ui="{ ...authFieldUi, error: 'sr-only' }"
      >
        <UInput
          id="email"
          v-model="email"
          name="email"
          type="email"
          autocomplete="username"
          :placeholder="t('views.auth.login.emailPlaceholder')"
          required
          size="xl"
          icon="i-lucide-mail"
          class="w-full"
          :ui="authIconInputUi"
        />
      </UFormField>

      <UFormField
        :label="t('common.fields.password')"
        name="password"
        :error="credentialsError"
        :ui="{ ...authFieldUi, error: 'sr-only' }"
      >
        <PasswordInput
          id="password"
          ref="passwordInput"
          v-model="password"
          name="password"
          autocomplete="current-password"
          required
        />
      </UFormField>

      <UButton
        type="submit"
        size="xl"
        block
        :loading="isSubmitting"
        :label="isSubmitting ? t('common.feedback.loggingIn') : t('views.auth.login.submit')"
        :class="authSubmitClass"
      />
    </form>

    <p :class="authLinkRowClass">
      {{ t('views.auth.login.links.registerPrompt') }}
      <ULink
        to="/registration"
        class="font-medium text-primary hover:text-primary hover:underline underline-offset-2"
      >
        {{ t('views.auth.login.links.register') }}
      </ULink>
    </p>
  </AppAuthLayout>
</template>
