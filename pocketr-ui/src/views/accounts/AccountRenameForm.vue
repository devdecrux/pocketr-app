<script setup lang="ts">
/**
 * Rename-account form, shared by the desktop modal and the mobile drawer. It emits the trimmed
 * name; the submit button lives in the overlay footer and targets this form through `id`.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import { APP_ICONS } from '@/utils/appearance/appIcons'

const props = defineProps<{ id: string; serverError?: string | null }>()

const name = defineModel<string>('name', { required: true })

const emit = defineEmits<{ submit: [name: string] }>()

const { t } = useI18n()

function onSubmit(): void {
  const trimmedName = name.value.trim()
  if (trimmedName) emit('submit', trimmedName)
}

const nameInputId = computed(() => `${props.id}-name`)
</script>

<template>
  <form :id="id" novalidate class="flex flex-col gap-4" @submit.prevent="onSubmit">
    <FormMessage v-if="serverError" tone="error" :message="serverError" />

    <UFormField :label="t('common.fields.name')" :for="nameInputId">
      <UInput
        :id="nameInputId"
        v-model="name"
        :icon="APP_ICONS.name"
        autocomplete="off"
        size="lg"
        class="w-full"
        :placeholder="t('common.formHints.accountName')"
      />
    </UFormField>
  </form>
</template>
