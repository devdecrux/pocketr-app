import { describe, expect, it, vi } from 'vitest'
import { archiveAccount, listAccounts } from '@/api/accounts'
import { useAccountStore } from '@/stores/account'
import { buildAccount } from '@/__tests__/support/fixtures'

const archived = buildAccount({
  id: 'archived',
  status: 'ARCHIVED',
  archivedAt: '2026-07-12T08:00:00Z',
})

describe('account store', () => {
  it('loads archived accounts for history while keeping posting choices active-only', async () => {
    vi.mocked(listAccounts).mockResolvedValue([
      buildAccount({ id: 'active' }),
      archived,
      buildAccount({ id: 'equity', type: 'EQUITY' }),
    ])
    const store = useAccountStore()

    await store.load()

    expect(listAccounts).toHaveBeenCalledWith({
      mode: 'INDIVIDUAL',
      householdId: undefined,
      includeArchived: true,
    })
    expect(store.accounts.map((item) => item.id)).toEqual(['active', 'archived', 'equity'])
    expect(store.activeAccounts.map((item) => item.id)).toEqual(['active'])
  })

  it('removes an account from local state only after the archive request succeeds', async () => {
    const store = useAccountStore()
    store.accounts = [buildAccount({ id: 'archive-me' }), buildAccount({ id: 'keep-me' })]

    await store.archiveAccount('archive-me')

    expect(archiveAccount).toHaveBeenCalledWith('archive-me')
    expect(store.accounts.map((item) => item.id)).toEqual(['keep-me'])
  })

  it('keeps the account when the archive request fails', async () => {
    vi.mocked(archiveAccount).mockRejectedValue(new Error('request failed'))
    const store = useAccountStore()
    store.accounts = [buildAccount({ id: 'archive-me' })]

    await expect(store.archiveAccount('archive-me')).rejects.toThrow('request failed')
    expect(store.accounts.map((item) => item.id)).toEqual(['archive-me'])
  })
})
