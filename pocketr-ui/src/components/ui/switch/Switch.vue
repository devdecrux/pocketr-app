<script setup lang="ts">
import type { SwitchRootEmits, SwitchRootProps } from 'reka-ui'
import { SwitchRoot, SwitchThumb, useForwardPropsEmits } from 'reka-ui'
import { reactiveOmit } from '@vueuse/core'

const props = defineProps<SwitchRootProps>()
const emits = defineEmits<SwitchRootEmits>()

const delegatedProps = reactiveOmit(props)
const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <SwitchRoot
    v-bind="forwarded"
    class="inline-flex h-5 w-9 items-center rounded-full border border-slate-400 bg-slate-300 data-[state=checked]:border-[var(--app-transaction-amount-positive-fg)] data-[state=checked]:bg-[var(--app-transaction-amount-positive-fg)] dark:border-slate-500 dark:bg-slate-700 dark:data-[state=checked]:border-[var(--app-transaction-amount-positive-fg)] dark:data-[state=checked]:bg-[var(--app-transaction-amount-positive-fg)]"
  >
    <SwitchThumb
      class="block size-4 rounded-full bg-white shadow-md ring-1 ring-slate-500/40 transition-transform data-[state=checked]:translate-x-4 data-[state=unchecked]:translate-x-0.5 dark:ring-slate-950/60"
    />
  </SwitchRoot>
</template>
