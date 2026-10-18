import { vi } from 'vitest'
import { listAccounts } from '@/api/accounts'
import { listCategories } from '@/api/categories'
import { listCurrencies } from '@/api/currencies'
import { listHouseholds, listSharedAccounts } from '@/api/households'
import { getAccountBalances, listTxns } from '@/api/ledger'
import { getLifetimeExpenseReport, getMonthlyReport } from '@/api/reports'
import { CURRENCIES, txnPage } from '@/__tests__/support/fixtures'

/** Harmless empty defaults for the globally auto-mocked API modules; specs override per scenario. */
export function installApiDefaults(): void {
  vi.mocked(listAccounts).mockResolvedValue([])
  vi.mocked(getAccountBalances).mockResolvedValue([])
  vi.mocked(listCategories).mockResolvedValue([])
  vi.mocked(listCurrencies).mockResolvedValue(CURRENCIES)
  vi.mocked(listHouseholds).mockResolvedValue([])
  vi.mocked(listSharedAccounts).mockResolvedValue([])
  vi.mocked(listTxns).mockResolvedValue(txnPage([]))
  vi.mocked(getMonthlyReport).mockResolvedValue({
    periodStart: '2026-09-01',
    periodEnd: '2026-09-30',
    entries: [],
  })
  vi.mocked(getLifetimeExpenseReport).mockResolvedValue([])
}
