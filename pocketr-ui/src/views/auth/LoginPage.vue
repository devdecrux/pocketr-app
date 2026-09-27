<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { primeCsrfToken } from '@/api/csrf'
import { api } from '@/api/http'
import AppAuthLayout from '@/components/layout/AppAuthLayout.vue'
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
const passwordInput = useTemplateRef<{ inputRef: HTMLInputElement | null }>('passwordInput')

/** One message for both fields: it marks both invalid and is shown (and announced) once. */
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
    <div
      class="mx-auto flex size-[103px] items-center justify-center rounded-full bg-(--pocketr-auth-mark-bg) sm:size-(--pocketr-auth-mark)"
      aria-hidden="true"
    >
      <UIcon
        name="i-lucide-user"
        class="size-[52px] text-primary sm:size-(--pocketr-auth-mark-icon)"
      />
    </div>

    <h1
      class="mt-1 text-center text-[30px] leading-10 font-bold text-highlighted sm:mt-(--pocketr-auth-title-gap) sm:text-(length:--pocketr-auth-title) sm:leading-(--pocketr-auth-title-leading)"
    >
      {{ t('views.auth.login.title') }}
    </h1>
    <p
      class="mt-2 text-center text-base text-muted sm:mt-(--pocketr-auth-subtitle-gap) sm:text-(length:--pocketr-auth-subtitle) sm:leading-[calc(1.75/1.125)]"
    >
      {{ t('components.authLayout.motto') }}
    </p>

    <UAlert
      v-if="isSessionExpired"
      color="neutral"
      variant="subtle"
      icon="i-lucide-triangle-alert"
      :description="t('views.auth.login.errors.sessionExpired')"
      class="mt-6"
      :ui="{ icon: 'size-4 text-error', description: 'text-sm text-default' }"
    />

    <form
      class="mt-[44px] flex flex-col gap-5 sm:mt-(--pocketr-auth-form-gap) sm:gap-(--pocketr-auth-field-gap)"
      @submit.prevent="login"
    >
      <UFormField
        :label="t('common.fields.email')"
        name="email"
        :error="credentialsError"
        :ui="{
          label: 'text-base text-highlighted sm:text-(length:--pocketr-auth-label)',
          container: 'mt-1 sm:mt-(--pocketr-auth-label-gap)',
          error: 'sr-only',
        }"
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
          :ui="{
            base: 'h-[50px] bg-(--pocketr-field-bg) ps-[58px] text-[17px] sm:h-(--pocketr-auth-control-h) sm:ps-(--pocketr-auth-control-text-inset) sm:text-(length:--pocketr-auth-control-text)',
            leading: 'ps-[17px] sm:ps-(--pocketr-auth-control-icon-inset)',
            leadingIcon: 'size-[22px] text-default sm:size-(--pocketr-auth-control-icon)',
          }"
        />
      </UFormField>

      <UFormField
        :label="t('common.fields.password')"
        name="password"
        :error="credentialsError ?? false"
        :ui="{
          label: 'text-base text-highlighted sm:text-(length:--pocketr-auth-label)',
          container: 'mt-1 sm:mt-(--pocketr-auth-label-gap)',
        }"
      >
        <UInput
          id="password"
          ref="passwordInput"
          v-model="password"
          name="password"
          type="password"
          autocomplete="current-password"
          required
          size="xl"
          icon="i-lucide-lock"
          class="w-full"
          :ui="{
            base: 'h-[50px] bg-(--pocketr-field-bg) ps-[58px] text-[17px] sm:h-(--pocketr-auth-control-h) sm:ps-(--pocketr-auth-control-text-inset) sm:text-(length:--pocketr-auth-control-text)',
            leading: 'ps-[17px] sm:ps-(--pocketr-auth-control-icon-inset)',
            leadingIcon: 'size-[22px] text-default sm:size-(--pocketr-auth-control-icon)',
          }"
        />
        <template #error="{ error }">
          <span role="alert">{{ error }}</span>
        </template>
      </UFormField>

      <UButton
        type="submit"
        size="xl"
        block
        :loading="isSubmitting"
        :label="isSubmitting ? t('common.feedback.loggingIn') : t('views.auth.login.submit')"
        class="mt-2.5 h-[52px] rounded-lg text-lg sm:mt-0.5 sm:h-(--pocketr-auth-button-h) sm:text-(length:--pocketr-auth-button-text)"
      />
    </form>

    <p
      class="mt-[22px] text-center text-[15px] text-muted sm:mt-(--pocketr-auth-link-gap) sm:text-(length:--pocketr-auth-link)"
    >
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
