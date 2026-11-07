<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <div>
        <h2 class="page-title" style="margin: 0">🔎 信息浏览 · 只看不订</h2>
        <div class="sub-text" style="margin-top: 4px">
          平台定时采集车次 / 航班 / 酒店 / 门票等公开信息，结构化入库并标注来源与更新时间
        </div>
      </div>
      <div class="flex-center gap-8">
        <el-select
          v-if="activeType === 'TRAIN' || activeType === 'FLIGHT'"
          v-model="origin"
          placeholder="全部出发地"
          clearable
          style="width: 150px"
          @change="loadList"
        >
          <el-option v-for="c in origins" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="destination" placeholder="全部目的地" clearable style="width: 150px" @change="loadList">
          <el-option v-for="c in cities" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button type="primary" :icon="Download" :loading="crawling" @click="doCrawl">立即采集</el-button>
      </div>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      class="mb-16"
      title="只看不订"
      description="本页信息由平台采集公开渠道（含定时任务与手动触发），仅供浏览参考，不支持在线预订或下单。每批数据的真实来源、命中模式与更新时间见下方「采集批次日志」；如需出行请通过车站 / 航司 / 酒店 / 景区官方渠道自行预订。"
    />

    <div class="info-layout">
      <div class="x-card info-main">
        <el-tabs v-model="activeType" @tab-change="loadList">
          <el-tab-pane v-for="t in TYPES" :key="t.value" :label="t.label" :name="t.value" />
        </el-tabs>

        <el-skeleton v-if="loading" :rows="5" animated />
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
          <el-empty v-else description="暂未采集到该分类信息" :image-size="90" />
        </template>
      </div>

      <!-- 全程费用成本估算 -->
      <div class="x-card info-side">
        <div class="card-title">💰 全程费用成本估算</div>
        <div class="sub-text mb-12">
          基于采集到的交通 / 住宿 / 门票 / 餐饮参考价，输出分项区间，支持按预算档位约束
        </div>
        <el-form label-width="70px" size="default">
          <el-form-item label="出发地">
            <el-select v-model="est.origin" placeholder="不限（默认长沙）" clearable style="width: 100%">
              <el-option v-for="c in origins" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="目的地">
            <el-select v-model="est.destination" placeholder="选择目的地" style="width: 100%">
              <el-option v-for="c in cities" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="游玩天数">
            <el-input-number v-model="est.days" :min="1" :max="7" style="width: 100%" />
          </el-form-item>
          <el-form-item label="预算档位">
            <el-select v-model="est.budgetLevel" style="width: 100%">
              <el-option v-for="b in BUDGETS" :key="b.value" :label="b.label" :value="b.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="">
            <el-button type="primary" :loading="estimating" style="width: 100%" @click="doEstimate">
              生成费用估算
            </el-button>
          </el-form-item>
        </el-form>

        <template v-if="estimate">
          <el-alert
            :type="estimate.withinBudget ? 'success' : 'warning'"
            :closable="false"
            class="mb-12"
            :title="estimate.withinBudget
              ? `在「${estimate.budgetLabel}」预算内（人均约 ¥${estimate.perPersonLow} - ${estimate.perPersonHigh}）`
              : `超出「${estimate.budgetLabel}」（人均约 ¥${estimate.perPersonLow} - ${estimate.perPersonHigh}）`"
          />
          <div class="est-list">
            <div v-for="it in estimate.items" :key="it.name" class="est-item">
              <div class="est-name">{{ it.name }}</div>
              <div class="est-price">¥{{ it.low }} - {{ it.high }}</div>
              <div class="sub-text" style="font-size: 12px">{{ it.note }}</div>
            </div>
          </div>
          <div class="est-total">
            <span>人均合计</span>
            <span class="est-total-value">¥{{ estimate.totalLow }} - {{ estimate.totalHigh }}</span>
          </div>
          <div class="sub-text" style="font-size: 12px; margin-top: 8px">{{ estimate.note }}</div>
        </template>
      </div>
    </div>

    <!-- 采集批次日志：说明数据是怎么来的 -->
    <div class="x-card mt-16">
      <div class="flex-between mb-12">
        <div>
          <div class="card-title" style="margin: 0">🛠 采集批次日志</div>
          <div class="sub-text">
            每次采集（定时 / 手动 / 启动）都会留痕：数据来源、真实抓取或离线模板、抓取与入库条数、耗时
          </div>
        </div>
        <el-button size="small" :loading="batchLoading" @click="loadBatches">刷新日志</el-button>
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
              <span class="batch-stat">
                抓取{{ b.fetched }} · 新增{{ b.inserted }} · 更新{{ b.updated }} ·
                真实源{{ b.realCount }} / 模板{{ b.fallbackCount }}
              </span>
              <el-tag v-if="idx === 0" size="small" type="success" effect="light">最近</el-tag>
            </div>
          </template>
          <div class="sub-text" style="margin-bottom: 8px">{{ b.message }}</div>
          <div class="batch-detail">
            <div v-for="(line, i) in (b.detail || '').split('\n')" :key="i" class="batch-line">
              {{ line }}
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { scrapedApi } from '@/api'
import { formatTime } from '@/utils/format'

const TYPES = [
  { label: '🚄 车次', value: 'TRAIN' },
  { label: '✈️ 航班', value: 'FLIGHT' },
  { label: '🏨 酒店', value: 'HOTEL' },
  { label: '🎫 门票', value: 'TICKET' },
  { label: '🧭 目的地参考', value: 'REFERENCE' }
]
const BUDGETS = [
  { label: '不限预算', value: 'NONE' },
  { label: '1000元以内', value: 'LE1000' },
  { label: '1500元以内', value: 'LE1500' },
  { label: '2000元以内', value: 'LE2000' },
  { label: '3000元以内', value: 'LE3000' }
]

const activeType = ref('TRAIN')
const destination = ref('')
const origin = ref('')
const cities = ref([])
const origins = ref([])
const list = ref([])
const loading = ref(false)
const crawling = ref(false)

const batches = ref([])
const batchLoading = ref(false)

const est = ref({ destination: '', origin: '', days: 3, budgetLevel: 'NONE' })
const estimate = ref(null)
const estimating = ref(false)

const STATUS_TEXT = { RUNNING: '采集中', SUCCESS: '全部真实抓取', PARTIAL: '部分回落/失败', FAILED: '采集失败' }
const TRIGGER_TEXT = { SCHEDULED: '定时任务', MANUAL: '手动触发', STARTUP: '启动采集' }

function statusText(s) {
  return STATUS_TEXT[s] || s || '-'
}

function statusType(s) {
  if (s === 'SUCCESS') return 'success'
  if (s === 'PARTIAL') return 'warning'
  if (s === 'FAILED') return 'danger'
  return 'info'
}

function triggerText(t) {
  return TRIGGER_TEXT[t] || t || '-'
}

function priceText(row) {
  if (row.priceMin == null && row.priceMax == null) return '—'
  if (row.priceMin === 0 && row.priceMax === 0) return '免费'
  if (row.priceMin === row.priceMax) return `¥${row.priceMin}`
  return `¥${row.priceMin} - ${row.priceMax}`
}

async function loadList() {
  loading.value = true
  try {
    const params = { type: activeType.value }
    if (destination.value) params.destination = destination.value
    if (origin.value) params.origin = origin.value
    list.value = (await scrapedApi.list(params)) || []
  } finally {
    loading.value = false
  }
}

async function doCrawl() {
  crawling.value = true
  try {
    const b = await scrapedApi.crawl()
    const tip = b?.fallbackCount > 0
      ? `${b.message}（其中${b.fallbackCount} 个采集器使用离线模板）`
      : b?.message
    ElMessage.success(tip || '采集完成')
    await Promise.all([loadList(), loadBatches()])
  } finally {
    crawling.value = false
  }
}

async function loadBatches() {
  batchLoading.value = true
  try {
    batches.value = (await scrapedApi.crawlBatches().catch(() => [])) || []
  } finally {
    batchLoading.value = false
  }
}

async function doEstimate() {
  if (!est.value.destination) {
    ElMessage.warning('请先选择目的地')
    return
  }
  estimating.value = true
  try {
    estimate.value = await scrapedApi.costEstimate({
      destination: est.value.destination,
      origin: est.value.origin || undefined,
      days: est.value.days,
      budgetLevel: est.value.budgetLevel
    })
  } finally {
    estimating.value = false
  }
}

onMounted(async () => {
  cities.value = (await scrapedApi.cities().catch(() => [])) || []
  origins.value = (await scrapedApi.origins().catch(() => [])) || []
  await Promise.all([loadList(), loadBatches()])
})
</script>

<style scoped>
.info-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 1fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 1000px) {
  .info-layout {
    grid-template-columns: 1fr;
  }
}
.info-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--xlvq-border);
  border-radius: 12px;
  transition: box-shadow 0.15s;
}
.info-item:hover {
  box-shadow: var(--xlvq-shadow);
}
.info-route {
  min-width: 118px;
  font-weight: 700;
  color: var(--xlvq-primary);
  display: flex;
  align-items: center;
  gap: 6px;
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
  display: flex;
  align-items: center;
  gap: 8px;
}
.info-source {
  font-size: 12px;
  color: var(--xlvq-text-sub);
  margin-top: 4px;
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
  font-size: 15px;
}
.est-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.est-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.est-item:last-child {
  border-bottom: none;
}
.est-name {
  font-weight: 600;
  font-size: 13px;
}
.est-price {
  color: var(--xlvq-orange);
  font-weight: 700;
}
.est-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--xlvq-border);
  font-weight: 700;
}
.est-total-value {
  color: var(--xlvq-primary);
  font-size: 18px;
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
.batch-stat {
  color: var(--xlvq-text-sub);
}
.batch-detail {
  background: var(--xlvq-bg, #f7f8fa);
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
