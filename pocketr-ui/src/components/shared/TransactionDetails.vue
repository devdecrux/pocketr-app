<script setup lang="ts">
/**
 * Quiet detail panel of an expanded transaction, shared by the desktop table row and the mobile
 * list: where the money moved, the category and per-split amounts for multi-split or mixed-currency
 * transactions. With `creator` (mobile list, household mode) the member who added it sits on the right.
 */
import { useI18n } from 'vue-i18n'
import type { TxnDetails } from '@/utils/txnDetails'

export interface TxnDetailsCreator {
  name: string
  initials: string
  avatar?: string | null
}

defineProps<{ details: TxnDetails; date?: string; creator?: TxnDetailsCreator }>()

const { t } = useI18n()
</script>

<template>
  <div
    class="flex min-w-0 items-start gap-3 rounded-lg border border-default bg-(--pocketr-detail-bg) px-4 py-3 text-start text-sm whitespace-normal"
  >
    <div class="flex min-w-0 flex-1 flex-col gap-0.5">
      <p class="font-semibold text-highlighted">{{ t('views.transactions.details.title') }}</p>
      <p v-if="date" class="text-muted tabular-nums">{{ date }}</p>
      <p v-if="details.route.from || details.route.to" class="text-default">
        {{ details.route.from }}
        <span v-if="details.route.from && details.route.to" class="text-muted">
          → {{ details.route.to }}
        </span>
        <template v-else>{{ details.route.to }}</template>
      </p>
      <p v-if="details.categories.length" class="text-muted">
        {{ t('views.transactions.details.category', { name: details.categories.join(', ') }) }}
      </p>
      <ul v-if="details.splits.length" class="mt-1 flex flex-col gap-0.5 text-muted">
        <li v-for="split in details.splits" :key="split.key" class="flex justify-between gap-4">
          <span class="min-w-0 truncate">{{ split.label }}</span>
          <span class="shrink-0 tabular-nums">{{ split.amount }}</span>
        </li>
      </ul>
    </div>
    <div v-if="creator" class="flex w-20 shrink-0 flex-col items-center gap-1">
      <UAvatar
        :src="creator.avatar ?? undefined"
        :text="creator.initials"
        size="lg"
        aria-hidden="true"
        :ui="{
          root: 'size-9 bg-(--pocketr-avatar-bg)',
          fallback: 'text-sm font-normal text-highlighted',
        }"
      />
      <span class="line-clamp-2 w-full text-center text-xs break-words text-muted">
        {{ creator.name }}
      </span>
    </div>
  </div>
</template>
