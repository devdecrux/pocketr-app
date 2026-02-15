import { api } from '@/api/http'
import type { Currency } from '@/types/ledger'

const BASE = '/api/v1/currencies'

export function listCurrencies(): Promise<Currency[]> {
  return api.get(BASE).json<Currency[]>()
}
