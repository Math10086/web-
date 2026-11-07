<template>
  <div class="page-wrap">
    <!-- 热度榜 / 需求洞察 / 创作者中心均已并入本页，按角色展示对应标签 -->
    <!-- 商家只有「选品分析」一个业务标签，标签栏只剩一项没有意义，直接不显示 -->
    <el-tabs
      v-if="tabs.length > 1"
      v-model="activeTab"
      class="mb-16"
      @tab-change="syncTab"
    >
      <el-tab-pane v-if="showBizTabs" label="📊 数据洞察" name="insight" />
      <el-tab-pane v-if="showBizTabs" label="🔥 热度榜" name="hot" />
      <!-- 创作者中心：博主本人帖子的表现与选题建议（旧 /creator 自动跳转到 ?tab=creator） -->
      <el-tab-pane v-if="canCreate" label="✍️ 创作者中心" name="creator" />
      <!-- 商业闭环 · 需求洞察：仅商家 / 导游可见（供货方看"哪里火但没人供货"） -->
      <el-tab-pane v-if="isPartner" :label="auth.role === 'MERCHANT' ? '💼 选品分析' : '💼 需求洞察'" name="demand" />
    </el-tabs>

    <template v-if="activeTab === 'insight'">
    <!-- 顶部：公开看板说明 -->
    <div class="insight-hero mb-16">
      <div class="hero-main">
        <h1>📊 数据洞察 · 公开看板</h1>
        <p>
          平台面向公众开放的<strong>脱敏聚合</strong>数据：目的地热度与学生消费画像。<br />
          全部为统计量，不含任何用户昵称、头像、学号等可识别到个人的字段。
        </p>
      </div>
      <div class="hero-badges">
        <div class="hero-badge">
          <div class="hb-v">{{ threshold }}</div>
          <div class="hb-k">最小样本量门槛</div>
        </div>
        <div class="hero-badge">
          <div class="hb-v">{{ volumeTotal }}</div>
          <div class="hb-k">参与聚合的数据条数</div>
        </div>
      </div>
    </div>

    <el-alert
      v-if="summary.principle"
      type="info"
      :closable="false"
      class="mb-16"
      :title="summary.principle"
    />

    <!-- ① 目的地热度指数 -->
    <div class="x-card mb-16">
      <div class="flex-between mb-12" style="flex-wrap: wrap; gap: 8px">
        <div class="sec-title">🔥 目的地热度指数 · 全国热力地图</div>
        <span class="sub-text">
          热度 = 发帖数 ×{{ weights.post || 3 }} ＋ 出行队伍数 ×{{ weights.trip || 5 }} ＋ 采集热度 ×{{ weights.crawl || 1 }}
        </span>
      </div>

      <template v-if="heatItems.length">
        <div ref="heatChartEl" class="chart chart-lg" style="height: 520px"></div>
        <el-table :data="heatItems" size="small" style="margin-top: 10px">
          <el-table-column type="index" label="#" width="52" />
          <el-table-column prop="destination" label="目的地" min-width="120" />
          <el-table-column prop="postCount" label="攻略数" width="90" align="center" />
          <el-table-column prop="tripCount" label="结伴队伍" width="96" align="center" />
          <el-table-column prop="crawlCount" label="采集信息" width="96" align="center" />
          <el-table-column prop="sampleSize" label="样本量" width="90" align="center" />
          <el-table-column prop="heat" label="热度" width="90" align="center" sortable />
        </el-table>
      </template>

      <template v-else>
        <el-empty description="样本不足，暂不展示" />
        <div class="fallback-note">
          当前通过门槛的目的地为0个（共{{ heat.destinationsTotal || 0 }}个目的地，
          {{ heat.suppressedCount || 0 }}个因样本量不足{{ threshold }}被隐藏）。
          下列为<strong>演示样例数据</strong>，用于说明看板形态。
          <el-tag size="small" type="warning" effect="plain">演示样例 · 非真实统计</el-tag>
        </div>
        <div ref="heatChartEl" class="chart chart-lg" style="height: 520px"></div>
      </template>
    </div>

    <!-- ② 学生消费画像 -->
    <div class="x-card mb-16">
      <div class="sec-title mb-12">💸 学生消费画像</div>

      <div class="chart-grid">
        <div class="chart-box">
          <div class="chart-cap">消费类目占比</div>
          <div ref="categoryChartEl" class="chart"></div>
        </div>
        <div class="chart-box">
          <div class="chart-cap">人均消费区间分布</div>
          <div ref="personChartEl" class="chart"></div>
        </div>
      </div>

      <el-alert
        v-if="consumption.insufficient"
        type="warning"
        :closable="false"
        class="mt-12"
        title="真实样本量不足，以下为演示样例数据（非真实统计）"
      />

      <el-table
        v-if="categoryRows.length"
        :data="categoryRows"
        size="small"
        style="margin-top: 12px"
        :row-style="categoryRowStyle"
        :cell-style="categoryCellStyle"
      >
        <el-table-column prop="categoryLabel" label="类目" min-width="110" />
        <el-table-column prop="count" label="记录数" width="100" align="center" />
        <el-table-column label="金额" width="140" align="center">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="占比" min-width="160">
          <template #default="{ row }">
            <div class="share-bar">
              <div class="share-fill" :style="{ width: row.share + '%' }"></div>
              <span class="share-txt">{{ row.share }}%</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="sub-text mt-12">
        数据源：商城订单{{ consumption.sample?.orders ?? 0 }}笔 / 行程与个人消费
        {{ consumption.sample?.expenses ?? 0 }}条；每类聚合单元样本量不足{{ threshold }}一律不展示。
      </div>
    </div>
    </template>

    <!-- 需求洞察标签页：商家 / 导游的经营参考（独立组件，进入时按需挂载） -->
    <DemandInsight v-else-if="isDemandTab" :initial-tab="demandSub" />
    <!-- 热度榜标签页：热帖 / 热词 / 热门城市 / 博主评级 -->
    <HotCenter v-else-if="activeTab === 'hot'" />
    <!-- 创作者中心：本人帖子的表现与选题建议（独立组件，按需挂载） -->
    <CreatorCenter v-else-if="activeTab === 'creator'" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { insightApi } from '@/api'
import HotCenter from '@/views/hot/HotCenter.vue'
import CreatorCenter from '@/views/creator/CreatorCenter.vue'
import DemandInsight from '@/views/demand/DemandInsight.vue'
import { useAuthStore } from '@/store/auth'

const auth = useAuthStore()
const isPartner = computed(() => auth.role === 'MERCHANT' || auth.role === 'GUIDE')
const showBizTabs = computed(() => !isPartner.value || auth.role === 'GUIDE')
/** 创作者中心：只对内容创作者开放（博主，以及含博主能力的导游）。
 *  普通学生用户没有博主身份，看不到这个标签——想要的话先去个人中心申请成为博主。 */
const canCreate = computed(
  () => !!auth.isLoggedIn && (auth.role === 'BLOGGER' || auth.role === 'GUIDE')
)

/** 当前角色实际可见的标签，只有1个时不渲染标签栏（只剩一项没有意义） */
const tabs = computed(() => {
  const list = []
  if (showBizTabs.value) list.push('insight', 'hot')
  if (canCreate.value) list.push('creator')
  if (isPartner.value) list.push('demand')
  return list
})

// 已并入的标签：热度榜 ?tab=hot（旧/hot）、需求洞察 ?tab=demand（旧/demand）、
// 创作者中心 ?tab=creator（旧/creator）
const route = useRoute()
const router = useRouter()
const VALID_TABS = ['insight', 'hot', 'demand', 'creator', 'flow', 'supply']
// flow/supply是需求洞察内部的子标签，复用DemandInsight组件并直落对应子tab
const DEMAND_TABS = ['demand', 'flow', 'supply']
const isDemandTab = computed(() => DEMAND_TABS.includes(activeTab.value))
const demandSub = computed(() =>
  activeTab.value === 'flow' ? 'flow' : activeTab.value === 'supply' ? 'supply' : ''
)
const activeTab = ref(VALID_TABS.includes(route.query.tab) ? route.query.tab : 'insight')
// 商家没有公开看板标签，进来直接落在选品分析
if (!showBizTabs.value && !DEMAND_TABS.includes(activeTab.value)) activeTab.value = 'demand'
// 访问了「自己看不到的标签」时回落到默认标签：普通用户/游客没有需求洞察，商家没有创作者中心
if (DEMAND_TABS.includes(activeTab.value) && !isPartner.value) activeTab.value = 'insight'
if (activeTab.value === 'creator' && !canCreate.value) activeTab.value = 'insight'
function syncTab(name) {
  router.replace({ query: { ...route.query, tab: name === 'insight' ? undefined : name } })
}

/* ---------- 静态演示样例（真实样本不足时兜底展示，页面上会明确标注） ---------- */
const SAMPLE_HEAT = [
  { destination: '张家界', postCount: 26, tripCount: 14, crawlCount: 22, sampleSize: 62, heat: 170 },
  { destination: '长沙', postCount: 31, tripCount: 9, crawlCount: 25, sampleSize: 65, heat: 163 },
  { destination: '上海外滩', postCount: 28, tripCount: 12, crawlCount: 19, sampleSize: 59, heat: 155 },
  { destination: '北京故宫', postCount: 24, tripCount: 11, crawlCount: 18, sampleSize: 53, heat: 145 },
  { destination: '凤凰古城', postCount: 18, tripCount: 11, crawlCount: 12, sampleSize: 41, heat: 121 },
  { destination: '成都', postCount: 22, tripCount: 10, crawlCount: 15, sampleSize: 47, heat: 131 },
  { destination: '西安', postCount: 21, tripCount: 9, crawlCount: 14, sampleSize: 44, heat: 122 },
  { destination: '重庆', postCount: 19, tripCount: 9, crawlCount: 13, sampleSize: 41, heat: 115 },
  { destination: '杭州', postCount: 18, tripCount: 8, crawlCount: 13, sampleSize: 39, heat: 107 },
  { destination: '厦门', postCount: 17, tripCount: 8, crawlCount: 12, sampleSize: 37, heat: 103 },
  { destination: '广州', postCount: 16, tripCount: 7, crawlCount: 12, sampleSize: 35, heat: 95 },
  { destination: '武汉', postCount: 15, tripCount: 7, crawlCount: 11, sampleSize: 33, heat: 91 },
  { destination: '南岳衡山', postCount: 15, tripCount: 8, crawlCount: 10, sampleSize: 33, heat: 95 },
  { destination: '南京', postCount: 14, tripCount: 6, crawlCount: 10, sampleSize: 30, heat: 82 },
  { destination: '青岛', postCount: 13, tripCount: 6, crawlCount: 9, sampleSize: 28, heat: 78 },
  { destination: '桂林', postCount: 13, tripCount: 6, crawlCount: 9, sampleSize: 28, heat: 78 },
  { destination: '三亚', postCount: 12, tripCount: 6, crawlCount: 8, sampleSize: 26, heat: 74 },
  { destination: '武功山', postCount: 12, tripCount: 7, crawlCount: 6, sampleSize: 25, heat: 77 },
  { destination: '昆明', postCount: 12, tripCount: 5, crawlCount: 9, sampleSize: 26, heat: 70 },
  { destination: '天津五大道', postCount: 12, tripCount: 5, crawlCount: 9, sampleSize: 26, heat: 70 },
  { destination: '贵阳', postCount: 11, tripCount: 5, crawlCount: 8, sampleSize: 24, heat: 66 },
  { destination: '洛阳', postCount: 11, tripCount: 5, crawlCount: 8, sampleSize: 24, heat: 66 },
  { destination: '敦煌', postCount: 10, tripCount: 5, crawlCount: 7, sampleSize: 22, heat: 62 },
  { destination: '黄山', postCount: 10, tripCount: 5, crawlCount: 7, sampleSize: 22, heat: 62 },
  { destination: '拉萨', postCount: 10, tripCount: 4, crawlCount: 7, sampleSize: 21, heat: 57 },
  { destination: '喀纳斯', postCount: 9, tripCount: 4, crawlCount: 7, sampleSize: 20, heat: 54 },
  { destination: '平遥古城', postCount: 9, tripCount: 4, crawlCount: 6, sampleSize: 19, heat: 53 },
  { destination: '哈尔滨', postCount: 9, tripCount: 4, crawlCount: 6, sampleSize: 19, heat: 53 },
  { destination: '香港维港', postCount: 9, tripCount: 4, crawlCount: 5, sampleSize: 18, heat: 52 },
  { destination: '南昌', postCount: 8, tripCount: 4, crawlCount: 6, sampleSize: 18, heat: 50 },
  { destination: '西宁', postCount: 8, tripCount: 4, crawlCount: 6, sampleSize: 18, heat: 50 },
  { destination: '大连', postCount: 8, tripCount: 3, crawlCount: 5, sampleSize: 16, heat: 44 },
  { destination: '台湾日月潭', postCount: 8, tripCount: 3, crawlCount: 5, sampleSize: 16, heat: 44 },
  { destination: '呼和浩特', postCount: 8, tripCount: 3, crawlCount: 6, sampleSize: 17, heat: 45 },
  { destination: '银川', postCount: 8, tripCount: 3, crawlCount: 6, sampleSize: 17, heat: 45 },
  { destination: '秦皇岛', postCount: 7, tripCount: 3, crawlCount: 5, sampleSize: 15, heat: 41 },
  { destination: '长春', postCount: 7, tripCount: 3, crawlCount: 5, sampleSize: 15, heat: 41 },
  { destination: '澳门大三巴', postCount: 7, tripCount: 3, crawlCount: 4, sampleSize: 14, heat: 40 }
]
const SAMPLE_CATEGORY = [
  { categoryLabel: '住宿', amount: 3860, share: 31.2 },
  { categoryLabel: '交通', amount: 3240, share: 26.2 },
  { categoryLabel: '餐饮', amount: 2710, share: 21.9 },
  { categoryLabel: '门票', amount: 1560, share: 12.6 },
  { categoryLabel: '购物', amount: 1000, share: 8.1 }
]
const SAMPLE_PERSON = [
  { label: '300元以下', count: 41 },
  { label: '300–800元', count: 87 },
  { label: '800–1500元', count: 34 },
  { label: '1500元以上', count: 12 }
]

const summary = ref({})
const heat = ref({})
const consumption = ref({})
const heatChartEl = ref(null)
const categoryChartEl = ref(null)
const personChartEl = ref(null)
const charts = []

const threshold = computed(() => summary.value.threshold ?? 30)
const volumeTotal = computed(() => {
  const v = summary.value.volumes || {}
  return (v.posts || 0) + (v.trips || 0) + (v.crawl || 0) + (v.orders || 0) + (v.expenses || 0)
})
const weights = computed(() => heat.value.weights || {})

const heatItems = computed(() => heat.value.items || [])
/** 地图用全量达标目的地（不截断Top 10），让全国各省都尽可能着色 */
const heatMapItems = computed(() => {
  const map = heat.value.mapItems
  return map?.length ? map : heat.value.items || []
})
const categoryRows = computed(() =>
  consumption.value.categoryShare?.length ? consumption.value.categoryShare : SAMPLE_CATEGORY
)

/* ---------- 类目配色 ---------- */
/* 消费类目饼图：需要区分不同类目，保留多色 */
const CATEGORY_COLORS = {
  购物: '#2F80ED',
  住宿: '#52C41A',
  交通: '#FA8C16',
  门票: '#722ED1',
  餐饮: '#13C2C2'
}
const FALLBACK_COLORS = ['#EB2F96', '#2F80ED', '#52C41A', '#FA8C16', '#722ED1', '#13C2C2']
const fallbackMap = new Map()

function catColor(label) {
  if (CATEGORY_COLORS[label]) return CATEGORY_COLORS[label]
  // 未预置的类目按出现顺序分配备用色，保证同一类目每次颜色稳定
  if (!fallbackMap.has(label)) {
    fallbackMap.set(label, FALLBACK_COLORS[fallbackMap.size % FALLBACK_COLORS.length])
  }
  return fallbackMap.get(label)
}

/* ---------- 类目表：单一色系，只用深浅区分 ---------- */
// 与系统主色一致的绿，占比行底色都用它的深浅
const TINT_BASE = '#0F6E56'

function hexToRgba(hex, a) {
  const n = parseInt(hex.slice(1), 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`
}

function rowTint(share) {
  const max = Math.max(...categoryRows.value.map((r) => r.share || 0), 1)
  const ratio = Math.min((share || 0) / max, 1)
  // 8% → 38% 透明度区间：最深的一行仍能保证深色文字可读
  const alpha = 0.08+ 0.3 * ratio
  return hexToRgba(TINT_BASE, alpha)
}

function categoryRowStyle({ row }) {
  return { backgroundColor: rowTint(row.share) }
}

function categoryCellStyle() {
  // 单元格默认自带白底，置为透明才能透出整行底色
  return { background: 'transparent' }
}

function mount(el, option) {
  if (!el?.value) return
  // V4：同一容器重建前先释放旧实例，避免重复init后画面不刷新
  const old = echarts.getInstanceByDom(el.value)
  if (old) old.dispose()
  const chart = echarts.init(el.value)
  chart.setOption(option)
  charts.push(chart)
}

// ---- 目的地热度：中国地图省级着色（颜色越深热度越高） ----

/** 目的地 → 省级行政区映射（同省多个目的地取最高热度着色） */
const CITY_PROVINCE = {
  长沙: '湖南省', 张家界: '湖南省', 凤凰古城: '湖南省', 凤凰: '湖南省', 南岳衡山: '湖南省', 衡山: '湖南省',
  岳阳: '湖南省', 常德: '湖南省', 郴州: '湖南省', 韶山: '湖南省', 武功山: '湖南省',
  西安: '陕西省', 华山: '陕西省', 咸阳: '陕西省', 延安: '陕西省',
  成都: '四川省', 九寨沟: '四川省', 峨眉山: '四川省', 乐山: '四川省', 稻城: '四川省',
  重庆: '重庆市', 武汉: '湖北省', 宜昌: '湖北省', 恩施: '湖北省',
  广州: '广东省', 深圳: '广东省', 珠海: '广东省', 佛山: '广东省',
  厦门: '福建省', 福州: '福建省', 泉州: '福建省', 武夷山: '福建省',
  南京: '江苏省', 苏州: '江苏省', 无锡: '江苏省', 杭州: '浙江省', 宁波: '浙江省',
  桂林: '广西壮族自治区', 南宁: '广西壮族自治区', 贵阳: '贵州省', 遵义: '贵州省',
  昆明: '云南省', 丽江: '云南省', 大理: '云南省', 西双版纳: '云南省',
  三亚: '海南省', 海口: '海南省', 青岛: '山东省', 济南: '山东省', 泰山: '山东省',
  郑州: '河南省', 洛阳: '河南省', 开封: '河南省', 敦煌: '甘肃省', 兰州: '甘肃省',
  西宁: '青海省', 乌鲁木齐: '新疆维吾尔自治区', 喀纳斯: '新疆维吾尔自治区',
  拉萨: '西藏自治区', 林芝: '西藏自治区', 哈尔滨: '黑龙江省', 长春: '吉林省',
  沈阳: '辽宁省', 大连: '辽宁省', 秦皇岛: '河北省', 承德: '河北省',
  呼和浩特: '内蒙古自治区', 银川: '宁夏回族自治区', 南昌: '江西省', 九江: '江西省',
  黄山: '安徽省', 合肥: '安徽省', 太原: '山西省', 平遥: '山西省'
}
const PROVINCES = [
  '北京市', '天津市', '河北省', '山西省', '内蒙古自治区', '辽宁省', '吉林省', '黑龙江省',
  '上海市', '江苏省', '浙江省', '安徽省', '福建省', '江西省', '山东省', '河南省',
  '湖北省', '湖南省', '广东省', '广西壮族自治区', '海南省', '重庆市', '四川省', '贵州省',
  '云南省', '西藏自治区', '陕西省', '甘肃省', '青海省', '宁夏回族自治区',
  '新疆维吾尔自治区', '香港特别行政区', '澳门特别行政区', '台湾省'
]
const PROVINCE_ALIAS = {
  北京: '北京市', 天津: '天津市', 上海: '上海市', 重庆: '重庆市', 河北: '河北省',
  山西: '山西省', 内蒙古: '内蒙古自治区', 辽宁: '辽宁省', 吉林: '吉林省',
  黑龙江: '黑龙江省', 江苏: '江苏省', 浙江: '浙江省', 安徽: '安徽省', 福建: '福建省',
  江西: '江西省', 山东: '山东省', 河南: '河南省', 湖北: '湖北省', 湖南: '湖南省',
  广东: '广东省', 广西: '广西壮族自治区', 海南: '海南省', 四川: '四川省',
  贵州: '贵州省', 云南: '云南省', 西藏: '西藏自治区', 陕西: '陕西省', 甘肃: '甘肃省',
  青海: '青海省', 宁夏: '宁夏回族自治区', 新疆: '新疆维吾尔自治区',
  香港: '香港特别行政区', 澳门: '澳门特别行政区', 台湾: '台湾省'
}

function provinceOf(dest) {
  if (!dest) return null
  const d = String(dest)
  if (CITY_PROVINCE[d]) return CITY_PROVINCE[d]
  for (const key of Object.keys(CITY_PROVINCE)) {
    if (d.includes(key)) return CITY_PROVINCE[key]
  }
  for (const alias of Object.keys(PROVINCE_ALIAS)) {
    if (d.includes(alias)) return PROVINCE_ALIAS[alias]
  }
  return null
}

import chinaGeo from '@/assets/china.json'

// 标准中国省级行政边界（阿里DataV GeoAtlas，符合国家标准图廓）
echarts.registerMap('china', chinaGeo)

let chinaMapReady = null
async function ensureChinaMap() {
  return true // 模块加载时已注册
}

async function renderHeat() {
  const source = heatMapItems.value.length ? heatMapItems.value : SAMPLE_HEAT
  // 地图边界加载失败时回退为旧横向条形图，保证看板永远有图
  try {
    await ensureChinaMap()
  } catch (e) {
    renderHeatBar(source)
    return
  }

  // 省级聚合：同省多个目的地取最高热度，并记录明细供tooltip展示
  const byProvince = new Map()
  for (const d of source) {
    const prov = provinceOf(d.destination)
    if (!prov) continue
    const cur = byProvince.get(prov)
    if (!cur || d.heat > cur.heat) {
      byProvince.set(prov, { heat: d.heat, dests: [d.destination, ...(cur?.dests || [])].filter((v, i, a) => a.indexOf(v) === i) })
    } else {
      cur.dests = [...new Set([...cur.dests, d.destination])]
    }
  }
  const maxHeat = Math.max(...source.map((d) => d.heat), 1)

  mount(heatChartEl, {
    tooltip: {
      trigger: 'item',
      formatter: (p) => {
        const d = byProvince.get(p.name)
        if (!d) return `${p.name}<br/>暂无足够样本`
        return `${p.name}<br/>最高热度 <b>${d.heat}</b><br/>热门目的地：${d.dests.join('、')}`
      }
    },
    visualMap: {
      min: 0,
      max: maxHeat,
      left: 12,
      bottom: 12,
      calculable: false,
      text: ['热度高', '热度低'],
      inRange: { color: ['#FFF6E8', '#FFD9A0', '#FFA94D', '#F76707', '#D9480F'] }
    },
    series: [
      {
        type: 'map',
        map: 'china',
        roam: true,
        scaleLimit: { min: 0.7, max: 3 },
        zoom: 1.15,
        top: 12,
        bottom: 24,
        data: [...byProvince.entries()].map(([name, d]) => ({ name, value: d.heat, dests: d.dests })),
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 12, fontWeight: 700, color: '#303133' },
          itemStyle: { areaColor: '#FFD9A0', shadowBlur: 10, shadowColor: 'rgba(0,0,0,.2)' }
        },
        itemStyle: {
          areaColor: '#f2f4f7',
          borderColor: '#fff',
          borderWidth: 1
        }
      }
    ]
  })
}

/** 兜底：旧横向渐变条形图（地图边界加载失败时使用） */
function renderHeatBar(source) {
  const sorted = [...source].sort((a, b) => a.heat - b.heat)
  mount(heatChartEl, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 48, top: 16, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: sorted.map((d) => d.destination) },
    series: [
      {
        type: 'bar',
        data: sorted.map((d) => d.heat),
        // 横向条形：宽度按类目槽位的45% 自适应，间距比原先写死16px时更紧凑
        barWidth: '45%',
        barMaxWidth: 26,
        label: { show: true, position: 'right', fontWeight: 700, color: '#A32D2D' },
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: (p) =>
            new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#f7c6a0' },
              { offset: 1, color: p.dataIndex >= sorted.length - 1 ? '#D9480F' : '#F08C3A' }
            ])
        }
      }
    ]
  })
}

function renderCategory() {
  const rows = categoryRows.value
  // 占比最大的扇区对齐12点钟方向，其余按占比降序顺时针排列
  // （ECharts默认startAngle=90从正上方顺时针，因此只需保证数据降序）
  const sorted = [...rows].sort((a, b) => (b.amount || 0) - (a.amount || 0))
  mount(categoryChartEl, {
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 元（{d}%）' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '44%'],
        startAngle: 90, // 12点钟方向起始
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%' },
        data: sorted.map((r) => ({
          name: r.categoryLabel,
          value: r.amount,
          itemStyle: { color: catColor(r.categoryLabel) }
        })),
        color: ['#2F80ED', '#52C41A', '#FA8C16', '#722ED1', '#13C2C2', '#EB2F96']
      }
    ]
  })
}

function renderPerson() {
  const live = consumption.value.perPersonDistribution || []
  const rows = live.length ? live : SAMPLE_PERSON
  mount(personChartEl, {
    tooltip: { trigger: 'axis' },
    // 直方图：柱间距收紧，渐变加阴影增强立体感
    grid: { left: 40, right: 24, top: 24, bottom: 34 },
    xAxis: { type: 'category', data: rows.map((r) => r.label), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: rows.map((r) => r.count),
        barCategoryGap: '18%', // 收紧类目间距，凸显"分桶"直方图形态
        barWidth: '62%',
        barMaxWidth: 72,
        label: { show: true, position: 'top', fontWeight: 700, color: '#0F6E56' },
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#5BD1A8' },
            { offset: 1, color: '#0F6E56' }
          ]),
          shadowBlur: 8,
          shadowOffsetY: 4,
          shadowColor: 'rgba(15,110,86,.28)'
        }
      }
    ]
  })
}

function onResize() {
  charts.forEach((c) => c.resize())
}

async function loadAll() {
  // 商家没有公开看板（数据洞察/热度榜两个标签对它隐藏），这3个接口后端会直接403，
  // 请求了也用不上、还会弹一串红字，故按角色跳过
  if (!showBizTabs.value) return
  const [s, h, c] = await Promise.allSettled([
    insightApi.summary(),
    insightApi.destinationHeat(),
    insightApi.consumptionProfile()
  ])
  summary.value = s.status === 'fulfilled' ? s.value || {} : {}
  heat.value = h.status === 'fulfilled' ? h.value || {} : {}
  consumption.value = c.status === 'fulfilled' ? c.value || {} : {}
  await nextTick()
  renderHeat()
  renderCategory()
  renderPerson()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})

// V4：看板内容是v-if条件渲染——从其它标签页（含深链 ?tab=demand）切回「数据洞察」
// 时容器才第一次出现，必须补渲染一次，否则看板永远是空白
watch(activeTab, async (t) => {
  if (t !== 'insight') return
  await nextTick()
  renderHeat()
  renderCategory()
  renderPerson()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.insight-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding: 24px 30px;
  border-radius: 14px;
  background: linear-gradient(120deg, #eef6ff 0%, #f4fbf7 55%, #fff7ef 100%);
  border: 1px solid #e6eef8;
}
.hero-main h1 {
  font-size: 24px;
  margin: 0 0 8px;
}
.hero-main p {
  margin: 0;
  color: #5a6472;
  line-height: 1.7;
}
.hero-badges {
  display: flex;
  gap: 14px;
}
.hero-badge {
  min-width: 120px;
  text-align: center;
  background: #fff;
  border: 1px solid #e6eef8;
  border-radius: 12px;
  padding: 12px 16px;
}
.hb-v {
  font-size: 26px;
  font-weight: 800;
  color: #185fa5;
}
.hb-k {
  font-size: 12px;
  color: #8a94a6;
  margin-top: 2px;
}
.sec-title {
  font-size: 17px;
  font-weight: 700;
}
.chart {
  width: 100%;
  height: 280px;
}
.chart-lg {
  height: 360px;
}
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.chart-box {
  border: 1px solid var(--xlvq-border, #ebeef5);
  border-radius: 12px;
  padding: 12px 14px 4px;
}
.chart-cap {
  font-size: 13px;
  font-weight: 600;
  color: #5a6472;
  margin-bottom: 4px;
}
.fallback-note {
  font-size: 13px;
  color: #6b4a12;
  background: #fdf6ee;
  border: 1px solid #f0c9a0;
  border-radius: 8px;
  padding: 10px 14px;
  margin: 8px 0 12px;
  line-height: 1.7;
}
.share-bar {
  position: relative;
  height: 16px;
  background: #f1f3f5;
  border-radius: 8px;
  overflow: hidden;
}
.share-fill {
  height: 100%;
  background: linear-gradient(90deg, #5bd1a8, #0f6e56);
  border-radius: 8px;
}
.share-txt {
  position: absolute;
  right: 8px;
  top: 0;
  font-size: 11px;
  line-height: 16px;
  color: #33475b;
}
@media (max-width: 860px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
