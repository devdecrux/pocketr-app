<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { api } from '@/api/http'
import PasswordInput from '@/components/forms/PasswordInput.vue'
import AppAuthLayout from '@/components/layout/AppAuthLayout.vue'
import {
  authFieldsClass,
  authFieldUi,
  authIconInputUi,
  authInputUi,
  authLinkRowClass,
  authMarkClass,
  authMarkIconClass,
  authSubmitClass,
  authSubtitleClass,
  authTitleClass,
} from '@/components/layout/authForm'

type InputHandle = { inputRef: HTMLInputElement | null }
type PasswordHandle = InputHandle & { hide: () => void }

const { t } = useI18n()
const router = useRouter()

const firstName = ref('')
const lastName = ref('')
const password = ref('')
const confirmPassword = ref('')
const email = ref('')
const isPasswordMismatch = ref(false)
const isRegisterError = ref(false)
const isSubmitting = ref(false)
const emailInput = useTemplateRef<InputHandle>('emailInput')
const passwordInput = useTemplateRef<PasswordHandle>('passwordInput')
const confirmPasswordInput = useTemplateRef<PasswordHandle>('confirmPasswordInput')

const passwordMismatchError = computed(() =>
  isPasswordMismatch.value ? t('views.auth.registration.errors.passwordsMismatch') : false,
)

async function register(): Promise<void> {
  isPasswordMismatch.value = false
  isRegisterError.value = false

  if (password.value !== confirmPassword.value) {
    isPasswordMismatch.value = true
    confirmPasswordInput.value?.inputRef?.focus()
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

    passwordInput.value?.hide()
    confirmPasswordInput.value?.hide()
    await router.push('/login')
  } catch {
    isRegisterError.value = true
  } finally {
    isSubmitting.value = false
  }

  // The disabled (loading) submit button drops focus; return keyboard users to the form.
  if (
    isRegisterError.value &&
    (!document.activeElement || document.activeElement === document.body)
  ) {
    emailInput.value?.inputRef?.focus()
  }
}
</script>

<template>
  <AppAuthLayout :motto="false">
    <div :class="authMarkClass" aria-hidden="true">
      <UIcon name="i-lucide-user-plus" :class="authMarkIconClass" />
    </div>

    <h1 :class="authTitleClass">
      {{ t('views.auth.registration.title') }}
    </h1>
    <p :class="authSubtitleClass">
      {{ t('components.authLayout.motto') }}
    </p>

    <form
      class="mt-[11px] sm:mt-(--pocketr-auth-form-gap)"
      :class="authFieldsClass"
      @submit.prevent="register"
    >
      <div class="grid gap-(--pocketr-auth-field-gap) sm:grid-cols-2 sm:gap-x-4">
        <UFormField :label="t('common.fields.firstName')" name="firstName" :ui="authFieldUi">
          <UInput
            id="first-name"
            v-model="firstName"
            name="firstName"
            autocomplete="given-name"
            :placeholder="t('views.auth.registration.firstNamePlaceholder')"
            required
            size="xl"
            class="w-full"
            :ui="authInputUi"
          />
        </UFormField>
        <UFormField :label="t('common.fields.lastName')" name="lastName" :ui="authFieldUi">
          <UInput
            id="last-name"
            v-model="lastName"
            name="lastName"
            autocomplete="family-name"
            :placeholder="t('views.auth.registration.lastNamePlaceholder')"
            required
            size="xl"
            class="w-full"
            :ui="authInputUi"
          />
        </UFormField>
      </div>

      <UFormField :label="t('common.fields.email')" name="email" :ui="authFieldUi">
        <UInput
          id="email"
          ref="emailInput"
          v-model="email"
          name="email"
          type="email"
          autocomplete="username"
          :placeholder="t('views.auth.registration.emailPlaceholder')"
          required
          size="xl"
          icon="i-lucide-mail"
          class="w-full"
          :ui="authIconInputUi"
        />
      </UFormField>

      <UFormField :label="t('common.fields.password')" name="password" :ui="authFieldUi">
        <PasswordInput
          id="password"
          ref="passwordInput"
          v-model="password"
          name="password"
          autocomplete="new-password"
          required
        />
      </UFormField>

      <UFormField
        :label="t('common.fields.confirmPassword')"
        name="confirmPassword"
        :error="passwordMismatchError"
        :ui="authFieldUi"
      >
        <PasswordInput
          id="confirm-password"
          ref="confirmPasswordInput"
          v-model="confirmPassword"
          name="confirmPassword"
          autocomplete="new-password"
          required
        />
        <template #error="{ error }">
          <span role="alert">{{ error }}</span>
        </template>
      </UFormField>

      <UAlert
        v-if="isRegisterError"
        role="alert"
        color="error"
        variant="subtle"
        icon="i-lucide-circle-alert"
        :description="t('views.auth.registration.errors.unableToRegister')"
        :ui="{ icon: 'size-4', description: 'text-sm' }"
      />

      <UButton
        type="submit"
        size="xl"
        block
        :loading="isSubmitting"
        :label="
          isSubmitting ? t('common.feedback.creatingAccount') : t('views.auth.registration.submit')
        "
        :class="authSubmitClass"
      />
    </form>

    <p :class="authLinkRowClass">
      {{ t('views.auth.registration.links.loginPrompt') }}
      <ULink
        to="/login"
        class="font-medium text-primary hover:text-primary hover:underline underline-offset-2"
      >
        {{ t('common.actions.signIn') }}
      </ULink>
    </p>
  </AppAuthLayout>
</template>
