<script setup lang="ts">
import AppCreateButton from '@/components/page/AppCreateButton.vue'
import AppPageHeading from '@/components/page/AppPageHeading.vue'
import AppContent from '@/layout/AppContent.vue'
import DashboardBalanceCard from '@/views/dashboard/DashboardBalanceCard.vue'
import DashboardPeriodSelect from '@/views/dashboard/DashboardPeriodSelect.vue'
import DashboardRecentExpenses from '@/views/dashboard/DashboardRecentExpenses.vue'
import DashboardSpendingChartCard from '@/views/dashboard/DashboardSpendingChartCard.vue'
import DashboardSpentCard from '@/views/dashboard/DashboardSpentCard.vue'
import DashboardTopCategories from '@/views/dashboard/DashboardTopCategories.vue'
import { useDashboardData } from '@/views/dashboard/useDashboardData'
import { useModeStore } from '@/stores/mode'

const modeStore = useModeStore()
const {
  selectedPeriod,
  currentPeriod,
  periodItems,
  periodCount,
  visibleAssetAccounts,
  balances,
  balancesLoading,
  reports,
  reportLoading,
  currentEntries,
  recentExpenses,
  recentExpensesLoading,
  lifetimeReport,
  lifetimeReportLoading,
  selectPeriod,
  selectPeriodCount,
} = useDashboardData()
</script>

<template>
  <AppContent :title="$t('views.dashboard.title')">
    <template #context>
      <DashboardPeriodSelect
        :model-value="selectedPeriod"
        :items="periodItems"
        @update:model-value="selectPeriod"
      />
    </template>

    <AppPageHeading
      :title="$t('views.dashboard.title')"
      :desktop-title="$t('views.dashboard.overview.heading')"
      :subtitle="
        modeStore.isHousehold
          ? $t('views.dashboard.subtitle.household')
          : $t('views.dashboard.overview.heading')
      "
      :desktop-subtitle="
        modeStore.isHousehold
          ? $t('views.dashboard.subtitle.household')
          : $t('views.dashboard.overview.subtitle')
      "
    >
      <template #actions>
        <AppCreateButton
          to="/transactions"
          :label="$t('views.transactions.actions.new')"
          :accessible-label="$t('views.transactions.create.title')"
        />
      </template>
    </AppPageHeading>

    <!-- Desktop: the two summary cards stack in column 1; the chart spans columns 2-5 and both rows. -->
    <div class="grid gap-3 lg:grid-cols-5 lg:grid-rows-2 lg:gap-4">
      <DashboardBalanceCard
        class="lg:col-start-1 lg:row-start-1"
        :accounts="visibleAssetAccounts"
        :balances="balances"
        :loading="balancesLoading"
      />
      <DashboardSpentCard
        class="lg:col-start-1 lg:row-start-2"
        :entries="currentEntries"
        :has-reports="reports.length > 0"
        :loading="reportLoading"
        :period="selectedPeriod"
        :current-period="currentPeriod"
      />
      <DashboardSpendingChartCard
        class="mt-1 lg:col-span-4 lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:mt-0"
        :reports="reports"
        :loading="reportLoading"
        :period-count="periodCount"
        @update:period-count="selectPeriodCount"
      />
    </div>

    <!-- Desktop: same 5-column grid as the row above. Recent expenses sits under Available balance,
         Top categories under Spent this month; columns 3-5 stay empty for now. -->
    <div class="grid gap-4 lg:grid-cols-5">
      <DashboardRecentExpenses :expenses="recentExpenses" :loading="recentExpensesLoading" />
      <DashboardTopCategories
        :period-entries="currentEntries"
        :period-loading="reportLoading"
        :lifetime-entries="lifetimeReport"
        :lifetime-loading="lifetimeReportLoading"
      />
    </div>

    <template #footer>
      <AppCreateButton
        to="/transactions"
        placement="footer"
        :label="$t('views.transactions.actions.new')"
        :accessible-label="$t('views.transactions.create.title')"
      />
    </template>
  </AppContent>
</template>
