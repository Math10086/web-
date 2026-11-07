<template>
  <div v-loading="loading">
    <el-empty v-if="!loading && !data" description="暂无法获取销售数据" />

    <template v-else-if="data">
      <!-- 经营概览 -->
      <div class="x-card mb-16">
        <div class="card-title">
          📈 销售概览
          <span class="sub-text" style="margin-left: auto; font-weight: normal">
            数据覆盖{{ data.coverage?.monthCount || 0 }}个月
            <template v-if="data.coverage?.firstMonth">
              （{{ data.coverage.firstMonth }} ~ {{ data.coverage.lastMonth }}）
            </template>
          </span>
        </div>
        <div class="biz-kpi">
          <div class="biz-kpi-item">
            <div class="biz-kpi-num">{{ data.overview?.orderCount || 0 }}</div>
            <div class="sub-text">订单数</div>
          </div>
          <div class="biz-kpi-item">
            <div class="biz-kpi-num">¥{{ fmt(data.overview?.revenue) }}</div>
            <div class="sub-text">店铺成交额</div>
          </div>
          <div class="biz-kpi-item">
            <div class="biz-kpi-num">{{ data.overview?.itemSold || 0 }}</div>
            <div class="sub-text">累计销量（件）</div>
          </div>
          <div class="biz-kpi-item">
            <div class="biz-kpi-num">¥{{ fmt(data.overview?.avgOrderValue) }}</div>
            <div class="sub-text">客单价</div>
          </div>
          <div class="biz-kpi-item">
            <div class="biz-kpi-num">{{ data.overview?.productCount || 0 }}</div>
            <div class="sub-text">在售商品</div>
          </div>
        </div>
      </div>

      <!-- 经营建议 -->
      <div v-if="data.advice?.length" class="x-card mb-16">
        <div class="card-title">🧠 数据说了什么</div>
        <div v-for="(a, i) in data.advice" :key="i" class="advice-item">
          <span class="advice-dot">{{ i + 1 }}</span>
          <span>{{ a }}</span>
        </div>
        <div class="sub-text" style="font-size: 12px; margin-top: 8px">{{ data.note }}</div>
      </div>

      <!-- 季度高峰 -->
      <div class="x-card mb-16">
        <div class="card-title">
          🗓️ 季度交易高峰
          <el-tag v-if="data.peak?.quarterLabel" type="warning" effect="plain" size="small" style="margin-left: 8px">
            高峰：{{ data.peak.quarterLabel }}
          </el-tag>
        </div>
        <div ref="qChart" class="sales-chart" />
      </div>

      <!-- 月度分布 -->
      <div class="x-card mb-16">
        <div class="card-title">
          📅 月度成交额分布
          <span v-if="data.peak?.monthLabel" class="sub-text" style="margin-left: auto; font-weight: normal">
            {{ data.peak.monthLabel }}最高
            <template v-if="data.peak.lowMonthLabel"> · {{ data.peak.lowMonthLabel }}最低</template>
          </span>
        </div>
        <div ref="mChart" class="sales-chart" />
      </div>

      <!-- 品类 × 月份热销热力图（离散数据） -->
      <div class="x-card mb-16">
        <div class="card-title">
          🔥 品类 × 月份 热销热力图
          <el-tag
            v-if="data.categoryMonths?.insufficient"
            type="warning"
            effect="plain"
            size="small"
            style="margin-left: 8px"
          >
            演示样例 · 明细订单尚少
          </el-tag>
        </div>
        <div class="sub-text mb-16">颜色越深 = 当月该品类卖得越多；用于选品与促销节奏安排</div>
        <div ref="hmChart" style="height: 330px" />
      </div>

      <div class="sales-grid">
        <!-- 品类结构：与右侧 Top5同一套排行榜样式（名次徽标 + 色块 + 红价 + 销量进度条） -->
        <div class="x-card">
          <div class="card-title">📦 品类结构</div>
          <div v-if="sortedCategories.length" class="top-rank">
            <div
              v-for="(c, i) in sortedCategories"
              :key="c.category"
              class="top-rank-row"
            >
              <span class="tr-no" :class="`no-${i + 1}`">{{ i + 1 }}</span>
              <div class="tr-chip" :style="{ background: catColor(i) }">
                {{ (c.category || '其')[0] }}
              </div>
              <div class="tr-main">
                <div class="tr-name">{{ c.category }}</div>
                <div class="tr-sub">
                  <span class="tr-price">¥{{ fmt(c.revenue) }}</span>
                  <span class="tr-meta">库存{{ c.stock }}件</span>
                </div>
                <div class="tr-bar">
                  <div
                    class="tr-bar-fill"
                    :style="{ width: catBarWidth(c.sales) + '%' }"
                  ></div>
                </div>
              </div>
              <div class="tr-sales">
                <div class="tr-sales-num">{{ c.sales }}</div>
                <div class="tr-sales-label">销量</div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无品类数据" :image-size="60" />
        </div>

        <!-- 商品表现：排行榜样式（名次徽标 + 缩略图 + 红价 + 销量进度条） -->
        <div class="x-card">
          <div class="card-title">🏆 商品销量 Top 5</div>
          <div v-if="topBySales.length" class="top-rank">
            <div
              v-for="(p, i) in topBySales"
              :key="p.id"
              class="top-rank-row"
            >
              <span class="tr-no" :class="`no-${i + 1}`">{{ i + 1 }}</span>
              <img v-if="p.image" class="tr-img" :src="p.image" :alt="p.name" />
              <div v-else class="tr-img tr-img-empty">🏷️</div>
              <div class="tr-main">
                <div class="tr-name" :title="p.name">{{ p.name }}</div>
                <div class="tr-sub">
                  <span class="tr-price">¥{{ fmt(p.price) }}</span>
                  <span class="tr-meta">
                    {{ p.ratingAvg ? `★ ${p.ratingAvg}` : '暂无评分' }} · 占比{{ p.share }}%
                  </span>
                </div>
                <div class="tr-bar">
                  <div
                    class="tr-bar-fill"
                    :style="{ width: barWidth(p.sales) + '%' }"
                  ></div>
                </div>
              </div>
              <div class="tr-sales">
                <div class="tr-sales-num">{{ p.sales }}</div>
                <div class="tr-sales-label">销量</div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无商品" :image-size="60" />
        </div>
      </div>

      <!-- 滞销提醒 -->
      <div v-if="data.slowProducts?.length" class="x-card mt-16">
        <div class="card-title">🐢 库存积压提醒</div>
        <div v-for="s in data.slowProducts" :key="s.id" class="slow-row">
          <span style="font-weight: 600">{{ s.name }}</span>
          <span class="sub-text">库存{{ s.stock }}件 · 累计售出{{ s.sales }}件</span>
          <el-tag size="small" type="danger" effect="plain">
            周转慢
          </el-tag>
        </div>
        <div class="sub-text" style="font-size: 12px; margin-top: 6px">
          可在商品详情页把滞销品挂到热销款旁边做关联推荐，或做「热销 + 滞销」组合装
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { productApi } from '@/api'

const loading = ref(false)
const data = ref(null)
const qChart = ref(null)
const mChart = ref(null)
const hmChart = ref(null)
let qi = null
let mi = null
let hmi = null

function fmt(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

/** 进度条宽度：以榜一销量为100% 基准 */
function barWidth(sales) {
  const list = topBySales.value || []
  const max = Math.max(...list.map((p) => p.sales || 0), 1)
  return Math.max(6, Math.round(((sales || 0) * 100) / max))
}

/* ---- 品类结构：复用Top5排行榜样式 ---- */
/** 品类按销量降序，排第一的销量最大 */
const sortedCategories = computed(() =>
  [...(data.value?.categories || [])].sort((a, b) => (b.sales || 0) - (a.sales || 0))
)

/** 商品销量Top5：按「销量」降序（后端默认按成交额排序，与标题语义不符） */
const topBySales = computed(() =>
  [...(data.value?.topProducts || [])].sort((a, b) => (b.sales || 0) - (a.sales || 0)).slice(0, 5)
)

/** 品类色块配色（与洞察页消费类目同一套色系） */
const CAT_COLORS = ['#2F80ED', '#52C41A', '#FA8C16', '#722ED1', '#13C2C2', '#EB2F96']
function catColor(i) {
  return CAT_COLORS[i % CAT_COLORS.length]
}

/** 品类进度条：以销量最高的品类为100% 基准 */
function catBarWidth(sales) {
  const max = Math.max(...sortedCategories.value.map((c) => c.sales || 0), 1)
  return Math.max(6, Math.round(((sales || 0) * 100) / max))
}

function renderQuarters() {
  if (!qChart.value || !data.value) return
  if (!qChart.value.clientWidth) return
  if (!qi) qi = echarts.init(qChart.value)
  const rows = data.value.quarters || []
  // 按成交额标出峰值季度，双轴双曲线便于对比两组走势
  const peakIdx = rows.reduce(
    (best, r, i) => ((r.revenue || 0) > ((rows[best] || {}).revenue || 0) ? i : best),
    0
  )
  const peakQuarter = rows[peakIdx]?.label
  qi.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单数', '成交额'], right: 10, top: 0 },
    grid: { left: 50, right: 60, top: 42, bottom: 34 },
    xAxis: { type: 'category', data: rows.map((r) => r.label) },
    yAxis: [
      { type: 'value', name: '订单数' },
      { type: 'value', name: '成交额(¥)' }
    ],
    series: [
      {
        name: '订单数',
        type: 'bar',
        // 柱宽占槽位42%（上限52px）：4个季度时不会出现大片空隙
        barWidth: '42%',
        barMaxWidth: 52,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          // 同色系渐变
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#7da7fb' },
            { offset: 1, color: '#5b8ff9' }
          ]),
          shadowBlur: 6,
          shadowOffsetY: 3,
          shadowColor: 'rgba(91,143,249,.3)'
        },
        label: { show: true, position: 'top' },
        data: rows.map((r) => r.orders)
      },
      {
        name: '成交额',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 7,
        lineStyle: {
          width: 3,
          // 双色渐变描边：订单蓝 → 成交橙，与柱系列区分度更高
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#5b8ff9' },
            { offset: 1, color: '#ff8a00' }
          ])
        },
        itemStyle: { color: '#ff8a00' },
        label: {
          show: true,
          formatter: (p) => (p.dataIndex === peakIdx ? '峰值 ' : '') + (p.value >= 10000 ? (p.value / 10000).toFixed(1) +'万' : p.value),
          color: '#C2570A',
          fontWeight: 700
        },
        data: rows.map((r) => r.revenue),
        markPoint: {
          symbol: 'pin',
          symbolSize: 46,
          itemStyle: { color: '#ff8a00' },
          label: { color: '#fff', fontSize: 10, formatter: '峰值' },
          data: peakQuarter ? [{ xAxis: peakQuarter, yAxis: rows[peakIdx].revenue }] : []
        }
      }
    ]
  })
}

function renderMonths() {
  if (!mChart.value || !data.value) return
  if (!mChart.value.clientWidth) return
  if (!mi) mi = echarts.init(mChart.value)
  const rows = data.value.months || []
  const peak = data.value.peak || {}
  mi.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 30, top: 30, bottom: 34 },
    xAxis: { type: 'category', data: rows.map((r) => r.label) },
    yAxis: { type: 'value', name: '成交额(¥)' },
    series: [
      {
        type: 'bar',
        // 12个月：柱宽占槽位55%（上限40px），比原先写死18px更饱满
        barWidth: '55%',
        barMaxWidth: 40,
        data: rows.map((r) => ({
          value: r.revenue,
          itemStyle: {
            color: r.month === peak.month ? '#ff8a00' : '#a9c4f5',
            borderRadius: [5, 5, 0, 0]
          }
        }))
      }
    ]
  })
}

function renderAll() {
  renderQuarters()
  renderMonths()
  renderHeatmap()
}

function onResize() {
  qi && qi.resize()
  mi && mi.resize()
  hmi && hmi.resize()
}

// ==================== 品类×月份 热力图 ====================

/** 演示样例矩阵：订单明细太少（<20件）时兜底，页面会标注"演示样例" */
const DEMO_CATEGORY_MONTHS = (() => {
  const rows = {
    户外装备: [3, 4, 6, 9, 12, 16, 20, 18, 13, 8, 5, 4],
    露营装备: [1, 2, 4, 8, 14, 20, 24, 20, 12, 6, 3, 2],
    摄影数码: [6, 5, 6, 8, 10, 12, 14, 12, 10, 9, 11, 14],
    旅行服饰: [4, 5, 8, 10, 9, 7, 8, 9, 12, 14, 8, 6],
    图书地图: [2, 2, 3, 3, 4, 5, 6, 5, 4, 3, 3, 4]
  }
  const cells = []
  for (const [cat, arr] of Object.entries(rows)) {
    arr.forEach((count, i) => count && cells.push({ category: cat, month: i + 1, count }))
  }
  return { categories: Object.keys(rows), cells, total: 300, insufficient: true }
})()

/** 真实数据足够时用真实矩阵，否则回落演示样例 */
const catMonths = computed(() => {
  const d = data.value?.categoryMonths
  return d && !d.insufficient ? d : DEMO_CATEGORY_MONTHS
})

function renderHeatmap() {
  if (!hmChart.value || !catMonths.value) return
  if (!hmChart.value.clientWidth) return
  if (!hmi) hmi = echarts.init(hmChart.value)
  const cm = catMonths.value
  const cats = cm.categories
  const months = Array.from({ length: 12 }, (_, i) => `${i + 1}月`)
  const max = Math.max(...cm.cells.map((c) => c.count), 1)
  hmi.setOption({
    tooltip: {
      position: 'top',
      formatter: (p) => `${cats[p.value[0]]}<br/>${months[p.value[1]]}：<b>${p.value[2]}</b> 件`
    },
    grid: { left: 90, right: 20, top: 14, bottom: 64 },
    xAxis: { type: 'category', data: months, splitArea: { show: true } },
    yAxis: { type: 'category', data: cats, splitArea: { show: true } },
    // 颜色深浅与数值单调对应，并附图例
    visualMap: {
      min: 0,
      max,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 6,
      text: ['热销', '冷门'],
      // 跨色系渐变（淡黄→黄→橙→红→暗红）：比单色橙带色差大得多，
      // 低值接近纸白、中值明黄、高值暗红，热冷一眼可分
      inRange: { color: ['#FFF9DB', '#FFE066', '#FF9F1C', '#E8590C', '#A61E1E'] }
    },
    series: [
      {
        type: 'heatmap',
        data: cm.cells.map((c) => [cats.indexOf(c.category), c.month - 1, c.count]),
        label: {
          show: true,
          fontSize: 10,
          // 深色格子（≥55% 峰值）用白字，浅色格子用深黄字，两种底色都读得清
          formatter: (p) =>
            p.value[2] >= max * 0.55 ? `{hot|${p.value[2]}}` : `{cool|${p.value[2] || ''}}`,
          rich: {
            hot: { color: '#fff', fontWeight: 700, fontSize: 10 },
            cool: { color: '#9A6700', fontSize: 10 }
          }
        },
        emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,.3)' } }
      }
    ]
  })
}

async function load() {
  loading.value = true
  try {
    data.value = await productApi.merchantInsight()
    await nextTick()
    // 首次挂载时容器可能还没量出宽度，补一次延时渲染兜底
    renderAll()
    setTimeout(renderAll, 150)
  } catch (e) {
    console.error('[sales-panel] load fail:', e)
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  hmi && hmi.dispose()
  qi && qi.dispose()
  mi && mi.dispose()
  qi = null
  mi = null
})
</script>

<style scoped>
.sales-chart {
  width: 100%;
  height: 280px;
}
.sales-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.advice-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.75;
  margin-bottom: 8px;
}
.advice-dot {
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--xlvq-primary);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 3px;
}
.slow-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.slow-row:last-child {
  border-bottom: none;
}
/* ---- 商品销量Top5排行榜 ---- */
.top-rank {
  display: flex;
  flex-direction: column;
}
.top-rank-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 2px;
  border-bottom: 1px dashed var(--xlvq-border, #eef1f5);
}
.top-rank-row:last-child {
  border-bottom: none;
}
.tr-no {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 800;
  background: #f0f2f5;
  color: #8a94a3;
}
.tr-no.no-1 {
  background: #ff4d4f;
  color: #fff;
}
.tr-no.no-2 {
  background: #ff8a3d;
  color: #fff;
}
.tr-no.no-3 {
  background: #ffc53d;
  color: #fff;
}
.tr-img {
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: 8px;
  object-fit: cover;
  background: #f5f6f8;
}
.tr-img-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
/* 品类结构用的色块（替代商品缩略图的位置） */
.tr-chip {
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 800;
  opacity: 0.88;
}
.tr-main {
  flex: 1;
  min-width: 0;
}
.tr-name {
  font-size: 13px;
  font-weight: 700;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tr-sub {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 2px;
  font-size: 12px;
}
.tr-price {
  color: #ff4d4f;
  font-weight: 800;
}
.tr-meta {
  color: #a0a6af;
}
.tr-bar {
  margin-top: 5px;
  height: 6px;
  border-radius: 3px;
  background: #f0f2f5;
  overflow: hidden;
}
.tr-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #52c41a, #95de64);
}
.tr-sales {
  flex: none;
  text-align: right;
  min-width: 40px;
}
.tr-sales-num {
  font-size: 18px;
  font-weight: 800;
  color: #303133;
  line-height: 1.1;
}
.tr-sales-label {
  font-size: 11px;
  color: #a0a6af;
}
@media (max-width: 900px) {
  .sales-grid {
    grid-template-columns: 1fr;
  }
}
</style>
