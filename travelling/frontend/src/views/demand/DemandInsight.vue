<template>
  <div class="page-wrap">
    <div class="flex-between mb-16" style="flex-wrap: wrap; gap: 8px">
      <h2 class="page-title" style="margin: 0">
        {{ isGuide ? '📈 需求洞察' : '📈 选品分析' }}
      </h2>
      <span class="sub-text">
        {{ isGuide ? '导游视角 · 哪些城市还缺向导服务' : '商家视角 · 选品与定价参考' }}
      </span>
    </div>

    <!-- 一句话结论 -->
    <div class="x-card mb-16 headline-card">
      <div class="headline-text">{{ data.headline || '正在计算供需缺口…' }}</div>
      <div class="sub-text mt-12">
        数据来自{{ sample.posts }}篇攻略 · {{ sample.trips }}支结伴队伍 ·
        {{ isGuide ? sample.services + ' 个在售向导服务' : sample.products + ' 件在售商品' }}
        <el-tag v-if="!sample.enough" size="small" type="warning" class="ml-8">
          样本较少，仅供参考
        </el-tag>
      </div>
    </div>

    <!-- 概览 -->
    <div class="biz-kpi mb-16">
      <div v-if="!isMerchant" class="biz-kpi-item">
        <div class="biz-kpi-num">{{ blankCities.length }}</div>
        <div class="sub-text">供给空白城市</div>
      </div>
      <div v-if="!isMerchant" class="biz-kpi-item">
        <div class="biz-kpi-num">{{ topTransportPct }}%</div>
        <div class="sub-text">{{ topTransportLabel }}占比</div>
      </div>
      <div v-if="!isMerchant" class="biz-kpi-item">
        <div class="biz-kpi-num">¥{{ budget.avg || 0 }}</div>
        <div class="sub-text">队伍人均预算（中位 ¥{{ budget.median || 0 }}）</div>
      </div>
      <div class="biz-kpi-item">
        <div class="biz-kpi-num">{{ sample.trips }}</div>
        <div class="sub-text">在招 / 进行中队伍</div>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <!-- ① 供需缺口（商家不展示：选品参考以主题为主） -->
      <el-tab-pane v-if="!isMerchant" label="供需缺口" name="gaps">
        <!-- 极坐标图：直接嵌入原页面，按容器宽度等比缩放，避免出现滚动条 -->
        <PolarFlowChart />
        <div class="x-card">
          <div class="card-title">🔥 需求热度 vs 现有供给</div>
          <div class="sub-text mb-16">
            缺口分 = 需求热度 ÷（供给数 + 1），即「每个在售{{
              isGuide ? '服务' : '商品'
            }}摊到多少需求」；数值越高越值得进入，明细见下方榜单
          </div>
          <!-- 玫瑰图已删，保留总结数据条 -->
          <div class="gap-summary">
            <div class="gs-title">截至{{ todayText }}</div>
            <div class="gs-line">共 <strong>{{ gaps.length }}</strong> 个城市存在缺口</div>
            <div class="gs-line">缺口总分 <strong>{{ totalGap }}</strong> 分</div>
            <div class="gs-line">高缺口 <strong>{{ highCount }}</strong> 城 · 完全空白 <strong>{{ blankCount }}</strong> 城</div>
            <div class="gs-line">在售向导服务{{ sample.services || 0 }}个</div>
          </div>
        </div>

        <div class="x-card mt-16">
          <div class="card-title">🎯 缺口榜单</div>
          <el-empty v-if="!gaps.length" description="暂无足够数据" :image-size="60" />
          <div v-for="g in gaps" :key="g.city" class="gap-item">
            <div class="flex-between" style="flex-wrap: wrap; gap: 6px">
              <div>
                <strong style="font-size: 15px">{{ g.city }}</strong>
                <el-tag
                  :type="g.level === 'HIGH' ? 'danger' : g.level === 'MID' ? 'warning' : 'info'"
                  size="small"
                  class="ml-8"
                >
                  {{ levelLabel(g.level) }}
                </el-tag>
                <el-tag v-if="!g.supplyCount" size="small" type="danger" effect="plain" class="ml-8">
                  完全空白
                </el-tag>
              </div>
              <div class="sub-text">
                缺口分 <strong style="color: var(--xlvq-primary)">{{ g.gapScore }}</strong>
                · 已有{{ supplyName }} {{ g.supplyCount }} {{ supplyUnit }}
              </div>
            </div>
            <div class="sub-text mt-12">
              攻略{{ g.demand?.posts }} · 结伴{{ g.demand?.trips }} · 线路
              {{ g.demand?.routes }} · 足迹{{ g.demand?.footprints }}
              <span v-if="g.themes?.length"> · 热门主题：{{ g.themes.join('、') }}</span>
            </div>
            <div class="gap-suggestion">{{ g.suggestion }}</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ② 出行与预算 -->
      <el-tab-pane v-if="!isMerchant" label="出行与预算" name="transport">
        <div class="chart-row">
          <div class="x-card chart-card">
            <div class="card-title">🚄 结伴队伍的出行方式</div>
            <div class="sub-text mb-16">
              决定商品形态：高铁为主 → 主推便携 / 短途装备；自驾为主 → 主推车载与大件
            </div>
            <div ref="transportChartEl" style="height: 280px"></div>
          </div>
          <div class="x-card chart-card">
            <div class="card-title">💰 人均预算分档</div>
            <div class="sub-text mb-16">定价锚点：把主力商品放在占比最高的档位</div>
            <div ref="budgetChartEl" style="height: 280px"></div>
          </div>
        </div>

        <div class="x-card mt-16">
          <div class="card-title">明细</div>
          <table class="demand-table">
            <thead>
              <tr>
                <th>出行方式</th>
                <th>队伍数</th>
                <th>占比</th>
                <th>该方式平均预算</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in transportMix" :key="t.mode">
                <td>{{ t.label }}</td>
                <td>{{ t.count }}</td>
                <td>{{ pct(t.ratio) }}%</td>
                <td>¥{{ t.avgBudget }}</td>
              </tr>
              <tr v-if="!transportMix.length">
                <td colspan="4" class="sub-text">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- ⑤ 城市景点热度（原「供给体系」独立标签页，已并入出行与预算） -->
        <div class="x-card mt-16">
          <div class="flex-between mb-12" style="flex-wrap: wrap; gap: 8px">
            <div class="card-title" style="margin: 0">
              🧭 城市景点热度分布
              <el-tag size="small" type="warning" effect="plain" style="margin-left: 6px">平台行为合成</el-tag>
            </div>
            <el-select
              v-model="polarCity"
              size="small"
              style="width: 220px"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入城市"
              @change="onPolarCityChange"
            >
              <el-option label="全部城市（Top 12）" value="" />
              <el-option
                v-for="c in polarCities"
                :key="c.city"
                :label="`${c.city}（${c.count} 个景点）`"
                :value="c.city"
              />
            </el-select>
          </div>
          <div class="sub-text mb-8">
            同一城市内景点热度对比，半径越长越拥挤——带队选时段、避峰的参考
          </div>
          <div class="heat-formula mb-16">
            <span class="hf-label">热度口径</span>
            足迹打卡 ×5 · 帖子提及 ×3 · 收藏 ×2 · 结伴目的地 ×4（只命中城市时按1.5分摊；30天内全额、90天内0.6、更早按比例衰减）
            ，合成时占65%；另35% 为景点基础知名度。热度由平台真实行为实时计算，不是人工填的固定值。
          </div>
          <el-empty
            v-if="!spotsPolar.length"
            :description="polarCity ? `「${polarCity}」暂无景点数据，可换一个城市或选「全部城市」` : '暂无景点数据'"
            :image-size="60"
          />
          <div v-show="spotsPolar.length" ref="polarChartEl" style="height: 500px"></div>
        </div>
      </el-tab-pane>

      <!-- ③ 主题需求：商家看「选什么品」，导游看「做什么主题」 -->
      <el-tab-pane :label="isGuide ? '兴趣主题' : '主题选品'" name="themes">
        <!-- ④ 兴趣—目的地—消费 桑基流转（原独立标签页，已并入主题页，置于队伍兴趣之前） -->
        <div class="x-card">
          <div class="card-title">🕸️ 兴趣 — 目的地 — 消费 关联</div>
          <div class="sub-text mb-16">
            三层桑基图：学生有什么兴趣 → 去了哪些目的地 → 在那里花什么钱；线条越宽人数越多。
            可用于「按兴趣组织线路、按目的地备货」的决策参考
          </div>
          <el-alert
            v-if="flowData?.insufficient"
            type="warning"
            :closable="false"
            title="样本量不足，以下为演示样例数据（非真实统计）"
            class="mb-16"
          />
          <el-empty v-if="!hasFlow" description="暂无流转数据" :image-size="60" />
          <div ref="flowChartEl" style="height: 680px"></div>
        </div>

        <div class="x-card mt-16">
          <div class="card-title">
            {{ isGuide ? '🧭 队伍兴趣标签 → 可主推的主题' : '🧭 队伍兴趣标签 → 建议经营品类' }}
          </div>
          <div class="sub-text mb-16">
            按结伴队伍填写的兴趣标签聚合，占比 = 提到该标签的队伍数 ÷ 总队伍数<span v-if="!isGuide">
              ；「已有商品」是该品类当前的在售数量，需求高而商品少即为选品机会</span
            ><span v-else>；可据此设计主题线路或导游服务，不必参考商品品类</span>
          </div>
          <el-empty v-if="!themeDemand.length" description="暂无数据" :image-size="60" />
          <div v-for="t in themeDemand" :key="t.tag" class="theme-row">
            <div class="flex-between" style="flex-wrap: wrap; gap: 6px">
              <div>
                <el-tag size="small" type="primary" effect="plain">{{ t.tag }}</el-tag>
                <!-- 商品品类只对商家有意义，导游不上架商品 -->
                <template v-if="!isGuide">
                  <span class="ml-8">→ 建议经营 </span>
                  <strong>{{ t.categoryHint }}</strong>
                  <el-tag
                    v-if="t.productCount !== undefined"
                    size="small"
                    :type="t.productCount === 0 ? 'danger' : 'info'"
                    effect="plain"
                    class="ml-8"
                  >
                    已有商品{{ t.productCount }}件
                  </el-tag>
                  <el-tag
                    v-if="t.level === 'HIGH'"
                    size="small"
                    type="danger"
                    class="ml-8"
                  >
                    选品机会
                  </el-tag>
                </template>
              </div>
              <div class="sub-text">{{ t.count }}支队伍 · {{ pct(t.ratio) }}%</div>
            </div>
            <el-progress
              :percentage="pct(t.ratio)"
              :stroke-width="10"
              :show-text="false"
              class="mt-12"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { demandApi, insightApi, spotApi } from '@/api'
import PolarFlowChart from '@/components/PolarFlowChart.vue'
import { useAuthStore } from '@/store/auth'

const auth = useAuthStore()
// 商家只看「主题选品」：供需缺口/出行与预算两个标签与相关KPI对商家隐藏
const isMerchant = computed(() => auth.role === 'MERCHANT')
// 支持深链直落子标签（/insight?tab=flow / ?tab=supply由父组件传入）
const props = defineProps({ initialTab: { type: String, default: '' } })
// 「关联流转」已并入主题页（?tab=flow → themes）；「供给体系」已并入出行与预算（?tab=supply → transport）
const tab = ref(
  ['gaps', 'transport', 'themes', 'flow', 'supply'].includes(props.initialTab)
    ? props.initialTab === 'flow'
      ? 'themes'
      : props.initialTab === 'supply'
        ? 'transport'
        : props.initialTab
    : isMerchant.value
      ? 'themes'
      : 'gaps'
)
const data = ref({})
const loading = ref(false)

const transportChartEl = ref(null)
const budgetChartEl = ref(null)
const flowChartEl = ref(null)
const polarChartEl = ref(null)
let charts = []

// 景点热度的数据按需加载（进入出行与预算tab才请求）；桑基流转已并入主题页，跟随themes渲染
const flowData = ref(null)
const spotsData = ref([])
const hasFlow = computed(() => !!(flowData.value?.nodes?.length && flowData.value?.links?.length))
/** 极坐标图当前聚焦的城市（'' = 全部城市）；默认聚焦景点最多的城市，保证同城可比 */
const polarCity = ref('')
const polarCities = computed(() => {
  const m = new Map()
  for (const s of spotsData.value) {
    if (!s.city) continue
    m.set(s.city, (m.get(s.city) || 0) + 1)
  }
  return [...m.entries()]
    .map(([city, count]) => ({ city, count }))
    .sort((a, b) => b.count - a.count)
})
const spotsPolar = computed(() =>
  [...spotsData.value]
    .filter((s) => (s.heat || 0) > 0 && (!polarCity.value || s.city === polarCity.value))
    .sort((a, b) => (b.heat || 0) - (a.heat || 0))
    .slice(0, 12)
)

const isGuide = computed(() => auth.role === 'GUIDE')
const gaps = computed(() => data.value.gaps || [])
const transportMix = computed(() => data.value.transportMix || [])
const budget = computed(() => data.value.budget || {})
const themeDemand = computed(() => data.value.themeDemand || [])
const sample = computed(() => data.value.sample || {})
const supplyName = computed(() => data.value.supplyName || '向导服务')
const supplyUnit = computed(() => data.value.supplyUnit || '个')

const blankCities = computed(() => gaps.value.filter((g) => !g.supplyCount))
const topTransport = computed(() => transportMix.value[0] || {})
const topTransportPct = computed(() => pct(topTransport.value.ratio))
const topTransportLabel = computed(() => topTransport.value.label || '—')

/* 玫瑰图右上角总结 */
const todayText = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric'
})
const totalGap = computed(() => gaps.value.reduce((s, g) => s + (Number(g.gapScore) || 0), 0))
const highCount = computed(() => gaps.value.filter((g) => g.level === 'HIGH').length)
const blankCount = computed(() => blankCities.value.length)

function pct(v) {
  return Math.round((Number(v) || 0) * 100)
}

function levelLabel(level) {
  return { HIGH: '高缺口', MID: '中缺口', LOW: '已饱和' }[level] || '—'
}

// ==================== 图表 ====================

function mount(el, option) {
  if (!el?.value) return
  // 同一容器重建前先释放旧实例，避免泄漏
  const old = echarts.getInstanceByDom(el.value)
  if (old) old.dispose()
  const chart = echarts.init(el.value)
  chart.setOption(option)
  charts.push(chart)
}

function renderTransportChart() {
  if (!transportMix.value.length) return
  mount(transportChartEl, {
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 支队伍（{d}%）' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '44%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%' },
        data: transportMix.value.map((t) => ({ name: t.label, value: t.count })),
        color: ['#2F80ED', '#52C41A', '#FA8C16', '#722ED1', '#13C2C2', '#EB2F96']
      }
    ]
  })
}

function renderBudgetChart() {
  const bands = budget.value.bands || []
  if (!bands.length) return
  mount(budgetChartEl, {
    tooltip: { trigger: 'axis', formatter: (p) => `${p[0].name}<br/>${p[0].value} 支队伍` },
    grid: { left: 40, right: 24, top: 24, bottom: 44 },
    xAxis: {
      type: 'category',
      data: bands.map((b) => b.label),
      axisLabel: { fontSize: 11, interval: 0, rotate: 20 }
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: bands.map((b) => b.count),
        // 柱宽占槽位55%（上限64px），让区间之间更紧凑
        barWidth: '55%',
        barMaxWidth: 64,
        label: { show: true, position: 'top', fontWeight: 700, color: '#0F6E56' },
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#5BD6A5' },
            { offset: 1, color: '#0F6E56' }
          ])
        }
      }
    ]
  })
}

// ==================== ④桑基流转图 ====================

/** 演示样例：真实流转数据不足时兜底（页面会标注"演示样例"） */
const DEMO_FLOW = {
  nodes: [
    { name: '徒步', layer: '兴趣' }, { name: '摄影', layer: '兴趣' }, { name: '美食', layer: '兴趣' }, { name: '古镇', layer: '兴趣' },
    { name: '张家界', layer: '目的地' }, { name: '长沙', layer: '目的地' }, { name: '西安', layer: '目的地' }, { name: '成都', layer: '目的地' },
    { name: '交通', layer: '消费类目' }, { name: '住宿', layer: '消费类目' }, { name: '餐饮', layer: '消费类目' }, { name: '门票', layer: '消费类目' }, { name: '购物', layer: '消费类目' }
  ],
  links: [
    { source: '徒步', target: '张家界', value: 14 }, { source: '徒步', target: '西安', value: 6 },
    { source: '摄影', target: '张家界', value: 7 }, { source: '摄影', target: '成都', value: 8 },
    { source: '美食', target: '长沙', value: 13 }, { source: '美食', target: '成都', value: 9 },
    { source: '古镇', target: '西安', value: 5 }, { source: '古镇', target: '长沙', value: 4 },
    { source: '张家界', target: '门票', value: 12 }, { source: '张家界', target: '住宿', value: 8 }, { source: '张家界', target: '交通', value: 6 },
    { source: '长沙', target: '餐饮', value: 11 }, { source: '长沙', target: '购物', value: 5 }, { source: '长沙', target: '交通', value: 4 },
    { source: '西安', target: '门票', value: 6 }, { source: '西安', target: '住宿', value: 5 }, { source: '西安', target: '餐饮', value: 4 },
    { source: '成都', target: '餐饮', value: 9 }, { source: '成都', target: '住宿', value: 7 }, { source: '成都', target: '购物', value: 3 }
  ]
}

const FLOW_COLORS = { 兴趣: '#2F80ED', 目的地: '#F08C3A', 消费类目: '#0F6E56' }

function renderFlowChart() {
  const d = flowData.value
  if (!d?.nodes?.length || !d?.links?.length || !flowChartEl.value) return
  mount(flowChartEl, {
    tooltip: {
      trigger: 'item',
      formatter: (p) =>
        p.dataType === 'edge'
          ? `${p.data.source} → ${p.data.target}<br/>宽度 = <b>${p.data.value}</b> 人/笔`
          : `${p.name}<br/>流转合计 <b>${p.value}</b>`
    },
    series: [
      {
        type: 'sankey',
        left: 24,
        right: 110,
        top: 20,
        bottom: 20,
        nodeWidth: 14,
        nodeGap: 14,
        nodeAlign: 'justify',
        emphasis: { focus: 'adjacency' },
        data: d.nodes.map((n) => ({
          ...n,
          itemStyle: { color: FLOW_COLORS[n.layer] || '#8A94A6', borderRadius: 3 }
        })),
        links: d.links,
        lineStyle: { color: 'gradient', curveness: 0.5, opacity: 0.35 },
        // 节点名加大加粗：演示投影下后排也能看清
        label: { fontSize: 15, fontWeight: 600, color: '#2b3446' }
      }
    ]
  })
}

async function ensureFlow() {
  if (!flowData.value) {
    const d = await insightApi.interestFlow().catch(() => null)
    flowData.value =
      d && d.nodes?.length && d.links?.length ? d : { ...DEMO_FLOW, insufficient: true }
  }
  await nextTick()
  renderFlowChart()
}

// ==================== ⑤城市景点热度（极坐标图，原「供给体系」页） ====================

function renderPolarChart() {
  const spots = spotsPolar.value
  if (!polarChartEl.value) return
  // 切城市需要重画：先销毁旧实例，避免echarts对同一dom重复init的警告
  echarts.getInstanceByDom(polarChartEl.value)?.dispose()
  if (!spots.length) return
  const vals = spots.map((s) => s.heat || 0)
  const max = Math.max(...vals, 1)
  const min = Math.min(...vals)
  const span = max - min
  // 把同城热度线性映射到环宽的28%~100%：最小景点也看得见，差距又拉得开。
  // 纯用radiusAxis的min偏移会让最小值贴成一个点，所以这里做数值映射、轴不显示刻度。
  const scaleVal = (v) => (span > 0 ? 0.28 + (0.72 * (v - min)) / span : 0.6)
  mount(polarChartEl, {
    tooltip: {
      trigger: 'item',
      formatter: (p) => {
        const s = p.data?.spot || {}
        const parts = [
          `足迹${s.heatFootprint ?? 0}`,
          `帖子${s.heatMention ?? 0}`,
          `收藏${s.heatFavorite ?? 0}`,
          `结伴${s.heatTrip ?? 0}`
        ].join(' · ')
        return `${p.name}（${s.city || '—'}）<br/>综合热度 <b>${s.heat ?? 0}</b><br/>`
          + `<span style="color:#8a94a6">行为明细：${parts}</span><br/>`
          + `<span style="color:#8a94a6">原始行为加权分${s.heatRaw ?? 0}</span>`
      }
    },
    angleAxis: {
      type: 'category',
      data: spots.map((s) => s.name),
      startAngle: 90,
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { fontSize: 11, color: '#5a6472' }
    },
    radiusAxis: {
      min: 0,
      max: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { lineStyle: { color: '#eef1f5' } }
    },
    polar: { radius: ['10%', '82%'], center: ['50%', '52%'] },
    series: [
      {
        type: 'bar',
        coordinateSystem: 'polar',
        roundCap: true,
        data: spots.map((s) => ({
          name: s.name,
          value: scaleVal(s.heat || 0),
          spot: s
        })),
        itemStyle: {
          color: (p) =>
            new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#ffd8b0' },
              { offset: 1, color: (p.data?.spot?.heat ?? 0) >= max * 0.7 ? '#D9480F' : '#F08C3A' }
            ])
        },
        emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(217,72,15,.4)' } }
      }
    ]
  })
}

async function ensureSupply() {
  if (!spotsData.value.length) {
    spotsData.value = await spotApi.list().catch(() => [])
    // 默认聚焦景点最多的城市：同城对比才有参考意义
    if (!polarCity.value && polarCities.value.length) {
      polarCity.value = polarCities.value[0].city
    }
  }
  await nextTick()
  renderPolarChart()
}

/** 切换城市后重画极坐标图（tab已渲染过才需要） */
async function onPolarCityChange() {
  // 城市景点热度图在「出行与预算」tab内，tab尚未渲染过时容器宽高为0，等切换时再画
  if (!renderedTabs.has('transport')) return
  await nextTick()
  renderPolarChart()
}

// 隐藏tab里的容器宽高为0，ECharts init后画不出内容且不会自动恢复；
// 因此只渲染「当前激活tab」的图表，切tab时按需补画（每张图只画一次）
const renderedTabs = new Set([tab.value])

const TAB_RENDERERS = {
  // 供需缺口页只剩总结条+榜单（纯DOM），无需画图
  transport: async () => {
    renderTransportChart()
    renderBudgetChart()
    await ensureSupply() // 城市景点热度已并入出行与预算，随本tab一起按需渲染
  },
  themes: ensureFlow // 主题页含桑基流转图，进入时按需加载渲染
}

async function onTabChange(name) {
  const render = TAB_RENDERERS[name]
  if (!render) return
  await nextTick()
  render()
  renderedTabs.add(name)
}

async function load() {
  loading.value = true
  try {
    data.value = await demandApi.insight()
    await nextTick()
    // 只画当前tab的图；其余等切换时补画
    TAB_RENDERERS[tab.value]?.()
    renderedTabs.add(tab.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  window.addEventListener('resize', onResize)
})

function onResize() {
  charts.forEach((c) => c.resize())
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
  charts = []
})
</script>

<style scoped>
.headline-card {
  border-left: 4px solid var(--xlvq-primary);
}
.headline-text {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.6;
}
.biz-kpi {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}
.biz-kpi-item {
  background: #fff;
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  padding: 14px;
  text-align: center;
}
.biz-kpi-num {
  font-size: 22px;
  font-weight: 800;
  color: var(--xlvq-primary);
}
.chart-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}
.gap-item {
  padding: 14px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.gap-summary {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px 28px;
  padding: 12px 16px;
  background: #faf6f2;
  border-radius: 8px;
  text-align: center;
  line-height: 1.9;
  font-size: 13px;
  color: #303133;
}
.gap-summary .gs-title {
  font-size: 15px;
  font-weight: 800;
}
.gap-summary .gs-line strong {
  color: #a50f15;
  font-size: 16px;
  padding: 0 2px;
}
.gap-item:last-child {
  border-bottom: none;
}
.gap-suggestion {
  margin-top: 8px;
  padding: 8px 12px;
  background: #fdf6ec;
  border-radius: 8px;
  font-size: 13px;
  color: #8a5a1b;
}
.theme-row {
  padding: 12px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.theme-row:last-child {
  border-bottom: none;
}
.demand-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.demand-table th,
.demand-table td {
  padding: 10px 8px;
  text-align: left;
  border-bottom: 1px solid var(--xlvq-border);
}
.demand-table th {
  color: var(--xlvq-text-sub);
  font-weight: 600;
}
/* 热度口径说明条：让"数据从哪来"一眼可见 */
.heat-formula {
  font-size: 12px;
  line-height: 1.7;
  color: var(--xlvq-text-sub);
  background: #f7fafd;
  border: 1px solid #e3ecf6;
  border-radius: 8px;
  padding: 8px 12px;
}
.hf-label {
  display: inline-block;
  margin-right: 8px;
  padding: 1px 8px;
  border-radius: 999px;
  background: #eaf3ff;
  color: #2f80ed;
  font-weight: 600;
}
</style>
