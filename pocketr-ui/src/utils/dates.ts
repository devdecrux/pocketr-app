import { CalendarDate, type DateValue, parseDate } from '@internationalized/date'

/** Local calendar date as `YYYY-MM-DD` (never UTC, so the default date is the user's today). */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** Parses an ISO `YYYY-MM-DD` string; anything else is treated as "no date". */
export function isoToCalendarDate(value?: string | null): CalendarDate | undefined {
  if (!value) return undefined
  try {
    return parseDate(value)
  } catch {
    return undefined
  }
}

export function calendarDateToIso(value: DateValue | null | undefined): string | undefined {
  return value ? new CalendarDate(value.year, value.month, value.day).toString() : undefined
}

/** Format every date picker and date column shows: day, month and year as digits. */
export const PICKER_DATE_FORMAT = 'DD/MM/YYYY'

/** "20/09/2026", the `PICKER_DATE_FORMAT` of a calendar date. */
export function formatPickerDate(value: DateValue): string {
  const day = String(value.day).padStart(2, '0')
  const month = String(value.month).padStart(2, '0')
  return `${day}/${month}/${value.year}`
}

/** "01/09/2026 – 30/09/2026", earlier date first. */
export function formatPickerRange(start: DateValue, end: DateValue): string {
  const [first, last] = start.compare(end) <= 0 ? [start, end] : [end, start]
  return `${formatPickerDate(first)} – ${formatPickerDate(last)}`
}

/** "20/09/2026" for an ISO `YYYY-MM-DD` string; the input unchanged when unparsable. */
export function formatIsoDate(value: string): string {
  const date = isoToCalendarDate(value)
  return date ? formatPickerDate(date) : value
}

function dateToCalendarDate(date: Date): CalendarDate {
  return new CalendarDate(date.getFullYear(), date.getMonth() + 1, date.getDate())
}

/** "20/09/2026" for a `Date`, read in the local calendar. */
export function formatDate(date: Date): string {
  return formatPickerDate(dateToCalendarDate(date))
}

/** "01/09/2026 – 30/09/2026" for two `Date`s, like the date-range filter shows its range. */
export function formatDateRange(start: Date, end: Date): string {
  return formatPickerRange(dateToCalendarDate(start), dateToCalendarDate(end))
}

/**
 * "12/09/2026" for an ISO timestamp such as `createdAt`, as the user's local calendar date; an empty
 * string for a missing or unparsable value.
 */
export function formatTimestampDate(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : formatDate(date)
}
