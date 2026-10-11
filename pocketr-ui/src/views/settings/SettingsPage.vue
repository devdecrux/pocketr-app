<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AppPageHeading from '@/components/page/AppPageHeading.vue'
import AppContent from '@/layout/AppContent.vue'
import SettingsHouseholdCard from '@/views/settings/SettingsHouseholdCard.vue'
import SettingsPreferencesCard from '@/views/settings/SettingsPreferencesCard.vue'
import SettingsProfileCard from '@/views/settings/SettingsProfileCard.vue'

const { t } = useI18n()
</script>

<template>
  <AppContent :title="t('views.settings.title')">
    <AppPageHeading
      :title="t('views.settings.title')"
      :desktop-title="t('views.settings.heading')"
      :subtitle="t('views.settings.subtitle')"
    />

    <div class="grid gap-3 lg:grid-cols-5 lg:gap-4">
      <SettingsProfileCard />
      <SettingsPreferencesCard />
      <SettingsHouseholdCard />
    </div>
  </AppContent>
</template>
