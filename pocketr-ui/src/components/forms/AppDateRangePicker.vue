<script setup lang="ts">
/**
 * Date-range filter: a trigger (calendar icon, range as DD/MM/YYYY – DD/MM/YYYY) that opens a range
 * `UCalendar` in a popover, with a Clear action once a date is set. `from` and `to` are ISO
 * `YYYY-MM-DD` strings and update as soon as they change (a lone start date filters "from then
 * on"); the popover closes once both ends are chosen. Without a range the trigger shows the current
 * month as a muted example, unless `placeholder` is set.
 */
import {
  type DateValue,
  endOfMonth,
  getLocalTimeZone,
  startOfMonth,
  today,
} from '@internationalized/date'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { FIELD_TRIGGER_CLASS } from '@/components/forms/fieldStyles'
import {
  calendarDateToIso,
  formatPickerDate,
  formatPickerRange,
  isoToCalendarDate,
} from '@/utils/dates'
import { APP_ICONS } from '@/utils/appIcons'

const props = defineProps<{
  id?: string
  from?: string
  to?: string
  placeholder?: string
  triggerClass?: string
}>()

const emit = defineEmits<{
  'update:from': [value: string | undefined]
  'update:to': [value: string | undefined]
}>()

const { t } = useI18n()
const open = ref(false)

const start = computed(() => isoToCalendarDate(props.from))
const end = computed(() => isoToCalendarDate(props.to))

const label = computed(() => {
  if (start.value && end.value) return formatPickerRange(start.value, end.value)
  if (start.value) return t('common.dateRange.from', { date: formatPickerDate(start.value) })
  if (end.value) return t('common.dateRange.until', { date: formatPickerDate(end.value) })
  return null
})

// An example of what a picked range looks like, so the empty control still reads as a date picker.
const emptyText = computed(() => {
  if (props.placeholder) return props.placeholder
  const now = today(getLocalTimeZone())
  return formatPickerRange(startOfMonth(now), endOfMonth(now))
})

const range = computed(() => ({ start: start.value, end: end.value }))

function onPick(next: unknown): void {
  const picked = next as { start?: DateValue | null; end?: DateValue | null } | null
  const nextFrom = calendarDateToIso(picked?.start)
  const nextTo = calendarDateToIso(picked?.end)
  if (nextFrom !== props.from) emit('update:from', nextFrom)
  if (nextTo !== props.to) emit('update:to', nextTo)
  if (nextFrom && nextTo) open.value = false
}

function clear(): void {
  emit('update:from', undefined)
  emit('update:to', undefined)
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
      :aria-label="
        label ? `${t('common.fields.dateRange')}: ${label}` : t('common.fields.dateRange')
      "
      :class="[FIELD_TRIGGER_CLASS, triggerClass]"
    >
      <UIcon :name="APP_ICONS.date" class="ms-3 size-5 shrink-0 text-default" />
      <span
        class="min-w-0 flex-1 truncate px-3 text-start tabular-nums"
        :class="label ? undefined : 'text-muted'"
      >
        {{ label ?? emptyText }}
      </span>
    </UButton>

    <template #content>
      <div class="flex flex-col gap-1 p-2">
        <UCalendar range :model-value="range" :week-starts-on="1" @update:model-value="onPick" />
        <UButton
          v-if="label"
          type="button"
          color="neutral"
          variant="ghost"
          size="sm"
          class="self-end"
          :label="t('common.actions.clear')"
          @click="clear"
        />
      </div>
    </template>
  </UPopover>
</template>
