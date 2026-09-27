import { describe, expect, it } from 'vitest'
import {
  buildReportPeriods,
  currentRolloverPeriod,
  daysAgo,
  formatDayMonthYear,
  niceAxisStep,
  rolloverPeriodRange,
} from '@/utils/dashboardPeriods'

describe('dashboard periods (backend RolloverPeriod contract)', () => {
  it('uses the calendar month for rollover day 1', () => {
    expect(currentRolloverPeriod(new Date(2026, 8, 27), 1)).toBe('2026-09')
    expect(currentRolloverPeriod(new Date(2026, 8, 1), undefined)).toBe('2026-09')
  })

  it('stays in the previous period before the rollover day', () => {
    expect(currentRolloverPeriod(new Date(2026, 8, 20), 25)).toBe('2026-08')
    expect(currentRolloverPeriod(new Date(2026, 8, 25), 25)).toBe('2026-09')
    expect(currentRolloverPeriod(new Date(2026, 0, 3), 15)).toBe('2025-12')
  })

  it('clamps the rollover day to the month length', () => {
    expect(currentRolloverPeriod(new Date(2026, 1, 28), 31)).toBe('2026-02')
    const { start, end } = rolloverPeriodRange('2026-02', 31)
    expect(formatDayMonthYear(start)).toBe('28-02-2026')
    expect(formatDayMonthYear(end)).toBe('30-03-2026')
  })

  it('builds consecutive periods ending with the selected one, oldest first', () => {
    expect(buildReportPeriods('2026-02', 3)).toEqual(['2025-12', '2026-01', '2026-02'])
    expect(buildReportPeriods('2026-09', 6)).toEqual([
      '2026-04',
      '2026-05',
      '2026-06',
      '2026-07',
      '2026-08',
      '2026-09',
    ])
  })

  it('describes calendar-month periods for rollover day 1', () => {
    const { start, end } = rolloverPeriodRange('2026-09', 1)
    expect(`${formatDayMonthYear(start)} - ${formatDayMonthYear(end)}`).toBe(
      '01-09-2026 - 30-09-2026',
    )
  })

  it('counts days back for relative expense dates', () => {
    const today = new Date(2026, 8, 27, 18, 30)
    expect(daysAgo('2026-09-27', today)).toBe(0)
    expect(daysAgo('2026-09-26', today)).toBe(1)
    expect(daysAgo('2026-08-31', today)).toBe(27)
  })

  it('rounds the chart axis to two readable steps', () => {
    expect(niceAxisStep(1460)).toBe(800)
    expect(niceAxisStep(95)).toBe(50)
    expect(niceAxisStep(0)).toBe(1)
  })
})
