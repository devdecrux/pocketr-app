<script setup lang="ts">
/**
 * Colour field of the category form: the preset swatches, a "no colour" swatch and a custom swatch
 * that opens a colour picker. All swatches are one radio group, so arrow keys move the selection.
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import {
  CATEGORY_COLOR_PRESETS,
  DEFAULT_CUSTOM_CATEGORY_COLOR,
  isPresetCategoryColor,
} from '@/utils/appearance/categoryColors'

const props = defineProps<{ id: string }>()

const color = defineModel<string | null>({ required: true })

const { t } = useI18n()

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
</script>

<template>
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
              :name="`${props.id}-color`"
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
                :name="isCustomSelected ? APP_ICONS.confirm : 'i-lucide-pipette'"
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
          :name="`${props.id}-color`"
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
            :name="APP_ICONS.confirm"
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
</template>
