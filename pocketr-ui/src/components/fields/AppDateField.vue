<script setup lang="ts">
/**
 * Single-date field: a trigger (calendar icon, date as DD/MM/YYYY) that opens a `UCalendar` in a
 * popover. The model is an ISO `YYYY-MM-DD` string; an unparsable value shows the placeholder, by
 * default the date format. Picking a day closes the popover. A disabled field shows the date but
 * cannot be opened; its content is dimmed rather than the whole button, because button-level opacity
 * washes out the 1px inset ring (most visibly its bottom edge) in dark mode.
 */
import { computed, ref } from 'vue'
import { FIELD_TRIGGER_CLASS, FIELD_TRIGGER_TEXT_CLASS } from '@/styles/field'
import {
  calendarDateToIso,
  formatPickerDate,
  isoToCalendarDate,
  PICKER_DATE_FORMAT,
} from '@/utils/dates'
import { APP_ICONS } from '@/utils/appearance/appIcons'

defineProps<{
  id?: string
  placeholder?: string
  ariaLabel?: string
  disabled?: boolean
}>()

const model = defineModel<string>({ required: true })

const open = ref(false)

const value = computed(() => isoToCalendarDate(model.value))
const label = computed(() => (value.value ? formatPickerDate(value.value) : null))

function onPick(next: unknown): void {
  const iso = calendarDateToIso(next as Parameters<typeof calendarDateToIso>[0])
  if (!iso) return
  model.value = iso
  open.value = false
}
</script>

<template>
  <UPopover v-model:open="open" :content="{ align: 'start' }">
    <UButton
      :id="id"
      type="button"
      color="neutral"
      variant="outline"
      size="lg"
      :aria-label="ariaLabel"
      :disabled="disabled"
      :class="[FIELD_TRIGGER_CLASS, 'disabled:opacity-100']"
    >
      <span
        class="flex h-full w-11 shrink-0 items-center justify-center self-stretch border-e border-default"
        :class="disabled ? 'opacity-60' : undefined"
      >
        <UIcon :name="APP_ICONS.date" class="size-5 text-default" />
      </span>
      <span
        :class="[
          FIELD_TRIGGER_TEXT_CLASS,
          label ? undefined : 'text-muted',
          disabled ? 'opacity-60' : undefined,
        ]"
      >
        {{ label ?? placeholder ?? PICKER_DATE_FORMAT }}
      </span>
    </UButton>

    <template #content>
      <UCalendar
        :model-value="value"
        :week-starts-on="1"
        class="p-2"
        @update:model-value="onPick"
      />
    </template>
  </UPopover>
</template>
