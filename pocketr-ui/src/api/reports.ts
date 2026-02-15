import { api } from '@/api/http'
import type { MonthlyReportEntry } from '@/types/ledger'

const BASE = '/api/v1/ledger/reports'

export function getMonthlyReport(params: {
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
