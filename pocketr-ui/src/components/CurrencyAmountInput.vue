<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Input } from '@/components/ui/input'
import { formatMinorPlain, parseToMinor } from '@/utils/money'

const props = withDefaults(
  defineProps<{
    id?: string
    modelValue?: number
    minorUnit?: number
    currencyCode?: string
    allowNegative?: boolean
    placeholder?: string
  }>(),
  {
    modelValue: 0,
    minorUnit: 2,
    currencyCode: undefined,
    allowNegative: false,
    placeholder: '0.00',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number): void
}>()

const displayValue = ref(formatMinorPlain(props.modelValue, props.minorUnit))

watch(
  () => props.modelValue,
  (val) => {
    const current = parseToMinor(displayValue.value, props.minorUnit, props.allowNegative)
    if (current !== val) {
      displayValue.value = formatMinorPlain(val, props.minorUnit)
    }
  },
)

const suffix = computed(() => props.currencyCode ?? '')

function onInput(event: Event): void {
  const target = event.target as HTMLInputElement
  displayValue.value = target.value
  const minor = parseToMinor(target.value, props.minorUnit, props.allowNegative)
  if (!Number.isNaN(minor)) {
    emit('update:modelValue', minor)
  }
}

function onBlur(): void {
  const minor = parseToMinor(displayValue.value, props.minorUnit, props.allowNegative)
  if (!Number.isNaN(minor)) {
    displayValue.value = formatMinorPlain(minor, props.minorUnit)
    emit('update:modelValue', minor)
  }
}
</script>

<template>
  <div class="relative">
    <Input
      :id="id"
      type="text"
      inputmode="decimal"
      :value="displayValue"
      :placeholder="placeholder"
      :class="suffix ? 'pr-14' : ''"
      @input="onInput"
      @blur="onBlur"
    />
    <span
      v-if="suffix"
      class="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-xs text-muted-foreground"
    >
      {{ suffix }}
    </span>
  </div>
</template>
