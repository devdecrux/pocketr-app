<script setup lang="ts">
/** The description field every transaction type ends with; only the placeholder differs. */
import { useI18n } from 'vue-i18n'
import { APP_ICONS } from '@/utils/appearance/appIcons'

defineProps<{ id: string; placeholder: string }>()

const description = defineModel<string>({ required: true })

const { t } = useI18n()
</script>

<template>
  <UFormField :label="t('common.fields.description')" :for="id">
    <UInput
      :id="id"
      v-model="description"
      :icon="APP_ICONS.description"
      autocomplete="off"
      size="lg"
      class="w-full"
      :placeholder="placeholder"
    />
  </UFormField>
</template>
