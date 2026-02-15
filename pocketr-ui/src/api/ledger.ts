import { api } from '@/api/http'
import type { AccountBalance, CreateTxnRequest, LedgerTxn, TxnQuery } from '@/types/ledger'

const BASE = '/api/v1/ledger'

export function createTxn(req: CreateTxnRequest): Promise<LedgerTxn> {
  return api.post(`${BASE}/transactions`, { json: req }).json<LedgerTxn>()
}

export function listTxns(params: TxnQuery): Promise<LedgerTxn[]> {
  const searchParams: Record<string, string> = { mode: params.mode }
  if (params.householdId) searchParams.householdId = params.householdId
  if (params.dateFrom) searchParams.dateFrom = params.dateFrom
  if (params.dateTo) searchParams.dateTo = params.dateTo
  if (params.accountId) searchParams.accountId = params.accountId
  if (params.categoryId) searchParams.categoryId = params.categoryId
  return api.get(`${BASE}/transactions`, { searchParams }).json<LedgerTxn[]>()
}

export function getAccountBalance(accountId: string, asOf?: string): Promise<AccountBalance> {
  const searchParams: Record<string, string> = {}
  if (asOf) searchParams.asOf = asOf
  return api.get(`${BASE}/accounts/${accountId}/balance`, { searchParams }).json<AccountBalance>()
}
