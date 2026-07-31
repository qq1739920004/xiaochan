<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import api from '../api'

const route = useRoute()
const router = useRouter()

const uniqueId = computed(() => route.query.uniqueId as string || '')
const storeName = computed(() => route.query.name as string || '门店')

const loading = ref(true)
const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

async function fetchData() {
  loading.value = true
  try {
    const response = await api.get(`/api/store-inventory-history/${uniqueId.value}`)
    if (response.data.success) {
      const data = response.data.data || []
      loading.value = false
      await nextTick()
      renderChart(data)
      return
    }
  } catch {
    // ignore
  }
  loading.value = false
}

function renderChart(data: any[]) {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  // 按 sku_id 分组，同时保留 sku_name 用于图例展示
  const groupMap = new Map<string, { id: string; name: string; items: any[] }>()
  for (const item of data) {
    const key = item.skuId || ''
    if (!groupMap.has(key)) {
      groupMap.set(key, { id: key, name: item.skuName || key, items: [] })
    }
    groupMap.get(key)!.items.push(item)
  }

  // 同名不同 sku_id 时图例会重名导致只显示一条，这里追加 sku_id 后缀去重
  const nameCount = new Map<string, number>()
  for (const g of groupMap.values()) {
    nameCount.set(g.name, (nameCount.get(g.name) || 0) + 1)
  }
  const usedNames = new Set<string>()
  for (const g of groupMap.values()) {
    if ((nameCount.get(g.name) || 0) > 1) {
      let n = 0
      let disambiguated = ''
      do {
        n++
        disambiguated = `${g.name}(#${n})`
      } while (usedNames.has(disambiguated))
      g.name = disambiguated
    }
    usedNames.add(g.name)
  }

  // 收集所有不重复的时间点并排序，作为统一横轴
  const timeSet = new Set<string>()
  for (const item of data) {
    if (item.createTime) {
      timeSet.add(item.createTime)
    }
  }
  const times = Array.from(timeSet).sort()

  const colors = ['#4f6ef7', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#f97316']

  const series: any[] = []
  let colorIndex = 0
  for (const [, group] of groupMap) {
    const inventoryMap = new Map<string, number>()
    for (const item of group.items) {
      inventoryMap.set(item.createTime, item.inventory)
    }
    const color = colors[colorIndex % colors.length]
    series.push({
      name: group.name,
      data: times.map(t => inventoryMap.get(t) ?? null),
      type: 'line',
      smooth: true,
      symbol: times.length > 20 ? 'none' : 'circle',
      symbolSize: 6,
      lineStyle: {
        color,
        width: 2,
      },
      itemStyle: {
        color,
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: color + '33' },
          { offset: 1, color: color + '05' },
        ]),
      },
    })
    colorIndex++
  }

  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
    },
    legend: {
      top: 0,
      data: [...groupMap.values()].map(g => g.name),
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: times,
      boundaryGap: false,
      axisLabel: {
        rotate: times.length > 10 ? 45 : 0,
        fontSize: 12,
        color: '#6b7280',
      },
      axisLine: {
        lineStyle: { color: '#e5e7eb' },
      },
    },
    yAxis: {
      type: 'value',
      name: '库存',
      minInterval: 1,
      axisLabel: {
        fontSize: 12,
        color: '#6b7280',
      },
      splitLine: {
        lineStyle: { color: '#f3f4f6' },
      },
    },
    series,
  }, true)
}

function goBack() {
  router.back()
}

function handleResize() {
  chartInstance?.resize()
}

onMounted(async () => {
  await fetchData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<template>
  <div class="inventory-page">
    <!-- Header -->
    <div class="page-header">
      <div class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
      </div>
      <div class="header-title">
        <span class="title-text">{{ storeName }}</span>
        <span class="title-sub">库存折线图</span>
      </div>
    </div>

    <!-- Chart area -->
    <div class="chart-container">
      <div v-if="loading" class="chart-loading">
        <div class="loading-dots">
          <span></span><span></span><span></span>
        </div>
        <span>加载中</span>
      </div>
      <div v-else ref="chartRef" class="chart"></div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
$primary: #4f6ef7;
$text-primary: #1a1a2e;
$text-secondary: #6b7280;
$text-hint: #9ca3af;
$bg: #f5f6fa;
$card-bg: #ffffff;
$radius-md: 12px;
$radius-lg: 16px;

.inventory-page {
  padding-bottom: calc(20px + env(safe-area-inset-bottom));
}

// ============================================
// Header
// ============================================
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: $card-bg;
  border-radius: $radius-lg;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f3f4f6;
  color: $text-secondary;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;

  &:active {
    background: #e5e7eb;
    transform: scale(0.95);
  }
}

.header-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.title-text {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title-sub {
  font-size: 12px;
  color: $text-hint;
}

// ============================================
// Chart
// ============================================
.chart-container {
  background: $card-bg;
  border-radius: $radius-lg;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  min-height: 400px;
}

.chart {
  width: 100%;
  height: 400px;
}

.chart-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 400px;
  color: $text-hint;
  font-size: 14px;
}

.loading-dots {
  display: flex;
  gap: 5px;

  span {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: $primary;
    animation: dot-bounce 1.4s infinite ease-in-out both;

    &:nth-child(1) { animation-delay: -0.32s; }
    &:nth-child(2) { animation-delay: -0.16s; }
  }
}

@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

// ============================================
// Responsive
// ============================================
@media screen and (min-width: 769px) {
  .chart {
    height: 500px;
  }
  .chart-container {
    padding: 24px;
  }
}
</style>