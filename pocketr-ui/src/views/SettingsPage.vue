<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import type { AuthUser } from '@/types/auth'
import { initialsFromName } from '@/utils/initials'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'

const authStore = useAuthStore()
const householdStore = useHouseholdStore()
const modeStore = useModeStore()
const router = useRouter()

const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const uploadError = ref('')
const uploadSuccess = ref('')

const householdName = ref('')
const isCreatingHousehold = ref(false)
const householdError = ref('')
const householdNameError = ref('')
const isLeavingHousehold = ref(false)
const leaveError = ref('')

const activeHouseholds = computed(() => householdStore.activeHouseholds)

const pendingInvites = computed(() => householdStore.pendingInvites)

const selectedFilename = computed(() => selectedFile.value?.name ?? 'No file selected')

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
    uploadError.value = 'Please choose an image before uploading.'
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
    uploadSuccess.value = 'Avatar updated.'
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

  return 'Failed to upload avatar. Please try again.'
}

async function createHousehold(): Promise<void> {
  householdNameError.value = ''
  householdError.value = ''

  const trimmed = householdName.value.trim()
  if (trimmed.length < 3) {
    householdNameError.value = 'Household name must be at least 3 characters.'
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
      householdError.value = householdStore.error ?? 'Failed to create household.'
    }
  } catch (error: unknown) {
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      householdError.value = payload?.message?.trim() || 'Failed to create household.'
    } else {
      householdError.value = 'Failed to create household.'
    }
  } finally {
    isCreatingHousehold.value = false
  }
}

async function handleAcceptInvite(householdId: string): Promise<void> {
  const success = await householdStore.acceptInvite(householdId)
  if (success) {
    modeStore.switchToHousehold(householdId)
    await router.push({ name: 'household-settings', params: { householdId } })
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
      leaveError.value = householdStore.error ?? 'Failed to leave household.'
    }
  } catch (error: unknown) {
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      leaveError.value = payload?.message?.trim() || 'Failed to leave household.'
    } else {
      leaveError.value = 'Failed to leave household.'
    }
  } finally {
    isLeavingHousehold.value = false
  }
}
</script>

<template>
  <section class="grid w-full gap-4 lg:max-w-2xl">
    <Card class="w-full">
      <CardHeader>
        <CardTitle class="text-2xl">Profile Settings</CardTitle>
        <CardDescription>Upload your avatar and personalize your profile.</CardDescription>
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
                <label for="avatar-upload">Browse</label>
              </Button>
              <Button
                size="sm"
                class="h-8 px-3 text-xs"
                :disabled="isUploading"
                @click="uploadAvatar"
              >
                {{ isUploading ? 'Uploading...' : 'Upload' }}
              </Button>
            </div>
          </div>

          <p class="text-xs text-muted-foreground">
            Accepted formats: JPEG, PNG, GIF, WEBP. Maximum size: 5MB.
          </p>

          <p v-if="uploadSuccess" class="text-sm text-emerald-600">{{ uploadSuccess }}</p>
          <p v-if="uploadError" class="text-sm text-red-600">{{ uploadError }}</p>
        </div>
      </CardContent>
    </Card>

    <Separator />

    <Card class="w-full">
      <CardHeader>
        <CardTitle class="text-2xl">Household Budgeting</CardTitle>
        <CardDescription>
          Create or manage a household to share accounts and track expenses together.
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-6">
        <!-- Active households -->
        <div v-if="activeHouseholds.length" class="space-y-3">
          <div
            v-for="household in activeHouseholds"
            :key="household.id"
            class="flex items-center justify-between rounded-md border border-border px-3 py-2"
          >
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-medium">{{ household.name }}</span>
              <span class="text-xs text-muted-foreground">
                {{ household.role }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <Button
                size="sm"
                variant="secondary"
                class="h-8 px-3 text-xs"
                @click="
                  router.push({ name: 'household-settings', params: { householdId: household.id } })
                "
              >
                Manage
              </Button>
              <Button
                size="sm"
                variant="destructive"
                class="h-8 px-3 text-xs"
                :disabled="isLeavingHousehold"
                @click="handleLeaveHousehold(household.id)"
              >
                {{ isLeavingHousehold ? 'Leaving...' : 'Leave' }}
              </Button>
            </div>
          </div>
          <p v-if="leaveError" class="text-sm text-red-600">{{ leaveError }}</p>
        </div>

        <!-- Pending invitations -->
        <div v-if="pendingInvites.length" class="space-y-3">
          <p class="text-sm font-medium">Pending Invitations</p>
          <div
            v-for="invite in pendingInvites"
            :key="invite.id"
            class="flex items-center justify-between rounded-md border border-border px-3 py-2"
          >
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-medium">{{ invite.name }}</span>
              <Badge variant="secondary">Invited</Badge>
            </div>
            <Button size="sm" class="h-8 px-3 text-xs" @click="handleAcceptInvite(invite.id)">
              Accept
            </Button>
          </div>
        </div>

        <!-- Create household (only when user is not in any household) -->
        <div v-if="!householdStore.hasHousehold">
          <div class="grid gap-3">
            <p class="text-sm text-muted-foreground">
              You are not part of any household yet. Create one to start sharing accounts with
              family or roommates.
            </p>
            <div class="grid gap-2">
              <Label for="household-name">Household name</Label>
              <Input
                id="household-name"
                v-model="householdName"
                type="text"
                placeholder="My Household"
                :class="{ 'border-red-500': householdNameError }"
              />
              <p v-if="householdNameError" class="text-xs text-red-600">
                {{ householdNameError }}
              </p>
            </div>
            <Button
              size="sm"
              class="w-fit"
              :disabled="isCreatingHousehold"
              @click="createHousehold"
            >
              {{ isCreatingHousehold ? 'Creating...' : 'Create Household' }}
            </Button>
            <p v-if="householdError" class="text-sm text-red-600">{{ householdError }}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  </section>
</template>
