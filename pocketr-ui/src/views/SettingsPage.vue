<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import type { AuthUser, SupportedUserLanguage } from '@/types/auth'
import { initialsFromName } from '@/utils/initials'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { AppFormField, AppListItem, AppStatusText } from '@/components/app'
import { supportedLocaleLabels, supportedLocales } from '@/i18n'
import { updateUserLanguage } from '@/api/user'
import { translate } from '@/i18n/translate'

const authStore = useAuthStore()
const householdStore = useHouseholdStore()
const modeStore = useModeStore()
const router = useRouter()

const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const uploadError = ref('')
const uploadSuccess = ref('')
const selectedLanguage = ref<SupportedUserLanguage>(authStore.user?.language ?? 'en')
const isSavingLanguage = ref(false)
const languageError = ref('')
const languageSuccess = ref('')

const householdName = ref('')
const isCreatingHousehold = ref(false)
const householdError = ref('')
const householdNameError = ref('')
const isLeavingHousehold = ref(false)
const leaveError = ref('')
const inviteActionError = ref('')

const activeHouseholds = computed(() => householdStore.activeHouseholds)

const pendingInvites = computed(() => householdStore.pendingInvites)

const selectedFilename = computed(
  () => selectedFile.value?.name ?? translate('views.settings.profile.noFileSelected'),
)

const hasLanguageChanged = computed(() => selectedLanguage.value !== authStore.user?.language)

onMounted(async () => {
  await householdStore.loadHouseholds()
})

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
  uploadError.value = ''
  uploadSuccess.value = ''
}

function clearSelectedFile(): void {
  selectedFile.value = null

  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

async function uploadAvatar(): Promise<void> {
  if (!selectedFile.value) {
    uploadError.value = translate('errors.avatar.chooseImage')
    return
  }

  isUploading.value = true
  uploadError.value = ''
  uploadSuccess.value = ''

  const formData = new FormData()
  formData.append('avatar', selectedFile.value)

  try {
    const updatedUser = await api.post('/api/v1/user/avatar', { body: formData }).json<AuthUser>()
    authStore.setUser(updatedUser)
    uploadSuccess.value = translate('success.avatar.updated')
    clearSelectedFile()
  } catch (error: unknown) {
    uploadError.value = await resolveUploadError(error)
  } finally {
    isUploading.value = false
  }
}

async function resolveUploadError(error: unknown): Promise<string> {
  if (error instanceof HTTPError) {
    const payload = await error.response.json<{ message?: string }>().catch(() => null)

    if (payload?.message?.trim()) {
      return payload.message
    }
  }

  return translate('errors.avatar.upload')
}

async function saveLanguage(): Promise<void> {
  languageError.value = ''
  languageSuccess.value = ''
  isSavingLanguage.value = true

  try {
    const updatedUser = await updateUserLanguage(selectedLanguage.value)
    authStore.setUser(updatedUser)
    selectedLanguage.value = updatedUser.language
    languageSuccess.value = translate('views.settings.profile.languageUpdated')
  } catch (error: unknown) {
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      languageError.value = payload?.message?.trim() || translate('errors.language.update')
    } else {
      languageError.value = translate('errors.language.update')
    }
  } finally {
    isSavingLanguage.value = false
  }
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
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      householdError.value = payload?.message?.trim() || translate('errors.households.create')
    } else {
      householdError.value = translate('errors.households.create')
    }
  } finally {
    isCreatingHousehold.value = false
  }
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
  }
}

async function handleLeaveHousehold(householdId: string): Promise<void> {
  isLeavingHousehold.value = true
  leaveError.value = ''

  try {
    const success = await householdStore.leaveHousehold(householdId)
    if (success) {
      modeStore.switchToIndividual()
    } else {
      leaveError.value = householdStore.error ?? translate('errors.households.leave')
    }
  } catch (error: unknown) {
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      leaveError.value = payload?.message?.trim() || translate('errors.households.leave')
    } else {
      leaveError.value = translate('errors.households.leave')
    }
  } finally {
    isLeavingHousehold.value = false
  }
}
</script>

<template>
  <section class="grid w-full grid-cols-1 gap-4 md:grid-cols-4">
    <Card>
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('views.settings.profile.title') }}</CardTitle>
        <CardDescription>{{ $t('views.settings.profile.description') }}</CardDescription>
      </CardHeader>
      <CardContent class="space-y-6">
        <div class="flex items-center gap-5">
          <Avatar class="h-16 w-16 rounded-lg border border-border">
            <AvatarImage v-if="authStore.user?.avatar" :src="authStore.user.avatar" />
            <AvatarFallback class="rounded-lg border">
              {{ initialsFromName(authStore.user?.firstName, authStore.user?.lastName) }}
            </AvatarFallback>
          </Avatar>
          <div class="grid gap-1">
            <span class="text-sm font-semibold">{{ authStore.displayName }}</span>
            <span class="text-xs text-muted-foreground">{{ authStore.user?.email }}</span>
          </div>
        </div>

        <div class="grid gap-2">
          <input
            id="avatar-upload"
            ref="fileInput"
            class="sr-only"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            @change="onFileChange"
          />

          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <span
              class="truncate rounded-md border border-border bg-muted/50 px-2 py-1 text-xs text-muted-foreground"
            >
              {{ selectedFilename }}
            </span>
            <div class="flex items-center gap-2">
              <Button as-child size="sm" variant="secondary" class="h-8 px-3 text-xs">
                <label for="avatar-upload">{{ $t('common.actions.browse') }}</label>
              </Button>
              <Button
                size="sm"
                class="h-8 px-3 text-xs"
                :disabled="isUploading || !selectedFile"
                @click="uploadAvatar"
              >
                {{ isUploading ? $t('common.feedback.uploading') : $t('common.actions.upload') }}
              </Button>
            </div>
          </div>

          <p class="text-xs text-muted-foreground">
            {{ $t('views.settings.profile.acceptedFormats') }}
          </p>

          <AppStatusText v-if="uploadSuccess" variant="success">{{ uploadSuccess }}</AppStatusText>
          <AppStatusText v-if="uploadError">{{ uploadError }}</AppStatusText>
        </div>

        <div class="grid gap-3 border-t border-border pt-4">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-end">
            <AppFormField :label="$t('views.settings.profile.language')" class="sm:flex-1">
              <Select v-model="selectedLanguage">
                <SelectTrigger class="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="locale in supportedLocales" :key="locale" :value="locale">
                    {{ supportedLocaleLabels[locale] }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </AppFormField>
            <Button
              size="sm"
              class="w-fit sm:h-9"
              :disabled="isSavingLanguage || !hasLanguageChanged"
              @click="saveLanguage"
            >
              {{ isSavingLanguage ? $t('common.feedback.saving') : $t('common.actions.save') }}
            </Button>
          </div>
          <AppStatusText v-if="languageSuccess" variant="success">{{
            languageSuccess
          }}</AppStatusText>
          <AppStatusText v-if="languageError">{{ languageError }}</AppStatusText>
        </div>
      </CardContent>
    </Card>

    <Card>
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('views.settings.household.title') }}</CardTitle>
        <CardDescription>
          {{ $t('views.settings.household.description') }}
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-6">
        <!-- Active households -->
        <div v-if="activeHouseholds.length" class="space-y-3">
          <AppListItem v-for="household in activeHouseholds" :key="household.id">
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-medium">{{ household.name }}</span>
              <span class="text-xs text-muted-foreground">
                {{ $t(`display.householdRoles.${household.role}`) }}
              </span>
            </div>
            <template #actions>
              <div class="flex items-center gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  class="h-8 px-3 text-xs"
                  @click="
                    router.push({
                      name: 'household-settings',
                      params: { householdId: household.id },
                    })
                  "
                >
                  {{ $t('common.actions.manage') }}
                </Button>
                <Button
                  size="sm"
                  variant="destructive"
                  class="h-8 px-3 text-xs"
                  :disabled="isLeavingHousehold"
                  @click="handleLeaveHousehold(household.id)"
                >
                  {{
                    isLeavingHousehold ? $t('common.feedback.leaving') : $t('common.actions.leave')
                  }}
                </Button>
              </div>
            </template>
          </AppListItem>
          <AppStatusText v-if="leaveError">{{ leaveError }}</AppStatusText>
        </div>

        <!-- Pending invitations -->
        <div v-if="pendingInvites.length" class="space-y-3">
          <p class="text-sm font-medium">{{ $t('views.settings.household.pendingInvitations') }}</p>
          <AppListItem v-for="invite in pendingInvites" :key="invite.id">
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-medium">{{ invite.name }}</span>
              <Badge variant="secondary">{{ $t('views.settings.household.invitedBadge') }}</Badge>
            </div>
            <template #actions>
              <Button
                size="sm"
                class="h-8 px-3 text-xs"
                :disabled="activeHouseholds.length > 0"
                @click="handleAcceptInvite(invite.id)"
              >
                {{ $t('common.actions.accept') }}
              </Button>
            </template>
          </AppListItem>
          <AppStatusText v-if="inviteActionError">{{ inviteActionError }}</AppStatusText>
        </div>

        <!-- Create household (only when user is not in any household) -->
        <div v-if="!householdStore.hasHousehold">
          <div class="grid gap-3">
            <p class="text-sm text-muted-foreground">
              {{ $t('views.settings.household.createDescription') }}
            </p>
            <AppFormField :label="$t('common.fields.householdName')" control-id="household-name">
              <Input
                id="household-name"
                v-model="householdName"
                type="text"
                :placeholder="$t('common.formHints.householdName')"
                :class="{ 'border-[color:var(--app-field-invalid-border)]': householdNameError }"
              />
              <AppStatusText v-if="householdNameError" size="xs">
                {{ householdNameError }}
              </AppStatusText>
            </AppFormField>
            <Button
              size="sm"
              class="w-fit"
              :disabled="isCreatingHousehold || householdName.trim().length < 3"
              @click="createHousehold"
            >
              {{
                isCreatingHousehold
                  ? $t('common.feedback.creating')
                  : $t('views.settings.household.createAction')
              }}
            </Button>
            <AppStatusText v-if="householdError">{{ householdError }}</AppStatusText>
          </div>
        </div>
      </CardContent>
    </Card>
  </section>
</template>
