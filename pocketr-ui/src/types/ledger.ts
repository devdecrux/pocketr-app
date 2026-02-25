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
  createdAt: string
}

export interface CreateAccountRequest {
  name: string
  type: AccountType
  currency: string
  openingBalanceMinor?: number
  openingBalanceDate?: string
}

export interface UpdateAccountRequest {
  name?: string
}

export interface CategoryTag {
  id: string
  name: string
  color?: string | null
  createdAt: string
}

export interface CreateCategoryRequest {
  name: string
  color?: string | null
}

export interface UpdateCategoryRequest {
  name: string
  color?: string | null
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

export interface TxnCreator {
  firstName: string | null
  lastName: string | null
  email: string
  avatar: string | null
}

export interface LedgerTxn {
  id: string
  householdId?: string | null
  txnDate: string
  description: string
  currency: string
  txnKind: string
  createdBy?: TxnCreator | null
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
  page?: number
  size?: number
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AccountBalance {
  accountId: string
  accountName: string
  accountType: AccountType
  currency: string
  balanceMinor: number
  asOf: string
}

export interface MonthlyReportEntry {
  expenseAccountId: string
  expenseAccountName: string
  categoryId: string | null
  categoryName: string | null
  netMinor: number
}

export type ViewMode = { kind: 'INDIVIDUAL' } | { kind: 'HOUSEHOLD'; householdId: string }
