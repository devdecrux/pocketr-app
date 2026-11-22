import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '@/api/http'
import type { CreateTxnRequest } from '@/types/ledger'

// The real endpoint module over the globally mocked HTTP client.
vi.unmock('@/api/ledger')
const { getAccountBalances, updateTxn } = await import('@/api/ledger')

function lastSearchParams(): URLSearchParams {
  const [, options] = vi.mocked(api.get).mock.lastCall as unknown as [
    string,
    { searchParams: URLSearchParams },
  ]
  return options.searchParams
}

describe('getAccountBalances', () => {
  beforeEach(() => {
    const json = vi.fn().mockResolvedValue([{ accountId: 'acc-1', balanceMinor: 1000 }])
    vi.mocked(api.get).mockReturnValue({ json } as never)
  })

  it('does not call HTTP when the account list is empty', async () => {
    expect(await getAccountBalances([])).toEqual([])
    expect(api.get).not.toHaveBeenCalled()
  })

  it('sends repeatable accountIds and only the optional params that are provided', async () => {
    await getAccountBalances(['acc-1', 'acc-2'])

    expect(api.get).toHaveBeenCalledOnce()
    let params = lastSearchParams()
    expect(params.getAll('accountIds')).toEqual(['acc-1', 'acc-2'])
    expect(params.get('asOf')).toBeNull()
    expect(params.get('householdId')).toBeNull()

    await getAccountBalances(['acc-1'], '2026-01-31', 'hh-uuid-42')

    expect(vi.mocked(api.get).mock.lastCall?.[0]).toBe('/api/v1/ledger/accounts/balances')
    params = lastSearchParams()
    expect(params.getAll('accountIds')).toEqual(['acc-1'])
    expect(params.get('asOf')).toBe('2026-01-31')
    expect(params.get('householdId')).toBe('hh-uuid-42')
  })
})

describe('updateTxn', () => {
  it('puts the same body as create to the transaction url and returns the updated transaction', async () => {
    const updated = { id: 'txn-1', description: 'Rent' }
    const json = vi.fn().mockResolvedValue(updated)
    vi.mocked(api.patch).mockReturnValue({ json } as never)
    const request: CreateTxnRequest = {
      mode: 'INDIVIDUAL',
      householdId: null,
      txnDate: '2026-09-20',
      currency: 'EUR',
      description: 'Rent',
      splits: [
        { accountId: 'daily', side: 'CREDIT', amountMinor: 90000 },
        { accountId: 'rent', side: 'DEBIT', amountMinor: 90000, categoryTagId: null },
      ],
    }

    expect(await updateTxn('txn-1', request)).toBe(updated)

    expect(api.patch).toHaveBeenCalledExactlyOnceWith('/api/v1/ledger/transactions/txn-1', {
      json: request,
    })
  })
})
