import { api } from '@/api/http'
import type { MonthlyReportEntry, RolloverExpenseReport } from '@/types/ledger'

const BASE = '/api/v1/ledger/reports'

export function getMonthlyReport(params: {
  mode: 'INDIVIDUAL' | 'HOUSEHOLD'
  householdId?: string
  period: string
}): Promise<RolloverExpenseReport> {
  const searchParams: Record<string, string> = {
    mode: params.mode,
    period: params.period,
  }
  if (params.householdId) searchParams.householdId = params.householdId
  return api.get(`${BASE}/expenses`, { searchParams }).json<RolloverExpenseReport>()
}

export function getLifetimeExpenseReport(params: {
  mode: 'INDIVIDUAL' | 'HOUSEHOLD'
  householdId?: string
}): Promise<MonthlyReportEntry[]> {
  const searchParams: Record<string, string> = {
    mode: params.mode,
  }
  if (params.householdId) searchParams.householdId = params.householdId
  return api.get(`${BASE}/expenses/lifetime`, { searchParams }).json<MonthlyReportEntry[]>()
}

export function getLegacyMonthlyReport(params: {
  mode: 'INDIVIDUAL' | 'HOUSEHOLD'
  householdId?: string
  period: string
}): Promise<MonthlyReportEntry[]> {
  const searchParams: Record<string, string> = {
    mode: params.mode,
    period: params.period,
  }
  if (params.householdId) searchParams.householdId = params.householdId
  return api.get(`${BASE}/monthly`, { searchParams }).json<MonthlyReportEntry[]>()
}
