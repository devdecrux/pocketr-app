<script setup lang="ts">
/**
 * Money field on `UInput`: the model is an integer in minor units (cents), the field shows the
 * decimal amount and the currency symbol in a leading cell. Typing commits every valid value;
 * blur normalises the text (`46.8` becomes `46.80`). Both `.` and `,` are accepted as separators.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { FIELD_BASE_CLASS } from '@/components/forms/fieldStyles'
import { formatMinorPlain, parseToMinor } from '@/utils/money'

const props = withDefaults(
  defineProps<{
    id?: string
    minorUnit?: number
    currencyCode?: string
    allowNegative?: boolean
    placeholder?: string
    ariaLabel?: string
    triggerClass?: string
  }>(),
  { minorUnit: 2, allowNegative: false },
)

const model = defineModel<number>({ default: 0 })

const { t } = useI18n()
const displayValue = ref(formatMinorPlain(model.value, props.minorUnit))

watch(model, (value) => {
  const current = parseToMinor(displayValue.value, props.minorUnit, props.allowNegative)
  if (current !== value) displayValue.value = formatMinorPlain(value, props.minorUnit)
})

// "€", "$", "лв." ...; the ISO code when the currency has no symbol of its own.
const symbol = computed(() => {
  if (!props.currencyCode) return null
  try {
    const parts = new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: props.currencyCode,
      currencyDisplay: 'narrowSymbol',
    }).formatToParts(0)
    return parts.find((part) => part.type === 'currency')?.value ?? props.currencyCode
  } catch {
    return props.currencyCode
  }
})

function onInput(value: string | number): void {
  displayValue.value = String(value)
  const minor = parseToMinor(displayValue.value, props.minorUnit, props.allowNegative)
  if (!Number.isNaN(minor)) model.value = minor
}

function onBlur(): void {
  const minor = parseToMinor(displayValue.value, props.minorUnit, props.allowNegative)
  if (Number.isNaN(minor)) return
  displayValue.value = formatMinorPlain(minor, props.minorUnit)
  model.value = minor
}
</script>

<template>
  <UInput
    :id="id"
    :model-value="displayValue"
    type="text"
    inputmode="decimal"
    autocomplete="off"
    :placeholder="placeholder ?? t('common.formHints.money')"
    :aria-label="ariaLabel"
    size="lg"
    class="w-full"
    :ui="{
      base: [FIELD_BASE_CLASS, 'ps-14 tabular-nums', triggerClass],
      leading: 'w-11 justify-center border-e border-default ps-0',
    }"
    @update:model-value="onInput"
    @blur="onBlur"
  >
    <template #leading>
      <span
        v-if="symbol"
        :data-testid="`${id ?? 'money'}-symbol`"
        class="text-base leading-none text-default"
      >
        {{ symbol }}
      </span>
      <UIcon v-else name="i-lucide-coins" class="size-5 text-default" />
    </template>
  </UInput>
</template>
