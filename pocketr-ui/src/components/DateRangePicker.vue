<script setup lang="ts">
import { computed } from 'vue'
import type { CalendarDate } from '@internationalized/date'
import { parseDate } from '@internationalized/date'
import type { DateRange } from 'reka-ui'
import {
  RangeCalendarCell,
  RangeCalendarCellTrigger,
  RangeCalendarGrid,
  RangeCalendarGridBody,
  RangeCalendarGridHead,
  RangeCalendarGridRow,
  RangeCalendarHeadCell,
  RangeCalendarHeader,
  RangeCalendarHeading,
  RangeCalendarNext,
  RangeCalendarPrev,
  RangeCalendarRoot,
} from 'reka-ui'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import { CalendarIcon, ChevronLeft, ChevronRight, X } from 'lucide-vue-next'

const props = defineProps<{
  from?: string // YYYY-MM-DD
  to?: string // YYYY-MM-DD
}>()

const emit = defineEmits<{
  'update:from': [value: string | undefined]
  'update:to': [value: string | undefined]
}>()

function toCalendarDate(s?: string): CalendarDate | undefined {
  if (!s) return undefined
  try {
    return parseDate(s) as CalendarDate
  } catch {
    return undefined
  }
}

function fmt(d: CalendarDate): string {
  return `${String(d.day).padStart(2, '0')}/${String(d.month).padStart(2, '0')}/${d.year}`
}

const range = computed<DateRange>({
  get: () => ({
    start: toCalendarDate(props.from),
    end: toCalendarDate(props.to),
  }),
  set(val: DateRange) {
    emit('update:from', val.start?.toString())
    emit('update:to', val.end?.toString())
  },
})

const label = computed<string | undefined>(() => {
  const s = toCalendarDate(props.from)
  const e = toCalendarDate(props.to)
  if (s && e) return `${fmt(s)} – ${fmt(e)}`
  if (s) return fmt(s)
  if (e) return `– ${fmt(e)}`
  return undefined
})

function clear() {
  emit('update:from', undefined)
  emit('update:to', undefined)
}
</script>

<template>
  <!-- Wrapper gives the X button something to position against -->
  <div class="relative inline-flex h-8">
    <Popover>
      <PopoverTrigger as-child>
        <Button
          variant="outline"
          :class="[
            'h-8 w-56 justify-start gap-2 px-3 text-xs font-normal',
            label ? 'pr-7' : '',
            !label ? 'text-muted-foreground' : '',
          ]"
        >
          <CalendarIcon class="size-3.5 shrink-0" />
          <span class="flex-1 truncate text-left">{{ label ?? 'Date range' }}</span>
        </Button>
      </PopoverTrigger>

      <PopoverContent class="w-auto p-0" align="start">
        <RangeCalendarRoot
          v-model="range"
          :week-starts-on="1"
          class="p-3"
          v-slot="{ grid, weekDays }"
        >
          <!-- Month navigation header -->
          <RangeCalendarHeader class="flex items-center justify-between pb-2">
            <RangeCalendarPrev as-child>
              <Button variant="outline" size="icon" class="size-7">
                <ChevronLeft class="size-4" />
              </Button>
            </RangeCalendarPrev>
            <RangeCalendarHeading class="text-sm font-semibold" />
            <RangeCalendarNext as-child>
              <Button variant="outline" size="icon" class="size-7">
                <ChevronRight class="size-4" />
              </Button>
            </RangeCalendarNext>
          </RangeCalendarHeader>

          <!-- Calendar grid (one per displayed month) -->
          <div v-for="month in grid" :key="month.value.toString()">
            <RangeCalendarGrid>
              <RangeCalendarGridHead>
                <RangeCalendarGridRow class="flex">
                  <RangeCalendarHeadCell
                    v-for="day in weekDays"
                    :key="day"
                    class="w-8 pb-1 text-center text-[10px] font-normal text-muted-foreground"
                  >
                    {{ day }}
                  </RangeCalendarHeadCell>
                </RangeCalendarGridRow>
              </RangeCalendarGridHead>

              <RangeCalendarGridBody>
                <RangeCalendarGridRow
                  v-for="(week, weekIdx) in month.rows"
                  :key="weekIdx"
                  class="mt-1 flex"
                >
                  <RangeCalendarCell
                    v-for="date in week"
                    :key="date.toString()"
                    :date="date"
                    class="relative p-0 text-center [&:has([data-highlighted])]:bg-accent [&:has([data-selection-start])]:rounded-l-md [&:has([data-selection-end])]:rounded-r-md"
                  >
                    <RangeCalendarCellTrigger
                      :day="date"
                      :month="month.value"
                      class="inline-flex size-8 cursor-pointer select-none items-center justify-center rounded-md text-sm font-normal transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring data-[today]:font-semibold data-[outside-view]:pointer-events-none data-[outside-view]:opacity-40 data-[disabled]:pointer-events-none data-[disabled]:opacity-30 data-[highlighted]:rounded-none data-[selected]:rounded-md data-[selected]:bg-primary data-[selected]:text-primary-foreground data-[selected]:hover:bg-primary"
                    />
                  </RangeCalendarCell>
                </RangeCalendarGridRow>
              </RangeCalendarGridBody>
            </RangeCalendarGrid>
          </div>
        </RangeCalendarRoot>
      </PopoverContent>
    </Popover>

    <!-- X sits outside the Popover — no event interaction with the trigger -->
    <button
      v-if="label"
      type="button"
      class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none"
      aria-label="Clear date range"
      @click="clear"
    >
      <X class="size-3.5" />
    </button>
  </div>
</template>
