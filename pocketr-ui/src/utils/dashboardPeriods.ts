/**
 * Dashboard reporting periods. A period is the `YYYY-MM` month a rollover period starts in, matching
 * the backend `RolloverPeriod.startingIn(period, rolloverDay)` contract of `/ledger/reports/expenses`.
 */

export const DEFAULT_REPORT_PERIOD_COUNT = 6
export const REPORT_PERIOD_COUNT_OPTIONS = [3, 6, 12] as const
export type ReportPeriodCount = (typeof REPORT_PERIOD_COUNT_OPTIONS)[number]

/** How many months back the period picker offers, including the current period. */
export const SELECTABLE_PERIOD_COUNT = 24

export function formatYearMonth(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function parsePeriod(period: string): { year: number; monthIndex: number } {
  const [year = new Date().getFullYear(), month = 1] = period.split('-').map(Number)
  return { year, monthIndex: month - 1 }
}

function normalizeRolloverDay(rolloverDay: number | null | undefined): number {
  if (!rolloverDay || !Number.isInteger(rolloverDay)) return 1
  return Math.min(Math.max(rolloverDay, 1), 31)
}

/** Local date of `rolloverDay` in the given month, clamped to the month length (backend `atClampedDay`). */
function clampedDay(year: number, monthIndex: number, rolloverDay: number): Date {
  const lastDay = new Date(year, monthIndex + 1, 0).getDate()
  return new Date(year, monthIndex, Math.min(rolloverDay, lastDay))
}

/** The period containing `date` (backend `RolloverPeriod.containing`). */
export function currentRolloverPeriod(date: Date, rolloverDay?: number | null): string {
  const day = normalizeRolloverDay(rolloverDay)
  const start = clampedDay(date.getFullYear(), date.getMonth(), day)
  const today = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const startMonth = today < start ? date.getMonth() - 1 : date.getMonth()
  return formatYearMonth(new Date(date.getFullYear(), startMonth, 1))
}

/** `count` consecutive periods ending with `endPeriod`, oldest first. */
export function buildReportPeriods(endPeriod: string, count: number): string[] {
  const { year, monthIndex } = parsePeriod(endPeriod)
  return Array.from({ length: count }, (_, index) =>
    formatYearMonth(new Date(year, monthIndex - (count - index - 1), 1)),
  )
}

/** Local start date (inclusive) and end date (inclusive) of a period. */
export function rolloverPeriodRange(
  period: string,
  rolloverDay?: number | null,
): { start: Date; end: Date } {
  const day = normalizeRolloverDay(rolloverDay)
  const { year, monthIndex } = parsePeriod(period)
  const start = clampedDay(year, monthIndex, day)
  const nextStart = clampedDay(year, monthIndex + 1, day)
  const end = new Date(nextStart.getFullYear(), nextStart.getMonth(), nextStart.getDate() - 1)
  return { start, end }
}

export function formatPeriodLabel(period: string, month: 'long' | 'short' = 'long'): string {
  const { year, monthIndex } = parsePeriod(period)
  return new Intl.DateTimeFormat(undefined, { month, year: 'numeric' }).format(
    new Date(year, monthIndex, 1),
  )
}

export function formatPeriodAxisLabel(period: string): string {
  const { year, monthIndex } = parsePeriod(period)
  return new Intl.DateTimeFormat(undefined, { month: 'short' }).format(
    new Date(year, monthIndex, 1),
  )
}

/** `DD-MM-YYYY`, the format the dashboard already used for rollover ranges. */
export function formatDayMonthYear(date: Date): string {
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${day}-${month}-${date.getFullYear()}`
}

export function formatIsoDate(value: string): string {
  const [year, month, day] = value.split('-')
  return `${day}-${month}-${year}`
}

/** Whole days between an ISO `YYYY-MM-DD` date and `today` (local), positive for past dates. */
export function daysAgo(isoDate: string, today: Date): number {
  const [year = 0, month = 1, day = 1] = isoDate.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  const base = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return Math.round((base.getTime() - date.getTime()) / 86_400_000)
}

/** A rounded axis step so two gridlines cover `maxValue` (e.g. 1460 -> 800 for 0 / 800 / 1,600). */
export function niceAxisStep(maxValue: number, splits = 2): number {
  if (maxValue <= 0) return 1
  const raw = maxValue / splits
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  return Math.ceil(raw / magnitude) * magnitude
}
