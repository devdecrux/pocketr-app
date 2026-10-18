<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import {
  CARD_BUTTON_GROUP_CLASS,
  CARD_SUBJECT_CLASS,
  CARD_TITLE_CLASS,
  NARROW_CARD_UI,
} from '@/styles/card'
import { AVATAR_ACCEPTED_TYPES, useAvatarUpload } from '@/views/settings/useAvatarUpload'
import {
  SETTINGS_BUTTON_CLASS,
  SETTINGS_BUTTON_UI,
  SETTINGS_COMPACT_BUTTON_UI,
} from '@/styles/settings'
import { useAuthStore } from '@/stores/auth'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { type FocusTarget, focusWhenRendered } from '@/utils/focus'
import { initialsFromName } from '@/utils/initials'

const authStore = useAuthStore()
const { t } = useI18n()
const avatar = useAvatarUpload()

const userInitials = computed(() =>
  initialsFromName(authStore.user?.firstName, authStore.user?.lastName),
)

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
  if (accepted) await focusWhenRendered(() => savePhotoButton.value)
}

async function discardPhoto(): Promise<void> {
  avatar.discard()
  await focusWhenRendered(() => changePhotoButton.value)
}

async function savePhoto(): Promise<void> {
  if (await avatar.upload()) await focusWhenRendered(() => changePhotoButton.value)
}
</script>

<template>
  <UCard :ui="NARROW_CARD_UI">
    <h3 :class="CARD_TITLE_CLASS">{{ t('views.settings.profile.title') }}</h3>

    <div class="mt-3 flex flex-col items-center gap-2 text-center lg:mt-2">
      <UAvatar
        :src="avatarSrc"
        :text="userInitials"
        :alt="
          avatar.previewUrl.value ? t('views.settings.profile.previewAlt') : authStore.displayName
        "
        :ui="{
          root: [
            'size-16 shrink-0 lg:size-14',
            avatar.previewUrl.value && 'ring-2 ring-primary ring-offset-2 ring-offset-(--ui-bg)',
          ],
          fallback: 'text-2xl lg:text-xl',
        }"
      />

      <div class="grid w-full min-w-0 justify-items-center gap-0.5">
        <p :class="CARD_SUBJECT_CLASS" :title="authStore.displayName">
          {{ authStore.displayName }}
        </p>
        <p class="max-w-full truncate text-sm text-muted lg:text-xs" :title="authStore.user?.email">
          {{ authStore.user?.email }}
        </p>

        <input
          id="avatar-file-input"
          ref="fileInput"
          type="file"
          :aria-label="t('views.settings.profile.changePhoto')"
          class="hidden"
          tabindex="-1"
          aria-hidden="true"
          :accept="AVATAR_ACCEPTED_TYPES.join(',')"
          data-test="avatar-file-input"
          @change="onFileChange"
        />

        <div :class="[CARD_BUTTON_GROUP_CLASS, 'mt-2']">
          <template v-if="avatar.pendingFile.value">
            <UTooltip :text="t('views.settings.profile.savePhoto')">
              <UButton
                size="xl"
                ref="savePhotoButton"
                :icon="APP_ICONS.confirm"
                :label="
                  avatar.isUploading.value
                    ? t('common.feedback.uploading')
                    : t('views.settings.profile.savePhoto')
                "
                :loading="avatar.isUploading.value"
                :class="SETTINGS_BUTTON_CLASS"
                :ui="SETTINGS_COMPACT_BUTTON_UI"
                @click="savePhoto"
              />
            </UTooltip>
            <UTooltip :text="t('common.actions.cancel')">
              <UButton
                size="xl"
                :icon="APP_ICONS.close"
                color="neutral"
                variant="outline"
                :label="t('common.actions.cancel')"
                :disabled="avatar.isUploading.value"
                :class="SETTINGS_BUTTON_CLASS"
                :ui="SETTINGS_COMPACT_BUTTON_UI"
                @click="discardPhoto"
              />
            </UTooltip>
          </template>
          <UButton
            size="xl"
            v-else
            ref="changePhotoButton"
            icon="i-lucide-camera"
            color="neutral"
            variant="outline"
            :label="t('views.settings.profile.changePhoto')"
            :class="SETTINGS_BUTTON_CLASS"
            :ui="SETTINGS_BUTTON_UI"
            @click="choosePhoto"
          />
        </div>

        <output
          v-if="avatar.pendingFile.value"
          class="mt-1.5 block max-w-full text-xs [overflow-wrap:anywhere] text-muted"
        >
          {{ t('views.settings.profile.pendingPhoto', { name: avatar.pendingFile.value.name }) }}
        </output>
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
</template>
