<script setup lang="ts">
import { HTTPError } from 'ky'
import { type ComponentPublicInstance, computed, nextTick, onMounted, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import FormMessage from '@/components/forms/FormMessage.vue'
import AppPagePanel from '@/components/layout/AppPagePanel.vue'
import AppConfirmDialog from '@/components/shared/AppConfirmDialog.vue'
import { AVATAR_ACCEPTED_TYPES, useAvatarUpload } from '@/composables/useAvatarUpload'
import { supportedLocaleLabels, supportedLocales } from '@/i18n'
import { translate } from '@/i18n/translate'
import { updateUserLanguage, updateUserRolloverDay } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import type { SupportedUserLanguage } from '@/types/auth'
import type { HouseholdSummary } from '@/types/household'
import { initialsFromName } from '@/utils/initials'

const PREFERENCES_FORM_ID = 'settings-preferences'
const PREFERENCES_TITLE_ID = 'settings-preferences-title'

const authStore = useAuthStore()
const householdStore = useHouseholdStore()
const modeStore = useModeStore()
const router = useRouter()
const { t } = useI18n()
const avatar = useAvatarUpload()
const isDesktop = useMediaQuery('(min-width: 1024px)')

type FocusTarget = ComponentPublicInstance | HTMLElement | null

function focus(target: FocusTarget): void {
  const element = target instanceof HTMLElement ? target : (target?.$el as HTMLElement | undefined)
  element?.focus()
}

function errorMessageFrom(error: unknown, fallbackKey: string): Promise<string> {
  if (!(error instanceof HTTPError)) return Promise.resolve(translate(fallbackKey))
  return error.response
    .json<{ message?: string }>()
    .catch(() => null)
    .then((payload) => payload?.message?.trim() || translate(fallbackKey))
}

const userInitials = computed(() =>
  initialsFromName(authStore.user?.firstName, authStore.user?.lastName),
)

/* Profile photo */

const fileInput = ref<HTMLInputElement | null>(null)
const changePhotoButton = ref<FocusTarget>(null)
const savePhotoButton = ref<FocusTarget>(null)

const avatarSrc = computed(() => avatar.previewUrl.value ?? authStore.user?.avatar ?? undefined)

function choosePhoto(): void {
  fileInput.value?.click()
}

async function onFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const accepted = avatar.select(input.files?.[0])
  // Reset so picking the same file again still fires `change`.
  input.value = ''
  if (accepted) {
    await nextTick()
    focus(savePhotoButton.value)
  }
}

async function discardPhoto(): Promise<void> {
  avatar.discard()
  await nextTick()
  focus(changePhotoButton.value)
}

async function savePhoto(): Promise<void> {
  if (await avatar.upload()) {
    await nextTick()
    focus(changePhotoButton.value)
  }
}

/* Preferences */

const selectedLanguage = ref<SupportedUserLanguage>(authStore.user?.language ?? 'en')
const selectedRolloverDay = ref<number | null>(authStore.user?.rolloverDay ?? 1)
const isSavingPreferences = ref(false)
const preferencesError = ref('')
const preferencesSuccess = ref('')
const rolloverFieldError = ref('')
const preferencesMessage = ref<FocusTarget>(null)

const languageItems = supportedLocales.map((locale) => ({
  label: supportedLocaleLabels[locale],
  value: locale,
}))

const hasLanguageChanged = computed(() => selectedLanguage.value !== authStore.user?.language)
const hasRolloverChanged = computed(() => selectedRolloverDay.value !== authStore.user?.rolloverDay)
const hasPreferencesChanged = computed(() => hasLanguageChanged.value || hasRolloverChanged.value)

async function savePreferences(): Promise<void> {
  if (!hasPreferencesChanged.value || isSavingPreferences.value) return

  preferencesError.value = ''
  preferencesSuccess.value = ''
  rolloverFieldError.value = ''

  const rolloverDay = selectedRolloverDay.value
  if (
    rolloverDay === null ||
    !Number.isInteger(rolloverDay) ||
    rolloverDay < 1 ||
    rolloverDay > 31
  ) {
    rolloverFieldError.value = translate('validation.rollover.dayRange')
    return
  }

  isSavingPreferences.value = true
  const errors: string[] = []

  // Each preference keeps its own endpoint; a failure in one does not undo the other.
  if (hasLanguageChanged.value) {
    try {
      const updatedUser = await updateUserLanguage(selectedLanguage.value)
      authStore.setUser(updatedUser)
      selectedLanguage.value = updatedUser.language
    } catch (error: unknown) {
      errors.push(await errorMessageFrom(error, 'errors.language.update'))
    }
  }

  if (hasRolloverChanged.value) {
    try {
      const updatedUser = await updateUserRolloverDay(rolloverDay)
      authStore.setUser(updatedUser)
      selectedRolloverDay.value = updatedUser.rolloverDay
    } catch (error: unknown) {
      errors.push(await errorMessageFrom(error, 'errors.rollover.update'))
    }
  }

  isSavingPreferences.value = false

  if (errors.length) {
    preferencesError.value = errors.join(' ')
  } else {
    preferencesSuccess.value = translate('views.settings.preferences.saved')
  }

  await nextTick()
  const message = preferencesMessage.value
  const element = message instanceof HTMLElement ? message : (message?.$el as HTMLElement)
  element?.scrollIntoView({ block: 'nearest' })
  if (errors.length) focus(message)
}

/* Household */

const householdsLoaded = ref(false)
const householdName = ref('')
const householdNameError = ref('')
const householdError = ref('')
const isCreatingHousehold = ref(false)
const leaveError = ref('')
const isLeavingHousehold = ref(false)
const inviteActionError = ref('')
const leaveTarget = ref<HouseholdSummary | null>(null)
// Kept after the dialog closes so its title does not change during the closing transition.
const leaveTargetName = ref('')
const householdHeading = ref<HTMLElement | null>(null)
const householdMessage = ref<FocusTarget>(null)

const activeHouseholds = computed(() => householdStore.activeHouseholds)
const pendingInvites = computed(() => householdStore.pendingInvites)

const isLeaveDialogOpen = computed({
  get: () => leaveTarget.value !== null,
  set: (open) => {
    if (!open && !isLeavingHousehold.value) leaveTarget.value = null
  },
})

onMounted(async () => {
  await householdStore.loadHouseholds()
  householdsLoaded.value = true
})

async function focusHouseholdMessage(): Promise<void> {
  await nextTick()
  focus(householdMessage.value)
}

async function createHousehold(): Promise<void> {
  householdNameError.value = ''
  householdError.value = ''

  const trimmed = householdName.value.trim()
  if (trimmed.length < 3) {
    householdNameError.value = translate('validation.household.nameMinLength')
    return
  }

  isCreatingHousehold.value = true

  try {
    const household = await householdStore.createHousehold({ name: trimmed })

    if (household) {
      householdName.value = ''
      modeStore.switchToHousehold(household.id)
      await router.push({ name: 'household-settings', params: { householdId: household.id } })
    } else {
      householdError.value = householdStore.error ?? translate('errors.households.create')
    }
  } catch (error: unknown) {
    householdError.value = await errorMessageFrom(error, 'errors.households.create')
  } finally {
    isCreatingHousehold.value = false
  }

  if (householdError.value) await focusHouseholdMessage()
}

async function handleAcceptInvite(householdId: string): Promise<void> {
  inviteActionError.value = ''

  if (activeHouseholds.value.length > 0) {
    inviteActionError.value = translate('views.settings.household.acceptInviteBlocked')
    return
  }

  const success = await householdStore.acceptInvite(householdId)
  if (success) {
    modeStore.switchToHousehold(householdId)
    await router.push({ name: 'household-settings', params: { householdId } })
  } else {
    inviteActionError.value = householdStore.error ?? translate('errors.households.acceptInvite')
    await focusHouseholdMessage()
  }
}

function requestLeave(household: HouseholdSummary): void {
  leaveError.value = ''
  leaveTargetName.value = household.name
  leaveTarget.value = household
}

async function confirmLeave(): Promise<void> {
  const household = leaveTarget.value
  if (!household) return

  isLeavingHousehold.value = true
  leaveError.value = ''

  try {
    const success = await householdStore.leaveHousehold(household.id)
    if (success) {
      modeStore.switchToIndividual()
    } else {
      leaveError.value = householdStore.error ?? translate('errors.households.leave')
    }
  } catch (error: unknown) {
    leaveError.value = await errorMessageFrom(error, 'errors.households.leave')
  } finally {
    isLeavingHousehold.value = false
    leaveTarget.value = null
  }

  // On failure the dialog returns focus to its Leave trigger and the alert announces the error.
  if (!leaveError.value) {
    await nextTick()
    householdHeading.value?.focus()
  }
}

const cardUi = { root: 'min-w-0 rounded-xl lg:h-full', body: 'p-4 sm:p-4 lg:p-3' }
// The desktop cards are narrow, so a label that cannot fit wraps instead of being cut off.
const buttonClass = 'max-w-full justify-center rounded-lg lg:h-auto lg:min-h-8'
const buttonUi = { label: 'lg:whitespace-normal lg:text-center' }
const compactButtonUi = { label: 'lg:max-2xl:sr-only' }
const cardTitleClass = 'text-base font-semibold text-highlighted'
const selectUi = {
  base: 'h-10 rounded-lg bg-(--pocketr-field-bg) text-sm ring-default',
  trailingIcon: 'size-4 text-default',
}
const fieldUi = { label: 'text-sm text-highlighted' }
</script>

<template>
  <AppPagePanel :title="t('views.settings.title')">
    <div class="min-w-0">
      <h1 class="text-[22px] leading-8 font-bold text-highlighted lg:hidden">
        {{ t('views.settings.title') }}
      </h1>
      <h2 class="text-lg leading-7 font-bold text-highlighted lg:text-2xl lg:leading-8">
        {{ t('views.settings.heading') }}
      </h2>
      <p class="text-sm text-muted lg:text-base">{{ t('views.settings.subtitle') }}</p>
    </div>

    <div class="grid gap-3 lg:grid-cols-5 lg:gap-4">
      <UCard :ui="cardUi">
        <h3 :class="cardTitleClass">{{ t('views.settings.profile.title') }}</h3>

        <div class="mt-3 flex flex-col items-center gap-2 text-center lg:mt-2">
          <UAvatar
            :src="avatarSrc"
            :text="userInitials"
            :alt="
              avatar.previewUrl.value
                ? t('views.settings.profile.previewAlt')
                : authStore.displayName
            "
            :ui="{
              root: [
                'size-16 shrink-0 bg-(--pocketr-avatar-bg) lg:size-14',
                avatar.previewUrl.value &&
                  'ring-2 ring-primary ring-offset-2 ring-offset-(--ui-bg)',
              ],
              fallback: 'text-2xl font-normal text-highlighted lg:text-xl',
            }"
          />

          <div class="grid w-full min-w-0 justify-items-center gap-0.5">
            <p
              class="max-w-full truncate text-base font-semibold text-highlighted lg:text-sm"
              :title="authStore.displayName"
            >
              {{ authStore.displayName }}
            </p>
            <p
              class="max-w-full truncate text-sm text-muted lg:text-xs"
              :title="authStore.user?.email"
            >
              {{ authStore.user?.email }}
            </p>

            <input
              ref="fileInput"
              type="file"
              class="hidden"
              tabindex="-1"
              aria-hidden="true"
              :accept="AVATAR_ACCEPTED_TYPES.join(',')"
              data-test="avatar-file-input"
              @change="onFileChange"
            />

            <div class="mt-2 flex max-w-full flex-wrap justify-center gap-2">
              <template v-if="avatar.pendingFile.value">
                <UTooltip :text="t('views.settings.profile.savePhoto')">
                  <UButton
                    ref="savePhotoButton"
                    icon="i-lucide-check"
                    :label="
                      avatar.isUploading.value
                        ? t('common.feedback.uploading')
                        : t('views.settings.profile.savePhoto')
                    "
                    :loading="avatar.isUploading.value"
                    :class="buttonClass"
                    :ui="compactButtonUi"
                    @click="savePhoto"
                  />
                </UTooltip>
                <UTooltip :text="t('common.actions.cancel')">
                  <UButton
                    icon="i-lucide-x"
                    color="neutral"
                    variant="outline"
                    :label="t('common.actions.cancel')"
                    :disabled="avatar.isUploading.value"
                    :class="buttonClass"
                    :ui="compactButtonUi"
                    @click="discardPhoto"
                  />
                </UTooltip>
              </template>
              <UButton
                v-else
                ref="changePhotoButton"
                icon="i-lucide-camera"
                color="neutral"
                variant="outline"
                :label="t('views.settings.profile.changePhoto')"
                :class="buttonClass"
                :ui="buttonUi"
                @click="choosePhoto"
              />
            </div>

            <p
              v-if="avatar.pendingFile.value"
              role="status"
              class="mt-1.5 max-w-full text-xs [overflow-wrap:anywhere] text-muted"
            >
              {{
                t('views.settings.profile.pendingPhoto', { name: avatar.pendingFile.value.name })
              }}
            </p>
            <p v-else class="mt-1.5 text-[11px] text-muted">
              {{ t('views.settings.profile.acceptedFormats') }}
            </p>
          </div>
        </div>

        <FormMessage
          v-if="avatar.error.value"
          tone="error"
          :message="avatar.error.value"
          class="mt-4"
        />
        <FormMessage
          v-else-if="avatar.success.value"
          tone="success"
          :message="avatar.success.value"
          class="mt-4"
        />
      </UCard>

      <UCard :ui="cardUi">
        <h3 :id="PREFERENCES_TITLE_ID" :class="cardTitleClass">
          {{ t('views.settings.preferences.title') }}
        </h3>

        <form
          :id="PREFERENCES_FORM_ID"
          class="mt-3 grid gap-3 lg:mt-2"
          novalidate
          @submit.prevent="savePreferences"
        >
          <div class="grid grid-cols-2 items-end gap-3 lg:grid-cols-1 lg:gap-2">
            <UFormField
              name="rolloverDay"
              :label="t('views.settings.preferences.rolloverDay')"
              :error="rolloverFieldError || undefined"
              :ui="{ ...fieldUi, labelWrapper: 'justify-start', hint: 'flex items-center' }"
              class="min-w-0"
            >
              <template #hint>
                <UTooltip
                  v-if="isDesktop"
                  :text="t('views.settings.preferences.rolloverHelp')"
                  :content="{ side: 'top' }"
                  :ui="{ content: 'h-auto max-w-60 py-1.5', text: 'whitespace-normal' }"
                >
                  <UButton
                    icon="i-lucide-info"
                    color="neutral"
                    variant="ghost"
                    size="xs"
                    :aria-label="t('views.settings.preferences.rolloverHelpLabel')"
                    class="-my-1"
                  />
                </UTooltip>
                <!-- Tooltips do not open on touch, so small screens get a tap-to-open popover. -->
                <UPopover v-else :content="{ side: 'top' }" arrow>
                  <UButton
                    icon="i-lucide-info"
                    color="neutral"
                    variant="ghost"
                    size="xs"
                    :aria-label="t('views.settings.preferences.rolloverHelpLabel')"
                    class="-my-1"
                  />
                  <template #content>
                    <p class="max-w-64 p-3 text-xs text-default">
                      {{ t('views.settings.preferences.rolloverHelp') }}
                    </p>
                  </template>
                </UPopover>
              </template>
              <UInputNumber
                v-model="selectedRolloverDay"
                :min="1"
                :max="31"
                :step="1"
                color="neutral"
                class="w-full"
                aria-describedby="settings-rollover-help"
                :ui="{ base: 'h-10 rounded-lg bg-(--pocketr-field-bg) text-sm' }"
              />
            </UFormField>

            <UFormField
              name="language"
              :label="t('views.settings.preferences.language')"
              :ui="fieldUi"
              class="min-w-0"
            >
              <USelect
                v-model="selectedLanguage"
                :items="languageItems"
                color="neutral"
                class="w-full"
                :ui="selectUi"
              />
            </UFormField>
          </div>

          <p id="settings-rollover-help" class="sr-only">
            {{ t('views.settings.preferences.rolloverHelp') }}
          </p>

          <FormMessage
            v-if="preferencesError"
            ref="preferencesMessage"
            tabindex="-1"
            tone="error"
            :message="preferencesError"
          />
          <FormMessage
            v-else-if="preferencesSuccess"
            ref="preferencesMessage"
            tone="success"
            :message="preferencesSuccess"
          />

          <UButton
            type="submit"
            :label="isSavingPreferences ? t('common.feedback.saving') : t('common.actions.save')"
            :aria-describedby="PREFERENCES_TITLE_ID"
            :loading="isSavingPreferences"
            :disabled="!hasPreferencesChanged"
            :class="['justify-self-center', buttonClass]"
            :ui="buttonUi"
          />
        </form>
      </UCard>

      <UCard :ui="cardUi">
        <h3
          ref="householdHeading"
          tabindex="-1"
          :class="[cardTitleClass, 'text-center outline-none']"
        >
          {{ t('views.settings.household.title') }}
        </h3>

        <div
          v-if="!householdsLoaded"
          class="mt-3 flex flex-col items-center gap-2 lg:mt-2"
          aria-hidden="true"
        >
          <USkeleton class="size-14 rounded-full lg:size-10" />
          <USkeleton class="h-4 w-2/3" />
          <USkeleton class="h-3 w-1/3" />
        </div>

        <template v-else>
          <ul v-if="activeHouseholds.length" class="mt-3 grid gap-5 lg:mt-2 lg:gap-3">
            <li
              v-for="household in activeHouseholds"
              :key="household.id"
              class="flex min-w-0 flex-col items-center gap-2 text-center"
            >
              <span
                class="flex size-14 shrink-0 items-center justify-center rounded-full bg-(--pocketr-icon-bg) lg:size-10 dark:bg-(--pocketr-avatar-bg)"
                aria-hidden="true"
              >
                <UIcon name="i-lucide-users" class="size-6 text-default lg:size-5" />
              </span>
              <p
                class="max-w-full truncate text-base font-semibold text-highlighted lg:text-sm"
                :title="household.name"
              >
                {{ household.name }}
              </p>
              <UBadge
                color="primary"
                variant="soft"
                :label="t(`display.householdRoles.${household.role}`)"
                class="rounded-full px-2.5"
              />
              <div class="mt-1 flex max-w-full flex-wrap justify-center gap-2">
                <UTooltip :text="t('views.settings.household.manage')">
                  <UButton
                    :to="{ name: 'household-settings', params: { householdId: household.id } }"
                    icon="i-lucide-settings"
                    color="neutral"
                    variant="outline"
                    :label="t('views.settings.household.manage')"
                    :class="buttonClass"
                    :ui="compactButtonUi"
                  />
                </UTooltip>
                <UTooltip :text="t('views.settings.household.leave')">
                  <UButton
                    icon="i-lucide-log-out"
                    color="error"
                    variant="solid"
                    :label="t('views.settings.household.leave')"
                    :disabled="isLeavingHousehold"
                    :class="buttonClass"
                    :ui="compactButtonUi"
                    @click="requestLeave(household)"
                  />
                </UTooltip>
              </div>
            </li>
          </ul>

          <div v-if="pendingInvites.length" class="mt-3 grid gap-2 text-center lg:mt-2">
            <h4 class="text-sm font-medium text-highlighted">
              {{ t('views.settings.household.pendingInvitations') }}
            </h4>
            <ul class="grid gap-2">
              <li
                v-for="invite in pendingInvites"
                :key="invite.id"
                class="flex min-w-0 flex-col items-center gap-2 rounded-lg border border-default p-2.5"
              >
                <span
                  class="max-w-full truncate text-sm font-medium text-highlighted"
                  :title="invite.name"
                >
                  {{ invite.name }}
                </span>
                <UBadge
                  color="warning"
                  variant="soft"
                  :label="t('views.settings.household.invitedBadge')"
                  class="rounded-full px-2.5 text-(color:--pocketr-warning-fg)"
                />
                <UButton
                  :label="t('common.actions.accept')"
                  :disabled="activeHouseholds.length > 0"
                  :class="buttonClass"
                  :ui="buttonUi"
                  @click="handleAcceptInvite(invite.id)"
                />
              </li>
            </ul>
          </div>

          <form
            v-if="!householdStore.hasHousehold"
            class="mt-3 grid gap-3 text-center lg:mt-2"
            novalidate
            @submit.prevent="createHousehold"
          >
            <p class="text-sm text-muted lg:text-xs">
              {{ t('views.settings.household.createDescription') }}
            </p>
            <UFormField
              name="householdName"
              :label="t('common.fields.householdName')"
              :error="householdNameError || undefined"
              :ui="fieldUi"
              class="text-start"
            >
              <UInput
                id="household-name"
                v-model="householdName"
                type="text"
                :placeholder="t('common.formHints.householdName')"
                class="w-full"
                :ui="{ base: 'h-10 rounded-lg bg-(--pocketr-field-bg) text-sm' }"
              />
            </UFormField>
            <UButton
              type="submit"
              :label="
                isCreatingHousehold
                  ? t('common.feedback.creating')
                  : t('views.settings.household.createAction')
              "
              :loading="isCreatingHousehold"
              :disabled="householdName.trim().length < 3"
              :class="['justify-self-center', buttonClass]"
              :ui="buttonUi"
            />
          </form>

          <FormMessage
            v-if="leaveError || inviteActionError || householdError"
            ref="householdMessage"
            tabindex="-1"
            tone="error"
            :message="leaveError || inviteActionError || householdError"
            class="mt-4"
          />
        </template>
      </UCard>
    </div>

    <AppConfirmDialog
      v-model:open="isLeaveDialogOpen"
      :title="t('views.settings.household.leaveTitle', { name: leaveTargetName })"
      :description="t('views.settings.household.leaveDescription')"
      :confirm-label="
        isLeavingHousehold ? t('common.feedback.leaving') : t('views.settings.household.leave')
      "
      :loading="isLeavingHousehold"
      @confirm="confirmLeave"
    />
  </AppPagePanel>
</template>
