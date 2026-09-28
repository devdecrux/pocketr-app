import { HTTPError } from 'ky'
import { onBeforeUnmount, ref } from 'vue'
import { api } from '@/api/http'
import { translate } from '@/i18n/translate'
import { useAuthStore } from '@/stores/auth'
import type { AuthUser } from '@/types/auth'

/** Mirrors the backend avatar rules (`UserAvatarService`), so a file is rejected before it is previewed. */
export const AVATAR_ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'] as const
export const AVATAR_MAX_BYTES = 5 * 1024 * 1024

/**
 * Pick, preview and upload a new avatar. The picked file is only previewed (object URL) until
 * `upload()` is called; `discard()` restores the current avatar. Every preview URL is revoked
 * when it is replaced, discarded, uploaded or the owner unmounts.
 */
export function useAvatarUpload() {
  const authStore = useAuthStore()

  const pendingFile = ref<File | null>(null)
  const previewUrl = ref<string | null>(null)
  const isUploading = ref(false)
  const error = ref('')
  const success = ref('')

  function revokePreview(): void {
    if (previewUrl.value) {
      URL.revokeObjectURL(previewUrl.value)
      previewUrl.value = null
    }
  }

  /** Validates and previews `file`. Returns whether it was accepted. */
  function select(file: File | null | undefined): boolean {
    error.value = ''
    success.value = ''
    if (!file) return false

    if (!(AVATAR_ACCEPTED_TYPES as readonly string[]).includes(file.type)) {
      error.value = translate('errors.avatar.unsupportedType')
      return false
    }
    if (file.size > AVATAR_MAX_BYTES) {
      error.value = translate('errors.avatar.tooLarge')
      return false
    }

    revokePreview()
    pendingFile.value = file
    previewUrl.value = URL.createObjectURL(file)
    return true
  }

  function discard(): void {
    revokePreview()
    pendingFile.value = null
    error.value = ''
  }

  /** Uploads the pending file. Returns whether the avatar was updated. */
  async function upload(): Promise<boolean> {
    if (!pendingFile.value) {
      error.value = translate('errors.avatar.chooseImage')
      return false
    }

    isUploading.value = true
    error.value = ''
    success.value = ''

    const formData = new FormData()
    formData.append('avatar', pendingFile.value)

    try {
      const updatedUser = await api.post('/api/v1/user/avatar', { body: formData }).json<AuthUser>()
      authStore.setUser(updatedUser)
      discard()
      success.value = translate('success.avatar.updated')
      return true
    } catch (nextError: unknown) {
      error.value = await resolveUploadError(nextError)
      return false
    } finally {
      isUploading.value = false
    }
  }

  onBeforeUnmount(revokePreview)

  return { pendingFile, previewUrl, isUploading, error, success, select, discard, upload }
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
