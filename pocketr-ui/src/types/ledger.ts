export interface Currency {
  code: string
  minorUnit: number
  name: string
}

export type AccountType = 'ASSET' | 'LIABILITY' | 'INCOME' | 'EXPENSE' | 'EQUITY'

export interface Account {
  id: string
  ownerUserId: number
  name: string
  type: AccountType
  currency: string
  isArchived: boolean
  createdAt: string
}

export interface CreateAccountRequest {
  name: string
  type: AccountType
  currency: string
}

export interface UpdateAccountRequest {
  name?: string
  isArchived?: boolean
}

export interface CategoryTag {
  id: string
  name: string
  createdAt: string
}

export interface CreateCategoryRequest {
  name: string
}

export interface UpdateCategoryRequest {
  name: string
}

export type SplitSide = 'DEBIT' | 'CREDIT'

export interface LedgerSplit {
  id?: string
  accountId: string
  side: SplitSide
  amountMinor: number
  categoryTagId?: string | null
  memo?: string | null
}

export interface LedgerTxn {
  id: string
  createdByUserId: number
  householdId?: string | null
  txnDate: string
  description: string
  currency: string
  splits: LedgerSplit[]
  createdAt: string
  updatedAt: string
}

export interface CreateTxnRequest {
  mode: 'INDIVIDUAL' | 'HOUSEHOLD'
  householdId?: string | null
  txnDate: string
  currency: string
  description: string
  splits: Array<{
    accountId: string
    side: SplitSide
    amountMinor: number
    categoryTagId?: string | null
    memo?: string | null
  }>
}

export interface TxnQuery {
  mode: 'INDIVIDUAL' | 'HOUSEHOLD'
  householdId?: string
  dateFrom?: string
  dateTo?: string
  accountId?: string
  categoryId?: string
}

export interface AccountBalance {
  accountId: string
  balanceMinor: number
}

export interface MonthlyReportEntry {
  expenseAccountId: string
  expenseAccountName: string
  categoryId: string | null
  categoryName: string | null
  netMinor: number
}

export type ViewMode = { kind: 'INDIVIDUAL' } | { kind: 'HOUSEHOLD'; householdId: string }
