<script setup lang="ts">
/**
 * Single-date field: a trigger (calendar icon, date as DD/MM/YYYY) that opens a `UCalendar` in a
 * popover. The model is an ISO `YYYY-MM-DD` string; an unparsable value shows the placeholder, by
 * default the date format. Picking a day closes the popover.
 */
import { computed, ref } from 'vue'
import { FIELD_TRIGGER_CLASS } from '@/components/forms/fieldStyles'
import {
  calendarDateToIso,
  formatPickerDate,
  isoToCalendarDate,
  PICKER_DATE_FORMAT,
} from '@/utils/dates'

defineProps<{ id?: string; placeholder?: string; ariaLabel?: string; triggerClass?: string }>()

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
      :class="[FIELD_TRIGGER_CLASS, triggerClass]"
    >
      <span
        class="flex h-full w-11 shrink-0 items-center justify-center self-stretch border-e border-default"
      >
        <UIcon name="i-lucide-calendar" class="size-5 text-default" />
      </span>
      <span
        class="min-w-0 flex-1 truncate px-3 text-start tabular-nums"
        :class="label ? undefined : 'text-muted'"
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
