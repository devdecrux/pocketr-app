import { describe, expect, it } from 'vitest'
import { APP_ICONS } from '@/utils/appIcons'
import { getAccountAppearance } from '@/utils/accountAppearance'
import { getTxnAppearance } from '@/utils/txnAppearance'

describe('APP_ICONS', () => {
  it('gives every concept one icon, except the ones that deliberately share it', () => {
    const shared = new Set(['asset', 'accounts', 'liability', 'debtPayment'])
    const owners = new Map<string, string[]>()
    for (const [concept, icon] of Object.entries(APP_ICONS)) {
      owners.set(icon, [...(owners.get(icon) ?? []), concept])
    }
    const sharedIcons = [...owners.values()]
      .filter((concepts) => concepts.length > 1)
      .flat()
      .filter((concept) => !shared.has(concept))
    expect(sharedIcons).toEqual([])
  })

  it('points every collapsed row left and every open row down', () => {
    expect(APP_ICONS.collapsed).toBe('i-lucide-chevron-left')
    expect(APP_ICONS.expanded).toBe('i-lucide-chevron-down')
  })

  it('uses a name icon that is not the category icon', () => {
    expect(APP_ICONS.name).toBe('i-lucide-text-cursor-input')
    expect(APP_ICONS.name).not.toBe(APP_ICONS.categories)
  })

  it('uses the sidebar icon for the page-level concepts', () => {
    expect(APP_ICONS.accounts).toBe('i-lucide-wallet-minimal')
    expect(APP_ICONS.transactions).toBe('i-lucide-arrow-up-down')
    expect(APP_ICONS.categories).toBe('i-lucide-shapes')
  })

  it('shares the icon between transaction kinds and account types that mean the same thing', () => {
    expect(getAccountAppearance('EXPENSE').icon).toBe(getTxnAppearance('EXPENSE').icon)
    expect(getAccountAppearance('INCOME').icon).toBe(getTxnAppearance('INCOME').icon)
    expect(getAccountAppearance('ASSET').icon).toBe(getTxnAppearance('OPENING_BALANCE').icon)
    expect(getAccountAppearance('LIABILITY').icon).toBe(getTxnAppearance('OPENING_DEBT').icon)
    expect(getAccountAppearance('LIABILITY').icon).toBe(getTxnAppearance('DEBT_PAYMENT').icon)
    expect(getTxnAppearance('TRANSFER').icon).toBe(APP_ICONS.transfer)
  })
})
