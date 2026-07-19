import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { Account } from '@/types/ledger'

const listAccounts = vi.fn()
const archiveAccount = vi.fn()

vi.mock('@/api/accounts', () => ({
  listAccounts,
  archiveAccount,
}))

vi.mock('@/stores/mode', () => ({
  useModeStore: () => ({ modeParam: 'INDIVIDUAL', householdId: null }),
}))

const { useAccountStore } = await import('@/stores/account')

function account(override: Partial<Account>): Account {
  return {
    id: 'account-1',
    ownerUserId: 1,
    name: 'Checking',
    type: 'ASSET',
    currency: 'EUR',
    createdAt: '2026-07-12T00:00:00Z',
    status: 'ACTIVE',
    archivedAt: null,
    ...override,
  }
}

describe('account store archiving', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listAccounts.mockReset()
    archiveAccount.mockReset()
  })

  it('defensively excludes archived and equity accounts from active accounts', () => {
    const store = useAccountStore()
    store.accounts = [
      account({ id: 'active' }),
      account({
        id: 'archived',
        status: 'ARCHIVED',
        archivedAt: '2026-07-12T08:00:00Z',
      }),
      account({ id: 'equity', type: 'EQUITY' }),
    ]

    expect(store.activeAccounts.map((item) => item.id)).toEqual(['active'])
  })

  it('removes an account from local state only after the archive request succeeds', async () => {
    const store = useAccountStore()
    store.accounts = [account({ id: 'archive-me' }), account({ id: 'keep-me' })]
    archiveAccount.mockResolvedValue(undefined)

    await store.archiveAccount('archive-me')

    expect(archiveAccount).toHaveBeenCalledWith('archive-me')
    expect(store.accounts.map((item) => item.id)).toEqual(['keep-me'])
  })

  it('keeps the account when the archive request fails', async () => {
    const store = useAccountStore()
    store.accounts = [account({ id: 'archive-me' })]
    archiveAccount.mockRejectedValue(new Error('request failed'))

    await expect(store.archiveAccount('archive-me')).rejects.toThrow('request failed')
    expect(store.accounts.map((item) => item.id)).toEqual(['archive-me'])
  })
})
