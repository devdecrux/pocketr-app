<script setup lang="ts">
const props = defineProps<{
  modelValue: string | null
  colors: readonly string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <button
      v-for="color in props.colors"
      :key="color"
      type="button"
      class="h-6 w-6 rounded-full border-2 transition-transform hover:scale-110"
      :style="{ backgroundColor: color }"
      :class="props.modelValue === color ? 'border-foreground scale-110' : 'border-transparent'"
      @click="emit('update:modelValue', color)"
    />
    <button
      type="button"
      class="flex h-6 w-6 items-center justify-center rounded-full border-2 border-dashed text-[10px] text-muted-foreground transition-colors hover:border-foreground"
      :class="props.modelValue === null ? 'border-foreground' : 'border-muted-foreground'"
      title="No color"
      @click="emit('update:modelValue', null)"
    >
      ✕
    </button>
  </div>
</template>
