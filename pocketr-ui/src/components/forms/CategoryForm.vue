<script setup lang="ts">
/**
 * Category create/edit form, shared by the desktop modal and the mobile drawer. It owns the name
 * rules (required, unique ignoring case) and emits only a valid payload; the submit button lives in
 * the overlay footer and targets this form through `id`.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/forms/FormMessage.vue'
import CategoryColorDot from '@/components/shared/CategoryColorDot.vue'
import type { CategoryTag } from '@/types/ledger'
import { CATEGORY_COLOR_PRESETS, isPresetCategoryColor } from '@/utils/categoryColors'

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

// A colour saved outside today's palette stays selectable, so editing never drops it silently.
const currentCustomColor = color.value && !isPresetCategoryColor(color.value) ? color.value : null

const swatches = computed(() => [
  ...(currentCustomColor
    ? [{ value: currentCustomColor, label: t('components.categoryColorPicker.current') }]
    : []),
  ...CATEGORY_COLOR_PRESETS.map((preset) => ({
    value: preset.value as string | null,
    label: t(`components.categoryColorPicker.colors.${preset.key}`),
  })),
  { value: null, label: t('components.categoryColorPicker.noColor') },
])

function isSelected(value: string | null): boolean {
  return (color.value?.toLowerCase() ?? null) === (value?.toLowerCase() ?? null)
}

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
      :ui="{ label: 'text-sm text-highlighted' }"
    >
      <UInput
        :id="nameInputId"
        v-model="name"
        autocomplete="off"
        size="lg"
        class="w-full"
        :ui="{ base: 'h-10 rounded-lg bg-(--pocketr-field-bg) text-sm ring-default' }"
      />
    </UFormField>

    <fieldset class="min-w-0">
      <legend class="text-sm font-medium text-highlighted">{{ t('common.fields.color') }}</legend>
      <div class="mt-2.5 grid grid-cols-4 justify-items-center gap-y-3 @min-[18rem]:grid-cols-8">
        <label
          v-for="swatch in swatches"
          :key="swatch.value ?? 'none'"
          class="relative flex size-10 cursor-pointer items-center justify-center"
        >
          <input
            type="radio"
            :name="`${id}-color`"
            class="peer sr-only"
            :checked="isSelected(swatch.value)"
            :aria-label="swatch.label"
            @change="color = swatch.value"
          />
          <span
            class="flex size-8 items-center justify-center rounded-full ring-offset-2 ring-offset-(--ui-bg) peer-checked:ring-2 peer-checked:ring-primary peer-focus-visible:outline-2 peer-focus-visible:outline-offset-4 peer-focus-visible:outline-primary"
            :class="swatch.value ? undefined : 'border border-dashed border-(--ui-text-muted)'"
            :style="swatch.value ? { backgroundColor: swatch.value } : undefined"
          >
            <UIcon
              v-if="isSelected(swatch.value)"
              name="i-lucide-check"
              class="size-4"
              :class="
                swatch.value ? 'text-white drop-shadow-[0_1px_1px_rgb(0_0_0/0.6)]' : 'text-muted'
              "
            />
            <UIcon v-else-if="!swatch.value" name="i-lucide-slash" class="size-4 text-muted" />
          </span>
        </label>
      </div>
      <p aria-hidden="true" class="mt-0.5 text-end text-xs text-muted">
        {{ t('components.categoryColorPicker.noColor') }}
      </p>
    </fieldset>

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
