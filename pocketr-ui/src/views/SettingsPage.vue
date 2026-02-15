<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, ref } from 'vue'
import { api } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { AuthUser } from '@/types/auth'
import { initialsFromName } from '@/utils/initials'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const authStore = useAuthStore()

const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const uploadError = ref('')
const uploadSuccess = ref('')

const selectedFilename = computed(() => selectedFile.value?.name ?? 'No file selected')

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
    const payload = await error.response
      .json<{ message?: string }>()
      .catch(() => null)

    if (payload?.message?.trim()) {
      return payload.message
    }
  }

  return 'Failed to upload avatar. Please try again.'
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
              <Button size="sm" class="h-8 px-3 text-xs" :disabled="isUploading" @click="uploadAvatar">
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
  </section>
</template>
