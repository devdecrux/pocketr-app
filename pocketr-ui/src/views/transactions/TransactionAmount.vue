<script setup lang="ts">
/**
 * Signed amount of a transaction, like "−€46.80" or "+€3,500.00" with a true minus (as on the
 * dashboard); transfers carry no sign and show a transfer icon instead. Used by the desktop cell
 * and the phone row.
 */
import { computed } from 'vue'
import { formatTxnDisplayAmount } from '@/views/transactions/txnDisplay'
import { useCurrencyStore } from '@/stores/currency'
import type { LedgerTxn } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { getTxnAppearance } from '@/utils/appearance/txnAppearance'

const props = defineProps<{ txn: LedgerTxn }>()

const currencyStore = useCurrencyStore()

const appearance = computed(() => getTxnAppearance(props.txn.txnKind))
const isTransfer = computed(() => appearance.value.indicator === 'transfer')
const text = computed(() => {
  const amount = formatTxnDisplayAmount(props.txn, currencyStore.getMinorUnit)
  if (isTransfer.value) return amount
  return `${appearance.value.indicator === 'minus' ? '−' : '+'}${amount}`
})
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center gap-1.5 text-[15px] font-semibold whitespace-nowrap"
    :class="appearance.amountClass"
  >
    <UIcon
      v-if="isTransfer"
      :name="APP_ICONS.transfer"
      class="size-4 shrink-0"
      aria-hidden="true"
    />
    {{ text }}
  </span>
</template>
