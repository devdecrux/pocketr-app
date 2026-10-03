import { computed, reactive, ref } from 'vue'
import { translate } from '@/i18n/translate'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import type { CreateTxnRequest } from '@/types/ledger'
import { todayIso } from '@/utils/dates'
import {
  debtPaymentStrategy,
  expenseStrategy,
  incomeStrategy,
  transferStrategy,
} from '@/utils/txnStrategies'

export const TXN_TABS = ['expense', 'income', 'transfer', 'debt-payment'] as const
export type TxnTab = (typeof TXN_TABS)[number]

export interface TxnStrategyResult {
  error: string | null
  request?: CreateTxnRequest
}

/**
 * Draft state of the create-transaction form: one field set per transaction type (kept while the
 * user switches tabs), the currency derived from the account the money leaves or arrives in, and the
 * validation and request building of the active type through the `txnStrategies`.
 */
export function useTransactionDraft() {
  const accountStore = useAccountStore()
  const currencyStore = useCurrencyStore()
  const modeStore = useModeStore()

  const activeTab = ref<TxnTab>('expense')

  const expense = reactive({
    date: todayIso(),
    payFrom: '',
    account: '',
    amount: 0,
    category: null as string | null,
    description: '',
  })
  const income = reactive({
    date: todayIso(),
    deposit: '',
    account: '',
    amount: 0,
    description: '',
  })
  const transfer = reactive({ date: todayIso(), from: '', to: '', amount: 0, description: '' })
  const debtPayment = reactive({
    date: todayIso(),
    payFrom: '',
    liabilityAccount: '',
    amount: 0,
    description: '',
  })

  // The currency follows the first account of the active type.
  const currencyAccountId = computed(() => {
    switch (activeTab.value) {
      case 'expense':
        return expense.payFrom
      case 'income':
        return income.deposit
      case 'transfer':
        return transfer.from
      default:
        return debtPayment.payFrom
    }
  })
  const currency = computed(
    () => accountStore.accountMap.get(currencyAccountId.value)?.currency ?? '',
  )
  const minorUnit = computed(() => currencyStore.getMinorUnit(currency.value))

  const isCrossUserTransfer = computed(() => {
    if (!modeStore.isHousehold) return false
    const from = accountStore.accountMap.get(transfer.from)
    const to = accountStore.accountMap.get(transfer.to)
    if (!from || !to) return false
    return from.ownerUserId !== to.ownerUserId
  })

  const strategyResult = computed<TxnStrategyResult>(() => {
    const ctx = { mode: modeStore.modeParam, householdId: modeStore.householdId }

    switch (activeTab.value) {
      case 'expense': {
        const fields = {
          date: expense.date,
          payFrom: expense.payFrom,
          account: expense.account,
          amount: expense.amount,
          currency: currency.value,
          description: expense.description,
          categoryTagId: expense.category,
        }
        const error = expenseStrategy.validate(fields)
        return error
          ? { error }
          : { error: null, request: expenseStrategy.buildRequest(ctx, fields) }
      }
      case 'income': {
        const fields = {
          date: income.date,
          deposit: income.deposit,
          account: income.account,
          amount: income.amount,
          currency: currency.value,
          description: income.description,
        }
        const error = incomeStrategy.validate(fields)
        return error
          ? { error }
          : { error: null, request: incomeStrategy.buildRequest(ctx, fields) }
      }
      case 'transfer': {
        const fields = {
          date: transfer.date,
          from: transfer.from,
          to: transfer.to,
          amount: transfer.amount,
          currency: currency.value,
          description: transfer.description,
        }
        const error = transferStrategy.validate(fields)
        return error
          ? { error }
          : { error: null, request: transferStrategy.buildRequest(ctx, fields) }
      }
      case 'debt-payment': {
        const fields = {
          date: debtPayment.date,
          payFrom: debtPayment.payFrom,
          liabilityAccount: debtPayment.liabilityAccount,
          amount: debtPayment.amount,
          currency: currency.value,
          description: debtPayment.description,
        }
        const error = debtPaymentStrategy.validate(fields)
        return error
          ? { error }
          : { error: null, request: debtPaymentStrategy.buildRequest(ctx, fields) }
      }
      default:
        return { error: translate('validation.transactions.unknownTab') }
    }
  })

  const isValid = computed(() => strategyResult.value.error === null)

  return {
    activeTab,
    expense,
    income,
    transfer,
    debtPayment,
    currency,
    minorUnit,
    isCrossUserTransfer,
    strategyResult,
    isValid,
  }
}
