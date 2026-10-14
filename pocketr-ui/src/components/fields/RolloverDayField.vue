<script setup lang="ts">
/**
 * The 1-31 rollover day field shared by Settings and the household settings: a number stepper, full
 * width on phones, with the help hint (tooltip next to a pointer, popover on touch screens) and a
 * screen-reader description. Each card passes its own wording and the layout slots of its field.
 */
import FieldHelpHint from '@/components/fields/FieldHelpHint.vue'

withDefaults(
  defineProps<{
    label: string
    helpText: string
    helpLabel: string
    /** Id of the screen-reader copy of the help text; the input points `aria-describedby` at it. */
    helpId: string
    error?: string
    disabled?: boolean
    /** `UFormField` slot classes of the card the field sits in. */
    fieldUi: Record<string, string>
    /** Width of the stepper from `lg`; phones always get the full width. */
    inputClass?: string
  }>(),
  { error: undefined, disabled: false, inputClass: undefined },
)

const rolloverDay = defineModel<number | null>({ required: true })
</script>

<template>
  <UFormField
    name="rolloverDay"
    :label="label"
    :error="error || undefined"
    :ui="fieldUi"
    class="min-w-0"
  >
    <template #hint>
      <FieldHelpHint :text="helpText" :label="helpLabel" />
    </template>
    <UInputNumber
      v-model="rolloverDay"
      :min="1"
      :max="31"
      :step="1"
      color="neutral"
      :class="['w-full', inputClass]"
      :aria-describedby="helpId"
      :disabled="disabled"
    />
    <p :id="helpId" class="sr-only">{{ helpText }}</p>
  </UFormField>
</template>
