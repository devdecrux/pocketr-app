import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockJson = vi.fn()
const mockGet = vi.fn(() => ({ json: mockJson }))
vi.mock('@/api/http', () => ({ api: { get: mockGet } }))

// import after mock is registered
const { getAccountBalance, getAccountBalances } = await import('@/api/ledger')

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

describe('getAccountBalances', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockJson.mockClear()
    mockJson.mockResolvedValue([{ accountId: 'acc-1', balanceMinor: 1000 }])
  })

  it('does not call HTTP when accountIds list is empty', async () => {
    const result = await getAccountBalances([])
    expect(result).toEqual([])
    expect(mockGet).not.toHaveBeenCalled()
  })

  it('sends repeatable accountIds query params', async () => {
    await getAccountBalances(['acc-1', 'acc-2'])

    expect(mockGet).toHaveBeenCalledTimes(1)
    const call = mockGet.mock.calls[0]
    expect(call).toBeDefined()
    const [, options] = call as unknown as [string, { searchParams: URLSearchParams }]
    expect(options.searchParams.getAll('accountIds')).toEqual(['acc-1', 'acc-2'])
    expect(options.searchParams.get('asOf')).toBeNull()
    expect(options.searchParams.get('householdId')).toBeNull()
  })

  it('sends accountIds with asOf and householdId when provided', async () => {
    await getAccountBalances(['acc-1'], '2026-01-31', 'hh-uuid-42')

    expect(mockGet).toHaveBeenCalledTimes(1)
    const call = mockGet.mock.calls[0]
    expect(call).toBeDefined()
    const [url, options] = call as unknown as [string, { searchParams: URLSearchParams }]
    expect(url).toBe('/api/v1/ledger/accounts/balances')
    expect(options.searchParams.getAll('accountIds')).toEqual(['acc-1'])
    expect(options.searchParams.get('asOf')).toBe('2026-01-31')
    expect(options.searchParams.get('householdId')).toBe('hh-uuid-42')
  })
})
