<template>
  <div :class="embedded ? '' : 'page-wrap'">
    <div v-if="!embedded" class="flex-between mb-16">
      <h2 class="page-title" style="margin: 0">📊 数据看板 · 平台数据可视化</h2>
      <span class="sub-text">数据实时来自平台用户行为（足迹 / 攻略 / 路线）</span>
    </div>

    <!-- 概览指标条 -->
    <div class="kpi-row mb-16">
      <div v-for="k in kpis" :key="k.label" class="kpi-card x-card">
        <div class="kpi-icon">{{ k.icon }}</div>
        <div>
          <div class="kpi-value">{{ k.value }}</div>
          <div class="sub-text">{{ k.label }}</div>
        </div>
      </div>
    </div>

    <div class="board-grid">
      <!-- 1. 各城市活跃人数（渐变对比条形图） -->
      <div class="x-card board-card wide">
        <div class="card-title">🏙️ 各城市活跃人数（去重用户）</div>
        <div ref="cityChartEl" class="chart-box" style="height: 320px"></div>
      </div>

      <!-- 2. 路线模式选择排行榜（南丁格尔玫瑰图） -->
      <div class="x-card board-card">
        <div class="card-title">🧭 路线模式选择排行榜</div>
        <div ref="modeChartEl" class="chart-box" style="height: 320px"></div>
      </div>

      <!-- 3. 景点到访人次（棒棒糖图） -->
      <div class="x-card board-card wide">
        <div class="card-title">📍 景点到访人次 Top12（棒棒糖图）</div>
        <div ref="spotChartEl" class="chart-box" style="height: 380px"></div>
      </div>

      <!-- 4. 城市内容构成（堆叠条形图） -->
      <div class="x-card board-card">
        <div class="card-title">🧱 城市内容构成（攻略/足迹/路线）</div>
        <div ref="stackChartEl" class="chart-box" style="height: 320px"></div>
      </div>

      <!-- 5. 路线城市排行榜（横向条形） -->
      <div class="x-card board-card">
        <div class="card-title">🛣️ 路线城市选择排行榜</div>
        <div ref="routeCityChartEl" class="chart-box" style="height: 320px"></div>
      </div>

      <!-- 6. 近7日发帖趋势（面积图） -->
      <div class="x-card board-card">
        <div class="card-title">📈 近7日发帖趋势</div>
        <div ref="trendChartEl" class="chart-box" style="height: 320px"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { statsApi } from '@/api'
import { ROUTE_MODE_MAP } from '@/utils/format'

/**
 * embedded = true时作为「管理后台 → 数据看板」Tab的内嵌内容渲染（不重复页头标题）。
 */
defineProps({ embedded: { type: Boolean, default: false } })

const kpis = ref([])
const cityChartEl = ref(null)
const modeChartEl = ref(null)
const spotChartEl = ref(null)
const stackChartEl = ref(null)
const routeCityChartEl = ref(null)
const trendChartEl = ref(null)
const charts = []

function mount(el, option) {
  if (!el?.value) return
  const chart = echarts.init(el.value)
  chart.setOption(option)
  charts.push(chart)
}

async function loadAll() {
  const [overview, cityAct, spotVisits, routeRank, trend] = await Promise.allSettled([
    statsApi.overview(),
    statsApi.cityActivity(),
    statsApi.spotVisits(),
    statsApi.routeRanking(),
    statsApi.postTrend()
  ])
  await nextTick()

  // ===== KPI概览 =====
  const ov = overview.status === 'fulfilled' ? overview.value || {} : {}
  const cityList = cityAct.status === 'fulfilled' ? cityAct.value || [] : []
  const sumOf = (k) => cityList.reduce((a, c) => a + (Number(c?.[k]) || 0), 0)
  kpis.value = [
    { icon: '👥', label: '注册用户', value: ov.userCount ?? '—' },
    { icon: '📝', label: '攻略/动态', value: ov.postCount ?? '—' },
    { icon: '🛣️', label: '已保存路线', value: ov.routeCount ?? sumOf('routeCount') },
    { icon: '📍', label: '足迹打卡', value: ov.footprintCount ?? sumOf('footprintCount') },
    { icon: '🎯', label: '兴趣圈层', value: ov.circleCount ?? '—' },
    { icon: '🛍️', label: '商城订单', value: ov.orderCount ?? '—' },
    { icon: '🧭', label: '向导预约', value: ov.bookingCount ?? '—' }
  ]

  // ===== 1. 各城市活跃人数：渐变横向条形 =====
  const cities = cityAct.status === 'fulfilled' ? cityAct.value || [] : []
  if (cities.length) {
    const sorted = [...cities].sort((a, b) => a.activeUsers - b.activeUsers)
    mount(cityChartEl, {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 80, right: 40, top: 20, bottom: 30 },
      xAxis: { type: 'value', minInterval: 1 },
      yAxis: { type: 'category', data: sorted.map((c) => c.city) },
      series: [
        {
          type: 'bar',
          data: sorted.map((c) => c.activeUsers),
          // 横向条形：占槽位45%，间距更紧凑
          barWidth: '45%',
          barMaxWidth: 26,
          label: { show: true, position: 'right', fontWeight: 700, color: '#2F80ED' },
          itemStyle: {
            borderRadius: [0, 9, 9, 0],
            // 按数值由低到高从浅到深的蓝色渐变
            color: (p) =>
              new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: '#8fc3ff' },
                { offset: 1, color: p.dataIndex >= sorted.length - 1 ? '#1D6FE0' : '#2F80ED' }
              ])
          }
        }
      ]
    })
  }

  // ===== 2. 路线模式排行榜：南丁格尔玫瑰图 =====
  const rank = routeRank.status === 'fulfilled' ? routeRank.value || {} : {}
  const modeRank = (rank.modeRank || []).filter((m) => m.count > 0)
  if (modeRank.length) {
    mount(modeChartEl, {
      tooltip: { trigger: 'item', formatter: '{b}<br/>被保存 {c} 次（{d}%）' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          type: 'pie',
          roseType: 'area',
          radius: ['22%', '72%'],
          center: ['50%', '46%'],
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { formatter: '{b}\n{c} 次' },
          data: modeRank.map((m) => ({
            name: m.name || ROUTE_MODE_MAP[m.mode] || m.mode,
            value: m.count
          })),
          color: ['#2F80ED', '#F5222D', '#52C41A', '#FA8C16', '#722ED1', '#13C2C2', '#EB2F96']
        }
      ]
    })
  }

  // ===== 3. 景点到访人次：棒棒糖图（细杆+圆头+渐变值标签） =====
  const spots = spotVisits.status === 'fulfilled' ? spotVisits.value || [] : []
  if (spots.length) {
    const list = [...spots].reverse() // 横向从下到上升序
    const maxV = Math.max(...list.map((s) => s.visits)) || 1
    mount(spotChartEl, {
      tooltip: {
        trigger: 'item',
        formatter: (p) =>
          p.seriesType === 'line'
            ? `${p.name}<br/>打卡${p.value} 次`
            : `${p.name}<br/>热度指数${p.value}`
      },
      grid: { left: 110, right: 60, top: 16, bottom: 30 },
      xAxis: { type: 'value', minInterval: 1, show: false },
      yAxis: {
        type: 'category',
        data: list.map((s) => `${s.name} · ${s.city}`),
        axisTick: { show: false },
        axisLine: { show: false }
      },
      series: [
        {
          type: 'bar', // 棒棒糖的"杆"
          barWidth: 3,
          silent: true,
          itemStyle: { color: '#c9dcf5' },
          data: list.map((s) => s.visits)
        },
        {
          type: 'line', // 棒棒糖的"糖头"
          symbol: 'circle',
          symbolSize: (v) => 10 + (v / maxV) * 12,
          lineStyle: { width: 0 },
          label: { show: true, position: 'right', color: '#1D6FE0', fontWeight: 700 },
          data: list.map((s) => s.visits),
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#13C2C2' },
              { offset: 1, color: '#2F80ED' }
            ])
          }
        }
      ]
    })
  }

  // ===== 4. 城市内容构成：堆叠条形图 =====
  if (cities.length) {
    const top = [...cities].sort((a, b) => b.activeUsers - a.activeUsers).slice(0, 8)
    mount(stackChartEl, {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { bottom: 0 },
      grid: { left: 50, right: 20, top: 26, bottom: 56 },
      xAxis: {
        type: 'category',
        data: top.map((c) => c.city),
        axisLabel: { interval: 0, rotate: 24 }
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '攻略/动态',
          type: 'bar',
          stack: 'total',
          // 堆叠柱：占槽位40%（上限44px），同组三色仍完整显示，间距收紧
          barWidth: '40%',
          barMaxWidth: 44,
          itemStyle: { color: '#2F80ED' },
          data: top.map((c) => c.postCount)
        },
        {
          name: '足迹打卡',
          type: 'bar',
          stack: 'total',
          itemStyle: { color: '#52C41A' },
          data: top.map((c) => c.footprintCount)
        },
        {
          name: '保存路线',
          type: 'bar',
          stack: 'total',
          itemStyle: { color: '#FA8C16', borderRadius: [6, 6, 0, 0] },
          data: top.map((c) => c.routeCount)
        }
      ]
    })
  }

  // ===== 5. 路线城市排行榜：横向条形 =====
  const cityRank = rank.cityRank || []
  if (cityRank.length) {
    const asc = [...cityRank].reverse()
    mount(routeCityChartEl, {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 80, right: 46, top: 16, bottom: 30 },
      xAxis: { type: 'value', minInterval: 1 },
      yAxis: { type: 'category', data: asc.map((c) => c.city) },
      series: [
        {
          type: 'bar',
          barWidth: '45%',
          barMaxWidth: 26,
          label: { show: true, position: 'right', color: '#FA8C16', fontWeight: 700 },
          data: asc.map((c) => c.count),
          itemStyle: {
            borderRadius: [0, 9, 9, 0],
            color: (p) =>
              new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: '#ffd08a' },
                { offset: 1, color: '#FA8C16' }
              ])
          }
        }
      ]
    })
  }

  // ===== 6. 近7日发帖趋势：面积图 =====
  const trendData = trend.status === 'fulfilled' ? trend.value || [] : []
  if (trendData.length) {
    mount(trendChartEl, {
      tooltip: { trigger: 'axis' },
      grid: { left: 44, right: 20, top: 26, bottom: 34 },
      xAxis: { type: 'category', data: trendData.map((d) => d.date.slice(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          data: trendData.map((d) => d.count),
          lineStyle: { width: 3, color: '#722ED1' },
          itemStyle: { color: '#722ED1' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(114,46,209,.35)' },
              { offset: 1, color: 'rgba(114,46,209,.02)' }
            ])
          }
        }
      ]
    })
  }
}

function onResize() {
  charts.forEach((c) => c.resize())
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(178px, 1fr));
  gap: 12px;
}
.kpi-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
}
.kpi-icon {
  font-size: 28px;
}
.kpi-value {
  font-size: 24px;
  font-weight: 800;
  color: #1f2d3d;
  line-height: 1.2;
}
.board-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.board-card {
  min-width: 0;
}
.board-card.wide {
  grid-column: span 2;
}
@media (max-width: 900px) {
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .board-grid {
    grid-template-columns: 1fr;
  }
  .board-card.wide {
    grid-column: span 1;
  }
}
</style>
