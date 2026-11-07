<template>
  <div class="transport-stack">
    <!-- ① 出行方式比较：两城之间所有方式的耗时 / 票价 / 体验 -->
    <div class="x-card">
      <div class="card-title">出行方式比较</div>
      <div class="flex gap-8 mb-12" style="align-items: center">
        <el-select
          v-model="origin"
          size="small"
          placeholder="出发地（可输入任意城市）"
          filterable
          allow-create
          default-first-option
          style="flex: 1"
        >
          <el-option v-for="c in origins" :key="'to-' + c" :label="c" :value="c" />
        </el-select>
        <span class="sub-text" style="flex: none">→</span>
        <el-select
          v-model="destination"
          size="small"
          placeholder="目的地（可输入任意城市）"
          filterable
          allow-create
          default-first-option
          style="flex: 1"
        >
          <el-option v-for="c in destOptions" :key="'td-' + c" :label="c" :value="c" />
        </el-select>
      </div>
      <div class="flex gap-8 mb-12">
        <el-button size="small" type="primary" style="flex: 1" :loading="comparing" @click="loadTransport">
          比较方案
        </el-button>
        <el-button size="small" @click="swapTransport">⇄ 互换</el-button>
      </div>
      <div v-if="transports.length" class="sub-text mb-12" style="line-height: 1.8">
        {{ origin }} → {{ destination }} · 共{{ transports.length }}种方式
        <template v-if="fastestRow">
          ｜最快{{ TRANSPORT_MODE_MAP[fastestRow.mode] || fastestRow.mode }}
          {{ minutesToText(fastestRow.duration) }}
        </template>
        <template v-if="cheapestRow">｜最省 ¥{{ cheapestRow.price }}</template>
      </div>
      <!-- 里程信息条：计算类方式（汽车 / 大巴 / 拼车）的依据 -->
      <div v-if="routeInfo" class="route-bar mb-12">
        <span>🛣 公路里程 <b>{{ routeInfo.distanceKm }}</b> km</span>
        <span>⏱ 驾车{{ minutesToText(routeInfo.driveMinutes) }}</span>
        <span>💰 过路费约 <b>{{ routeInfo.toll }}</b> 元</span>
        <el-tag size="small" :type="routeInfo.source === '腾讯地图计算' ? 'success' : 'info'" effect="plain">
          {{ routeInfo.source }}
        </el-tag>
      </div>
      <el-table
        :data="transports"
        size="small"
        border
        max-height="320"
        empty-text="选择出发地和目的地后点击比较"
        :row-class-name="rowClassName"
        @expand-change="onExpand"
      >
        <!-- 展开行：① 火车 / 航班看采集明细 ② 其余方式看计算依据 -->
        <el-table-column type="expand" width="40">
          <template #default="{ row }">
            <div class="row-expand">
              <template v-if="scrapedTypeOf(row.mode)">
                <div class="expand-head">
                  <span class="expand-title">🛰 {{ scrapedLabel(row.mode) }}明细</span>
                  <span class="sub-text">{{ curRoute.from }} → {{ curRoute.to }}</span>
                  <el-button size="small" text type="primary" :loading="live.loading" @click="loadLive(row)">
                    刷新采集
                  </el-button>
                </div>
                <el-skeleton v-if="live.loading" :rows="2" animated />
                <div v-else-if="detailRows(row).length" class="info-list">
                  <div v-for="(s, i) in detailRows(row)" :key="i" class="info-item">
                    <div class="info-route">
                      <span class="city">{{ s.origin }}</span>
                      <span class="arrow">→</span>
                      <span class="city">{{ s.destination }}</span>
                    </div>
                    <div class="info-body">
                      <div class="info-name">
                        {{ s.name }}
                        <el-tag v-if="isRealSource(s.source)" size="small" type="success" effect="plain">
                          真实抓取
                        </el-tag>
                        <el-tag v-else size="small" type="warning" effect="plain">离线模板</el-tag>
                      </div>
                      <div class="sub-text">
                        <span v-if="s.schedule">⏱ {{ s.schedule }}</span>
                        <span v-if="s.note"> · {{ s.note }}</span>
                      </div>
                      <div class="info-source">
                        来源：{{ s.source || '公开渠道' }}
                        <template v-if="s.updatedAt"> · 更新于{{ formatTime(s.updatedAt) }}</template>
                      </div>
                    </div>
                    <div class="info-price">
                      <div class="price-value">{{ priceText(s) }}</div>
                      <el-tag size="small" type="info" effect="plain">不可预订</el-tag>
                    </div>
                  </div>
                </div>
                <div v-else class="expand-empty">
                  暂无「{{ curRoute.from }} → {{ curRoute.to }}」的{{ scrapedLabel(row.mode) }}明细。
                  可点「刷新采集」重新抓取；当前已采集的出发地：{{ infoOrigins.join(' / ') || '—' }}
                  <div class="mt-12">
                    <el-button size="small" type="primary" plain :loading="crawling" @click="crawlAndRefresh(row)">
                      立即采集
                    </el-button>
                  </div>
                </div>
              </template>
              <div v-else class="expand-empty">
                <b>{{ TRANSPORT_MODE_MAP[row.mode] || row.mode }}的计算依据</b><br />
                {{ row.note || '该方式暂无公开采集源，表中为平台整理的基准数据。' }}
                <div v-if="row.source" class="mt-12">
                  <el-tag size="small" effect="plain">{{ row.source }}</el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="方式" prop="mode" width="92">
          <template #default="{ row }">{{ TRANSPORT_MODE_MAP[row.mode] || row.mode }}</template>
        </el-table-column>
        <el-table-column label="时长" width="80">
          <template #default="{ row }">{{ minutesToText(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="票价" width="78">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="来源" width="106">
          <template #default="{ row }">
            <el-tag v-if="row.source" size="small" :type="sourceTagType(row.source)" effect="plain">
              {{ row.source }}
            </el-tag>
            <span v-else class="sub-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="班次" prop="frequency" width="80" />
        <el-table-column label="舒适" width="86">
          <template #default="{ row }">
            <el-rate :model-value="Number(row.comfort) || 0" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="自由" width="86">
          <template #default="{ row }">
            <el-rate :model-value="Number(row.freedom) || 0" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="安全" width="86">
          <template #default="{ row }">
            <el-rate :model-value="Number(row.safety) || 0" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="note" min-width="120" show-overflow-tooltip />
      </el-table>
      <div class="sub-text mt-12" style="line-height: 1.8">
        💡 任意两座城市都能比：库内没有基准数据时，火车走12306采集、飞机按航段里程估算，
        汽车 / 大巴 / 拼车由腾讯地图的真实里程与过路费算出（每行的「来源」列标明数据出处）。
      </div>
      <div v-if="planNotices.length" class="plan-notice mt-12">
        <div v-for="(n, i) in planNotices" :key="i">· {{ n }}</div>
      </div>
    </div>

    <!-- ② 公开信息浏览（只看不订）：车次 / 航班 / 酒店 / 门票 -->
    <div class="x-card">
      <div class="flex-between mb-12">
        <div class="card-title" style="margin: 0">公开信息浏览</div>
        <div class="flex-center gap-8">
          <el-button size="small" text @click="openLog">采集日志</el-button>
          <el-button size="small" type="primary" :icon="Download" :loading="crawling" @click="doCrawl">
            立即采集
          </el-button>
        </div>
      </div>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="mb-12"
        title="只看不订"
        description="平台采集车次 / 航班 / 酒店 / 门票等公开信息并标注来源与更新时间（含定时任务与手动触发），仅供浏览参考，不支持在线预订或下单；每批数据来源见「采集日志」。"
      />

      <div class="flex gap-8 mb-12" style="align-items: center">
        <el-select
          v-if="activeType === 'TRAIN' || activeType === 'FLIGHT'"
          v-model="infoOrigin"
          placeholder="出发地（可输入任意城市）"
          clearable
          filterable
          allow-create
          default-first-option
          size="small"
          style="flex: 1"
          @change="loadInfo"
        >
          <el-option v-for="c in infoOrigins" :key="'io-' + c" :label="c" :value="c" />
        </el-select>
        <el-select
          v-model="infoDest"
          placeholder="目的地（可输入任意城市）"
          clearable
          filterable
          allow-create
          default-first-option
          size="small"
          style="flex: 1"
          @change="loadInfo"
        >
          <el-option v-for="c in infoDestOptions" :key="'id-' + c" :label="c" :value="c" />
        </el-select>
      </div>

      <el-tabs v-model="activeType" @tab-change="loadInfo">
        <el-tab-pane v-for="t in TYPES" :key="t.value" :label="t.label" :name="t.value" />
      </el-tabs>

      <el-skeleton v-if="infoLoading" :rows="4" animated />
      <template v-else>
        <div v-if="list.length" class="info-list">
          <div v-for="row in list" :key="row.id" class="info-item">
            <div class="info-route">
              <template v-if="activeType === 'TRAIN' || activeType === 'FLIGHT'">
                <span class="city">{{ row.origin }}</span>
                <span class="arrow">→</span>
                <span class="city">{{ row.destination }}</span>
              </template>
              <template v-else>
                <span class="city">{{ row.destination }}</span>
              </template>
            </div>
            <div class="info-body">
              <div class="info-name">
                {{ row.name }}
                <el-tag v-if="row.rating" size="small" type="success" effect="plain">
                  {{ row.rating }}分
                </el-tag>
              </div>
              <div class="sub-text">
                <span v-if="row.schedule">⏱ {{ row.schedule }}</span>
                <span v-if="row.note"> · {{ row.note }}</span>
              </div>
              <div class="info-source">
                来源：{{ row.source || '公开渠道' }} · 更新于{{ formatTime(row.updatedAt) }}
              </div>
            </div>
            <div class="info-price">
              <div class="price-value">{{ priceText(row) }}</div>
              <el-tag size="small" type="info" effect="plain">
                {{ activeType === 'REFERENCE' ? '公开数据' : '不可预订' }}
              </el-tag>
            </div>
          </div>
        </div>
        <el-empty
          v-else
          :description="emptyText"
          :image-size="80"
        >
          <el-button v-if="infoOrigin || infoDest" size="small" type="primary" plain :loading="crawling" @click="doCrawl">
            立即采集
          </el-button>
        </el-empty>
      </template>
    </div>

    <!-- 采集批次日志：数据是怎么来的（定时 / 手动 / 启动 + 真实抓取或离线模板） -->
    <el-dialog v-model="logVisible" title="采集批次日志" width="680px" top="8vh">
      <div class="sub-text mb-12">
        每次采集都会留痕：触发方式、每个采集器的数据来源、命中模式（真实抓取 / 离线模板）、抓取与入库条数、耗时
      </div>
      <div class="flex-between mb-12">
        <span class="sub-text">共{{ batches.length }}条记录</span>
        <el-button size="small" :icon="RefreshRight" :loading="logLoading" @click="loadBatches">刷新</el-button>
      </div>
      <el-empty v-if="!batches.length" description="暂无采集记录" :image-size="70" />
      <el-collapse v-else accordion>
        <el-collapse-item v-for="(b, idx) in batches" :key="b.id" :name="b.id">
          <template #title>
            <div class="batch-title">
              <el-tag size="small" :type="statusType(b.status)">{{ statusText(b.status) }}</el-tag>
              <span class="batch-no">{{ b.batchNo }}</span>
              <el-tag size="small" effect="plain">{{ triggerText(b.triggerType) }}</el-tag>
              <span class="sub-text">{{ formatTime(b.startedAt) }} · {{ b.durationMs }}ms</span>
              <el-tag v-if="idx === 0" size="small" type="success" effect="light">最近</el-tag>
            </div>
          </template>
          <div class="sub-text" style="margin-bottom: 6px">
            抓取{{ b.fetched }} · 新增{{ b.inserted }} · 更新{{ b.updated }} ·
            真实源{{ b.realCount }} / 离线模板{{ b.fallbackCount }} · 失败{{ b.failed }}
          </div>
          <div class="batch-detail">
            <div v-for="(line, i) in (b.detail || '').split('\n')" :key="i" class="batch-line">{{ line }}</div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, RefreshRight } from '@element-plus/icons-vue'
import { scrapedApi, transportApi } from '@/api'
import { TRANSPORT_MODE_MAP, formatTime, minutesToText } from '@/utils/format'
import { useTripPlan } from '@/composables/useTripPlan'

const plan = useTripPlan()

const TYPES = [
  { label: '🚄 车次', value: 'TRAIN' },
  { label: '✈️ 航班', value: 'FLIGHT' },
  { label: '🏨 酒店', value: 'HOTEL' },
  { label: '🎫 门票', value: 'TICKET' },
  { label: '🧭 目的地参考', value: 'REFERENCE' }
]

/* ===== 出行方式比较 ===== */
const origin = ref('长沙')
const destination = ref(plan.city)
const origins = ref(['长沙', '张家界', '武汉', '广州', '重庆', '成都', '西安'])
const destinations = ref(['张家界', '武汉', '广州', '重庆', '成都', '西安', '长沙'])
const transports = ref([])
const comparing = ref(false)
/** /transport/plan的返回：线路信息（里程/过路费）、火车明细、来源说明 */
const planInfo = ref(null)
const planDetails = ref([])
const planNotices = ref([])
const routeInfo = computed(() => planInfo.value?.route || null)

// 目的地下拉排除当前出发地
const destOptions = computed(() => destinations.value.filter((c) => c !== origin.value))
// 票价字段可能带后缀（如 "320(均摊)"），先抠数字再比，否则Number(...)为NaN，"最省"会选错
const priceNum = (p) => {
  const m = String(p ?? '').match(/\d+(?:\.\d+)?/)
  return m ? Number(m[0]) : Number.POSITIVE_INFINITY
}
const fastestRow = computed(() => {
  if (!transports.value.length) return null
  return transports.value.reduce((a, b) => (Number(a.duration) <= Number(b.duration) ? a : b))
})
const cheapestRow = computed(() => {
  if (!transports.value.length) return null
  return transports.value.reduce((a, b) => (priceNum(a.price) <= priceNum(b.price) ? a : b))
})

async function loadTransportCities() {
  const data = await transportApi.cities().catch(() => null)
  if (!data) return
  if (data.origins?.length) origins.value = data.origins
  if (data.destinations?.length) destinations.value = data.destinations
}

async function loadTransport() {
  const from = (origin.value || '').trim()
  const to = (destination.value || '').trim()
  if (!from || !to) {
    ElMessage.warning('请选择出发地和目的地')
    return
  }
  if (from === to) {
    transports.value = []
    ElMessage.info('出发地和目的地相同，换个目的地试试')
    return
  }
  // 记录本次比较的线路，展开行里的采集数据必须按这条线路取，避免下拉改了一半就对不上
  curRoute.from = from
  curRoute.to = to
  live.list = []
  comparing.value = true
  try {
    // ①走「任意城市方案」接口：库内基准优先，缺失方式由12306采集+腾讯里程计算补足
    const plan = await transportApi.plan(from, to).catch(() => null)
    if (plan?.options?.length) {
      planInfo.value = plan
      planDetails.value = plan.details || []
      planNotices.value = plan.notices || []
      transports.value = plan.options
    } else {
      // ②后端还是旧版本（没有/transport/plan）时退回「只查库」的老逻辑
      planInfo.value = null
      planDetails.value = []
      planNotices.value = []
      const data = await transportApi.list(from, to).catch(() => null)
      const list = data?.list || data || []
      transports.value = [...list].sort((a, b) => Number(a.duration) - Number(b.duration))
    }
  } finally {
    comparing.value = false
  }
  if (!transports.value.length) {
    // 给出该出发地已有的目的地，避免用户对着空表格猜
    const all = await transportApi.list(from).catch(() => null)
    const reachable = [...new Set((all?.list || all || []).map((r) => r.destination))]
    ElMessage.info(
      reachable.length
        ? `暂无「${from} → ${to}」的方案；从${from}出发可选：${reachable.join(' / ')}`
        : `暂无「${from}」出发的方案，先试试 长沙 / 张家界 / 重庆 / 成都 / 西安`
    )
  }
}

/* ===== 展开行：出行方式比较与采集到的明细 =====
 * 比较表来自数据库transport_option；车次/航班来自采集器入库的scraped_info。
 * 两条链路各有一份，这里在火车/航班行上做联动，方便对照基准价与实时班次。
 */
const curRoute = reactive({ from: '', to: '' })
const live = reactive({ loading: false, list: [] })

// 出行方式 → 采集数据类型（大巴/自驾/拼车/骑行没有公开采集源）
const SCRAPED_TYPE_MAP = { TRAIN: 'TRAIN', FLIGHT: 'FLIGHT' }
const scrapedTypeOf = (mode) => SCRAPED_TYPE_MAP[String(mode || '').toUpperCase()] || ''
const scrapedLabel = (mode) => (scrapedTypeOf(mode) === 'FLIGHT' ? '航班' : '车次')
// 采集批次日志里，真实抓取与离线模板的来源文案不同，据此打标签
const isRealSource = (s) => !!s && !String(s).includes('离线模板')

/** 数据来源 → 标签颜色：实时/真实计算偏绿，估算偏黄，库内基准中性 */
function sourceTagType(source) {
  const s = String(source || '')
  if (s.includes('实时') || s.includes('腾讯地图')) return 'success'
  if (s.includes('估算')) return 'warning'
  return 'info'
}

/** 火车/航班行的明细：优先用本次方案接口抓到的车次，否则用采集库里的数据 */
function detailRows(row) {
  const type = scrapedTypeOf(row.mode)
  if (!type) return []
  if (type === 'TRAIN' && planDetails.value.length) {
    return planDetails.value.map((d) => ({
      origin: d.from,
      destination: d.to,
      name: d.code + ' 次',
      schedule: d.depart + '-' + d.arrive,
      note: '历时' + d.duration,
      source: d.source
    }))
  }
  return live.list
}

const rowClassName = ({ row }) => (scrapedTypeOf(row.mode) ? 'row-live' : '')

async function loadLive(row) {
  const type = scrapedTypeOf(row.mode)
  if (!type) return
  live.loading = true
  try {
    live.list =
      (await scrapedApi
        .list({ type, origin: curRoute.from, destination: curRoute.to })
        .catch(() => [])) || []
  } finally {
    live.loading = false
  }
}

function onExpand(row, expanded) {
  const opened = Array.isArray(expanded) ? expanded.includes(row) : !!expanded
  if (opened && scrapedTypeOf(row.mode)) loadLive(row)
}

async function crawlAndRefresh(row) {
  try {
    await doCrawl()
  } catch (e) {
    /* 权限不足等情况由请求拦截器统一提示 */
  }
  await loadLive(row)
}

function swapTransport() {
  const o = origin.value
  origin.value = destination.value
  destination.value = o
  loadTransport()
}

/**
 * 目的地选项里已排除出发地；但默认目的地取自「规划城市」，若它正好等于出发地，
 * 下拉会显示一个不在选项里的同城值，点比较只会得到空表。这里把默认值落到第一个可选目的地。
 * 只处理默认值，用户手动选同城时仍按原逻辑提示，不悄悄改用户的选择。
 */
function normalizeDestination() {
  const opts = destOptions.value
  if (!opts.length || opts.includes(destination.value)) return
  destination.value = opts[0]
}

/* ===== 公开信息浏览（只看不订） ===== */
const activeType = ref('TRAIN')
const infoDest = ref(plan.city)
const infoOrigin = ref('')
const infoOrigins = ref([])
const list = ref([])
const infoLoading = ref(false)
const crawling = ref(false)

// 目的地候选=行程规划目的地+交通库城市+已采集出发地+当前手输值，去重
// 配合下拉的allow-create，用户可以直接输入任意城市，不局限于这几个来源
const infoDestOptions = computed(() => {
  const set = new Set()
  ;(plan.infoDestinations || []).forEach((c) => set.add(c))
  ;(destinations.value || []).forEach((c) => set.add(c))
  ;(infoOrigins.value || []).forEach((c) => set.add(c))
  if (infoDest.value) set.add(infoDest.value)
  set.delete(infoOrigin.value) // 目的地里排除当前出发地
  return [...set]
})

const batches = ref([])
const logVisible = ref(false)
const logLoading = ref(false)

const STATUS_TEXT = { RUNNING: '采集中', SUCCESS: '全部真实抓取', PARTIAL: '部分回落/失败', FAILED: '采集失败' }
const TRIGGER_TEXT = { SCHEDULED: '定时任务', MANUAL: '手动触发', STARTUP: '启动采集' }

const statusText = (s) => STATUS_TEXT[s] || s || '-'
const triggerText = (t) => TRIGGER_TEXT[t] || t || '-'

function statusType(s) {
  if (s === 'SUCCESS') return 'success'
  if (s === 'PARTIAL') return 'warning'
  if (s === 'FAILED') return 'danger'
  return 'info'
}

function priceText(row) {
  if (row.priceMin == null && row.priceMax == null) return '—'
  if (row.priceMin === 0 && row.priceMax === 0) return '免费'
  if (row.priceMin === row.priceMax) return `¥${row.priceMin}`
  return `¥${row.priceMin} - ${row.priceMax}`
}

const emptyText = computed(() => {
  if (infoOrigin.value && infoDest.value) return `暂未采集到「${infoOrigin.value} → ${infoDest.value}」的信息，可点下方按钮立即采集`
  if (infoDest.value) return `暂未采集到「${infoDest.value}」相关的信息，可点下方按钮立即采集`
  return '暂未采集到该分类信息'
})

async function loadInfo() {
  infoLoading.value = true
  try {
    const params = { type: activeType.value }
    if (infoDest.value) params.destination = infoDest.value
    if (infoOrigin.value) params.origin = infoOrigin.value
    list.value = (await scrapedApi.list(params)) || []
  } finally {
    infoLoading.value = false
  }
}

async function doCrawl() {
  crawling.value = true
  try {
    // 选了出发地/目的地就做定向采集（只抓这条线路，不局限于内置城市池）；
    // 否则退回全量采集
    const from = (infoOrigin.value || '').trim()
    const to = (infoDest.value || '').trim()
    let b
    if (to && from && from !== to) {
      b = await scrapedApi.crawlRoute(from, to)
      // 让左侧「行程规划」的目的地与这里保持一致，避免两处城市对不上
      if (to !== plan.city) plan.city = to
    } else if (to) {
      b = await scrapedApi.crawl()
    } else {
      ElMessage.warning('请先选择出发地和目的地')
      return
    }
    const tip = b?.fallbackCount > 0 && b?.fetched === 0
      ? b?.message || '该线路暂无公开数据，可达城市见左侧规划城市'
      : b?.message
    ElMessage.success(tip || '采集完成')
    await loadInfo()
    if (logVisible.value) await loadBatches()
  } finally {
    crawling.value = false
  }
}

async function loadBatches() {
  logLoading.value = true
  try {
    batches.value = (await scrapedApi.crawlBatches().catch(() => [])) || []
  } finally {
    logLoading.value = false
  }
}

function openLog() {
  logVisible.value = true
  loadBatches()
}

// 规划条件里的目的地变了，这里的两处默认值跟随（同一份条件，不必再填一遍）
watch(
  () => plan.city,
  (c) => {
    destination.value = c
    normalizeDestination()
    infoDest.value = c
    loadInfo()
  }
)

onMounted(async () => {
  await loadTransportCities()
  normalizeDestination()
  loadTransport()
  infoOrigins.value = (await scrapedApi.origins().catch(() => [])) || []
  loadInfo()
})
</script>

<style scoped>
.transport-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.info-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
/* 展开行：与采集数据联动 */
.row-expand {
  padding: 4px 8px 8px 8px;
  background: var(--xlvq-bg);
  border-radius: 10px;
}
/* 里程信息条：计算类方式的依据（里程/驾车时长/过路费） */
.route-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--xlvq-text-sub);
  background: var(--xlvq-primary-light);
  border-radius: 10px;
  padding: 8px 10px;
}
.route-bar b {
  color: var(--xlvq-primary);
  font-size: 13.5px;
}
/* 方案提示：抓取降级、航班估算口径等说明 */
.plan-notice {
  font-size: 12px;
  line-height: 1.9;
  color: var(--xlvq-text-sub);
  background: var(--xlvq-bg);
  border-radius: 10px;
  padding: 8px 10px;
}
.expand-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.expand-title {
  font-weight: 700;
  font-size: 12.5px;
  color: var(--xlvq-primary);
}
.expand-empty {
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--xlvq-text-sub);
  padding: 6px 4px;
}
/* 有采集数据可展开的行给点提示色，避免用户不知道左边箭头能点 */
:deep(.row-live) .el-table__expand-icon {
  color: var(--xlvq-primary);
}
:deep(.row-live) td:first-child {
  background: var(--xlvq-primary-light);
}
.info-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--xlvq-border);
  border-radius: 12px;
  transition: box-shadow 0.15s;
}
.info-item:hover {
  box-shadow: var(--xlvq-shadow);
}
.info-route {
  min-width: 96px;
  font-weight: 700;
  font-size: 13px;
  color: var(--xlvq-primary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.info-route .arrow {
  color: var(--xlvq-text-sub);
  font-weight: 400;
}
.info-route .city {
  white-space: nowrap;
}
.info-body {
  flex: 1;
  min-width: 0;
}
.info-name {
  font-weight: 600;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.info-source {
  font-size: 11.5px;
  color: var(--xlvq-text-sub);
  margin-top: 2px;
}
.info-price {
  text-align: right;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.price-value {
  font-weight: 700;
  color: var(--xlvq-orange);
  font-size: 14px;
}
.batch-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}
.batch-no {
  font-weight: 600;
  font-family: Consolas, Monaco, monospace;
}
.batch-detail {
  background: var(--xlvq-bg, #f5f7fb);
  border-radius: 8px;
  padding: 10px 12px;
}
.batch-line {
  font-size: 12px;
  line-height: 1.9;
  font-family: Consolas, Monaco, monospace;
  color: var(--xlvq-text-sub);
  word-break: break-all;
}
</style>
