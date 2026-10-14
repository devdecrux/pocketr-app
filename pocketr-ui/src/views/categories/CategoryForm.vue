<script setup lang="ts">
/**
 * Category create/edit form, shared by the desktop modal and the mobile drawer. It owns the name
 * rules (required, unique ignoring case) and emits only a valid payload; the submit button lives in
 * the overlay footer and targets this form through `id`.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import CategoryColorDot from '@/components/fields/CategoryColorDot.vue'
import CategoryColorPicker from '@/views/categories/CategoryColorPicker.vue'
import type { CategoryTag } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'

const props = defineProps<{
  id: string
  categories: readonly CategoryTag[]
  excludeId?: string
  serverError?: string | null
  preview?: boolean
}>()

const name = defineModel<string>('name', { required: true })
const color = defineModel<string | null>('color', { required: true })

const emit = defineEmits<{ submit: [payload: { name: string; color: string | null }] }>()

const { t } = useI18n()
const nameError = ref('')

watch(name, () => {
  nameError.value = ''
})

function hasDuplicateName(trimmedName: string): boolean {
  const normalized = trimmedName.toLowerCase()
  return props.categories.some(
    (category) =>
      category.id !== props.excludeId && category.name.trim().toLowerCase() === normalized,
  )
}

function onSubmit(): void {
  const trimmedName = name.value.trim()
  if (!trimmedName) {
    nameError.value = t('validation.category.nameRequired')
    return
  }
  if (hasDuplicateName(trimmedName)) {
    nameError.value = t('validation.category.duplicate', { name: trimmedName })
    return
  }
  nameError.value = ''
  emit('submit', { name: trimmedName, color: color.value })
}

const nameInputId = computed(() => `${props.id}-name`)
</script>

<template>
  <form :id="id" novalidate class="@container flex flex-col gap-4" @submit.prevent="onSubmit">
    <FormMessage v-if="serverError" tone="error" :message="serverError" />

    <UFormField
      :label="t('common.fields.name')"
      :name="nameInputId"
      :error="nameError || undefined"
    >
      <UInput
        :id="nameInputId"
        v-model="name"
        :icon="APP_ICONS.name"
        autocomplete="off"
        size="lg"
        class="w-full"
      />
    </UFormField>

    <CategoryColorPicker v-model="color" :id="id" />

    <div
      v-if="preview"
      class="flex min-w-0 items-center gap-3 rounded-lg border border-default bg-(--ui-bg-muted) px-4 py-3"
    >
      <span class="sr-only">{{ t('views.categories.form.preview') }}:</span>
      <CategoryColorDot :color="color" class="size-4" />
      <span class="truncate text-sm" :class="name.trim() ? 'text-highlighted' : 'text-muted'">
        {{ name.trim() || t('views.categories.form.previewPlaceholder') }}
      </span>
    </div>
  </form>
</template>
