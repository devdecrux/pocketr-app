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
import {
  CATEGORY_COLOR_PRESETS,
  DEFAULT_CUSTOM_CATEGORY_COLOR,
  isPresetCategoryColor,
} from '@/utils/categoryColors'

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

const swatches = computed(() => [
  ...CATEGORY_COLOR_PRESETS.map((preset) => ({
    value: preset.value as string | null,
    label: t(`components.categoryColorPicker.colors.${preset.key}`),
  })),
  { value: null, label: t('components.categoryColorPicker.noColor') },
])

// The custom swatch remembers the last off-palette colour, starting from the saved one, so editing
// never drops it silently and switching to a preset and back keeps the picked colour.
const customColor = ref<string | null>(
  color.value && !isPresetCategoryColor(color.value) ? color.value.toLowerCase() : null,
)
const isCustomSelected = computed(() => Boolean(color.value) && !isPresetCategoryColor(color.value))

const pickerOpen = ref(false)
// The picker keeps its own raw value so normalising the saved colour never feeds back into it.
const pickerValue = ref<string>()
const customInput = ref<HTMLInputElement | null>(null)
let openOnClick = false
let closedByPointer = false

function selectCustom(): void {
  customColor.value ??= DEFAULT_CUSTOM_CATEGORY_COLOR
  color.value = customColor.value
}

function openPicker(): void {
  selectCustom()
  pickerValue.value = customColor.value ?? DEFAULT_CUSTOM_CATEGORY_COLOR
  pickerOpen.value = true
}

// A pointer press on the swatch toggles the picker; arrow keys only move the radio selection.
function onCustomPointerDown(): void {
  openOnClick = !pickerOpen.value
}

function onCustomClick(): void {
  if (openOnClick) openPicker()
  openOnClick = false
}

function onCustomKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  openPicker()
}

function onPick(value: string | undefined): void {
  if (!value) return
  pickerValue.value = value
  customColor.value = value.toLowerCase()
  color.value = customColor.value
}

const pickerContent = {
  // Opening upwards keeps the preview row below the swatches visible while picking.
  side: 'top',
  align: 'start',
  sideOffset: 8,
  collisionPadding: 8,
  'aria-label': t('components.categoryColorPicker.picker'),
  onPointerDownOutside: () => {
    closedByPointer = true
  },
  // Escape returns focus to the custom swatch; an outside press leaves focus where it landed.
  onCloseAutoFocus: (event: Event) => {
    event.preventDefault()
    if (!closedByPointer) customInput.value?.focus()
    closedByPointer = false
  },
} as const

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
      <div class="mt-2.5 grid grid-cols-5 justify-items-center gap-y-3 @min-[20rem]:grid-cols-9">
        <UPopover v-model:open="pickerOpen" :content="pickerContent">
          <template #anchor>
            <label
              class="relative flex size-10 cursor-pointer items-center justify-center"
              @pointerdown="onCustomPointerDown"
            >
              <input
                ref="customInput"
                type="radio"
                :name="`${id}-color`"
                class="peer sr-only"
                :checked="isCustomSelected"
                :aria-label="t('components.categoryColorPicker.custom')"
                @change="selectCustom"
                @click="onCustomClick"
                @keydown="onCustomKeydown"
              />
              <span
                data-testid="custom-color-swatch"
                class="flex size-8 items-center justify-center rounded-full ring-offset-2 ring-offset-(--ui-bg) peer-checked:ring-2 peer-checked:ring-primary peer-focus-visible:outline-2 peer-focus-visible:outline-offset-4 peer-focus-visible:outline-primary"
                :class="customColor ? undefined : 'border border-default bg-elevated'"
                :style="customColor ? { backgroundColor: customColor } : undefined"
              >
                <UIcon
                  :name="isCustomSelected ? 'i-lucide-check' : 'i-lucide-pipette'"
                  class="size-4"
                  :class="
                    customColor ? 'text-white drop-shadow-[0_1px_1px_rgb(0_0_0/0.6)]' : 'text-muted'
                  "
                />
              </span>
            </label>
          </template>
          <template #content>
            <UColorPicker
              :model-value="pickerValue"
              format="hex"
              class="p-2"
              @update:model-value="onPick"
            />
          </template>
        </UPopover>
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
        <p
          aria-hidden="true"
          class="col-start-4 -mt-2.5 justify-self-center text-xs whitespace-nowrap text-muted @min-[20rem]:col-start-9 @min-[20rem]:justify-self-end"
        >
          {{ t('components.categoryColorPicker.noColor') }}
        </p>
      </div>
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
