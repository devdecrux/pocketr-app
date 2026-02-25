import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockJson = vi.fn()
const mockGet = vi.fn(() => ({ json: mockJson }))
vi.mock('@/api/http', () => ({ api: { get: mockGet } }))

// import after mock is registered
const { getAccountBalance } = await import('@/api/ledger')

describe('getAccountBalance', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockJson.mockClear()
    mockJson.mockResolvedValue({ balanceMinor: 1000 })
  })

  it('sends no householdId in individual mode (param omitted)', async () => {
    await getAccountBalance('acc-1')
    expect(mockGet).toHaveBeenCalledWith('/api/v1/ledger/accounts/acc-1/balance', {
      searchParams: {},
    })
  })

  it('sends no householdId when explicitly undefined', async () => {
    await getAccountBalance('acc-1', undefined, undefined)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/ledger/accounts/acc-1/balance', {
      searchParams: {},
    })
  })

  it('sends householdId when provided (household mode)', async () => {
    await getAccountBalance('acc-1', undefined, 'hh-uuid-42')
    expect(mockGet).toHaveBeenCalledWith('/api/v1/ledger/accounts/acc-1/balance', {
      searchParams: { householdId: 'hh-uuid-42' },
    })
  })

  it('sends both asOf and householdId when provided', async () => {
    await getAccountBalance('acc-1', '2026-01-31', 'hh-uuid-42')
    expect(mockGet).toHaveBeenCalledWith('/api/v1/ledger/accounts/acc-1/balance', {
      searchParams: { asOf: '2026-01-31', householdId: 'hh-uuid-42' },
    })
  })
})
