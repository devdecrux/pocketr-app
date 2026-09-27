<script setup lang="ts">
/**
 * "Spending over time" line chart. Page-only code: DashboardPage loads it with
 * `defineAsyncComponent`, so ECharts never reaches the app shell. Colors come from the Pocketr
 * CSS tokens and follow the `.dark` class on <html>.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import { niceAxisStep } from '@/utils/dashboardPeriods'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent])

const props = defineProps<{
  labels: string[]
  /** Major-unit values, one per label. */
  values: number[]
  formatValue: (value: number) => string
  formatAxis: (value: number) => string
}>()

const colors = ref({ line: '', grid: '', muted: '', surface: '', text: '', border: '' })
let themeObserver: MutationObserver | null = null

/**
 * Canvas-normalised colour: Nuxt UI palette tokens (e.g. `--ui-error`) resolve to `oklch()`, which
 * ECharts cannot parse, so each token is painted once and read back as hex.
 */
function toHex(color: string): string {
  const context = document.createElement('canvas').getContext('2d')
  if (!context || !color) return color
  context.fillStyle = color
  context.fillRect(0, 0, 1, 1)
  const [r = 0, g = 0, b = 0] = context.getImageData(0, 0, 1, 1).data
  return `#${[r, g, b].map((value) => value.toString(16).padStart(2, '0')).join('')}`
}

function readColors(): void {
  const style = getComputedStyle(document.documentElement)
  const read = (name: string) => toHex(style.getPropertyValue(name).trim())
  colors.value = {
    // Spending is shown in the semantic error colour (red in light and dark).
    line: read('--ui-error'),
    grid: read('--pocketr-chart-grid'),
    muted: read('--ui-text-muted'),
    surface: read('--ui-bg'),
    text: read('--ui-text'),
    border: read('--ui-border'),
  }
}

const option = computed<EChartsOption>(() => {
  const step = niceAxisStep(Math.max(0, ...props.values))
  const { line, grid, muted, surface, text, border } = colors.value
  return {
    animationDuration: 300,
    grid: { left: 0, right: 12, top: 10, bottom: 0, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: surface,
      borderColor: border,
      textStyle: { color: text },
      valueFormatter: (value) => props.formatValue(Number(value)),
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.labels,
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: muted, margin: 14, fontSize: 12 },
      splitLine: { show: true, lineStyle: { color: grid } },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: step * 2,
      interval: step,
      axisLabel: {
        color: muted,
        fontSize: 12,
        formatter: (value: number) => props.formatAxis(value),
      },
      splitLine: { lineStyle: { color: grid } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 2, color: line },
        itemStyle: { color: line },
        emphasis: { focus: 'series' },
        data: props.values,
      },
    ],
  }
})

onMounted(() => {
  readColors()
  themeObserver = new MutationObserver(readColors)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  themeObserver = null
})
</script>

<template>
  <VChart v-if="colors.line" class="size-full" :option="option" :autoresize="{ throttle: 50 }" />
</template>
