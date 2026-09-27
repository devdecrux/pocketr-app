<script setup lang="ts">
/**
 * Auth-page password field with a show/hide toggle (Nuxt UI password-toggle pattern). Attributes
 * such as `name`, `autocomplete`, `required` and `aria-*` go to the input. The revealed state is
 * local only; callers hide it again with the exposed `hide()`.
 */
import { computed, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { authIconInputUi } from '@/components/layout/authForm'

defineOptions({ inheritAttrs: false })

const props = defineProps<{ id: string }>()
const model = defineModel<string>({ default: '' })

const { t } = useI18n()
const isVisible = ref(false)
const input = useTemplateRef<{ inputRef: HTMLInputElement | null }>('input')

const toggleLabel = computed(() =>
  isVisible.value ? t('components.passwordInput.hide') : t('components.passwordInput.show'),
)

defineExpose({
  inputRef: computed(() => input.value?.inputRef ?? null),
  hide: () => {
    isVisible.value = false
  },
})
</script>

<template>
  <UInput
    v-bind="$attrs"
    :id="props.id"
    ref="input"
    v-model="model"
    :type="isVisible ? 'text' : 'password'"
    size="xl"
    icon="i-lucide-lock"
    class="w-full"
    :ui="{ ...authIconInputUi, trailing: 'pe-1' }"
  >
    <template #trailing>
      <UButton
        type="button"
        color="neutral"
        variant="link"
        :icon="isVisible ? 'i-lucide-eye-off' : 'i-lucide-eye'"
        :aria-label="toggleLabel"
        :aria-pressed="isVisible"
        :aria-controls="props.id"
        :ui="{ leadingIcon: 'size-(--pocketr-auth-control-icon)' }"
        @click="isVisible = !isVisible"
      />
    </template>
  </UInput>
</template>

<style scoped>
/* Edge draws its own reveal button on password inputs; this field already has one. */
:deep(input::-ms-reveal) {
  display: none;
}
</style>
