import { api } from '@/api/http'
import type { Account, CreateAccountRequest, UpdateAccountRequest } from '@/types/ledger'

const BASE = '/api/v1/accounts'

export function listAccounts(params: {
  mode: 'INDIVIDUAL' | 'HOUSEHOLD'
  householdId?: string
}): Promise<Account[]> {
  const searchParams: Record<string, string> = { mode: params.mode }
  if (params.householdId) {
    searchParams.householdId = params.householdId
  }
  return api.get(BASE, { searchParams }).json<Account[]>()
}

export function createAccount(req: CreateAccountRequest): Promise<Account> {
  return api.post(BASE, { json: req }).json<Account>()
}

export function updateAccount(id: string, req: UpdateAccountRequest): Promise<Account> {
  return api.patch(`${BASE}/${id}`, { json: req }).json<Account>()
}
