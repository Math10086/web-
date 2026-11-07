<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <div>
        <h2 class="page-title" style="margin: 0">🤖 AI 行程助手 · 规划 / 天气 / 动态调整</h2>
        <div class="sub-text" style="margin-top: 4px">
          输入目的地与偏好生成每日行程，接入目的地天气，遇不利天气可一键生成调整方案并同步通知同行成员
        </div>
      </div>
      <el-button v-if="result" :icon="MapLocation" @click="router.push('/map')">在地图查看</el-button>
    </div>

    <!-- 规划输入 -->
    <div class="x-card mb-16">
      <el-form inline label-width="70px">
        <el-form-item label="目的地">
          <el-select v-model="form.city" placeholder="选择城市" style="width: 150px" filterable>
            <el-option v-for="c in cities" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="天数">
          <el-input-number v-model="form.days" :min="1" :max="7" />
        </el-form-item>
        <el-form-item label="模式">
          <el-select v-model="form.mode" style="width: 150px">
            <el-option v-for="(name, key) in ROUTE_MODE_MAP" :key="key" :label="name" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="预算档位">
          <el-select v-model="form.budgetLevel" style="width: 150px">
            <el-option v-for="b in BUDGETS" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="">
          <el-button type="primary" :loading="generating" @click="generate">生成行程</el-button>
        </el-form-item>
      </el-form>
      <div v-if="auth.isLoggedIn" class="flex-center gap-8" style="margin-top: 4px">
        <span class="sub-text">关联结伴行程（调整后自动通知同行成员）：</span>
        <el-select v-model="form.tripId" clearable placeholder="不关联" style="width: 300px">
          <el-option
            v-for="t in myCaptainTrips"
            :key="t.id"
            :label="t.title"
            :value="t.id"
          />
        </el-select>
      </div>
    </div>

    <div class="ai-layout">
      <!-- 行程结果 -->
      <div class="x-card">
        <div class="card-title">🗓️ 每日行程</div>
        <el-empty v-if="!result" description="填写目的地与天数后点击「生成行程」" :image-size="100" />
        <template v-else>
          <div v-if="days.length" class="day-list">
            <div v-for="d in days" :key="d.day" class="day-card" :style="{ borderLeftColor: d.color }">
              <div class="day-head">
                <span class="day-badge" :style="{ background: d.color }">Day {{ d.day }}</span>
                <span class="day-title">{{ d.title }}</span>
                <span class="sub-text">{{ d.spotCount }}个景点</span>
              </div>
              <div v-if="d.anchor" class="sub-text" style="margin-bottom: 6px">核心区域：{{ d.anchor }}</div>
              <div v-if="d.spots?.length" class="day-spots">
                <el-tag
                  v-for="s in d.spots"
                  :key="s.id || s.name"
                  size="small"
                  effect="plain"
                  type="info"
                  class="mr-6"
                >
                  {{ s.name }}
                </el-tag>
              </div>
            </div>
          </div>
          <div v-else class="single-hint">
            <div class="sub-text">单日行程已生成，可点击右上角「在地图查看」查看路线。</div>
            <div v-if="result.spots?.length" class="day-spots" style="margin-top: 10px">
              <el-tag v-for="s in result.spots" :key="s.id || s.name" size="small" class="mr-6">
                {{ s.name }}
              </el-tag>
            </div>
          </div>
        </template>
      </div>

      <div class="ai-side">
        <!-- 天气 -->
        <div class="x-card mb-16">
          <div class="card-title">🌦️ 目的地天气（{{ form.city || '—' }}）</div>
          <el-skeleton v-if="weatherLoading" :rows="3" animated />
          <template v-else-if="weather">
            <div class="weather-row">
              <div v-for="w in weather.days" :key="w.date" class="weather-item" :class="{ bad: isBad(w) }">
                <div class="w-week">{{ w.weekday }}</div>
                <div class="w-icon">{{ w.icon }}</div>
                <div class="w-text">{{ w.text }}</div>
                <div class="w-temp">{{ w.tempMin }}~{{ w.tempMax }}℃</div>
                <div class="w-rain">降水{{ w.rainChance }}%</div>
              </div>
            </div>
            <div class="sub-text" style="margin-top: 8px">{{ weather.summary }}</div>
            <div class="sub-text" style="font-size: 12px; margin-top: 4px">
              数据来源：{{ weather.source }}
            </div>
          </template>
          <el-empty v-else description="选择目的地后自动加载天气" :image-size="70" />
        </div>

        <!-- 费用估算 -->
        <div class="x-card mb-16">
          <div class="card-title">💰 全程费用估算</div>
          <el-skeleton v-if="estLoading" :rows="3" animated />
          <template v-else-if="estimate">
            <el-alert
              :type="estimate.withinBudget ? 'success' : 'warning'"
              :closable="false"
              class="mb-12"
              :title="`人均约 ¥${estimate.totalLow} - ${estimate.totalHigh}（${estimate.budgetLabel}）`"
            />
            <div v-for="it in estimate.items" :key="it.name" class="est-item">
              <span>{{ it.name }}</span>
              <span class="est-price">¥{{ it.low }}-{{ it.high }}</span>
            </div>
          </template>
          <el-empty v-else description="生成行程后展示费用估算" :image-size="70" />
        </div>

        <!-- 动态调整 -->
        <div class="x-card">
          <div class="card-title">🔄 动态行程调整</div>
          <div class="sub-text mb-12">
            接入天气等外部因素，遇变化时生成调整后行程与理由，并可通知同行成员
          </div>
          <el-button
            type="warning"
            plain
            :disabled="!form.city"
            :loading="adjusting"
            style="width: 100%"
            @click="doAdjust"
          >
            按天气生成调整方案
          </el-button>
          <template v-if="adjust">
            <el-alert
              type="warning"
              :closable="false"
              class="mt-12"
              :title="adjust.summary"
            />
            <div v-for="d in adjust.adjustedDays" :key="d.day" class="adj-item">
              <div class="adj-head">
                <span class="day-badge" :style="{ background: d.bad ? '#e6a23c' : '#67c23a' }">
                  Day {{ d.day }}
                </span>
                <span class="sub-text">{{ d.weekday }} · {{ d.weather?.text }}</span>
                <el-tag size="small" :type="d.bad ? 'warning' : 'success'" effect="plain">
                  {{ d.bad ? (d.action === 'SWAP_INDOOR' ? '建议室内替换' : '建议顺延') : '维持不变' }}
                </el-tag>
              </div>
              <div class="adj-suggest">{{ d.suggestion }}</div>
              <div v-if="d.alternatives?.length" class="day-spots">
                <el-tag v-for="a in d.alternatives" :key="a.id" size="small" type="warning" effect="plain" class="mr-6">
                  {{ a.name }}
                </el-tag>
              </div>
            </div>
            <div v-if="adjust.notified" class="sub-text" style="margin-top: 8px">
              ✅ 已通知{{ adjust.notified }}位同行成员
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MapLocation } from '@element-plus/icons-vue'
import { routeApi, spotApi, weatherApi, planApi, scrapedApi, tripApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import { ROUTE_MODE_MAP } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()

const BUDGETS = [
  { label: '不限预算', value: 'NONE' },
  { label: '1000元以内', value: 'LE1000' },
  { label: '1500元以内', value: 'LE1500' },
  { label: '2000元以内', value: 'LE2000' },
  { label: '3000元以内', value: 'LE3000' }
]

const cities = ref([])
const myCaptainTrips = ref([])
const form = ref({ city: '', days: 3, mode: 'OPTIMAL', budgetLevel: 'NONE', tripId: null })

const generating = ref(false)
const result = ref(null)
const days = ref([])

const weather = ref(null)
const weatherLoading = ref(false)

const estimate = ref(null)
const estLoading = ref(false)

const adjust = ref(null)
const adjusting = ref(false)

function isBad(w) {
  return (w.rainChance || 0) >= 60 || /雨|雪|雷/.test(w.text || '')
}

async function loadWeather() {
  if (!form.value.city) {
    weather.value = null
    return
  }
  weatherLoading.value = true
  try {
    weather.value = await weatherApi.forecast(form.value.city, form.value.days)
  } catch (e) {
    weather.value = null
  } finally {
    weatherLoading.value = false
  }
}

async function loadEstimate() {
  if (!form.value.city) {
    estimate.value = null
    return
  }
  estLoading.value = true
  try {
    estimate.value = await scrapedApi.costEstimate({
      destination: form.value.city,
      days: form.value.days,
      budgetLevel: form.value.budgetLevel
    })
  } catch (e) {
    estimate.value = null
  } finally {
    estLoading.value = false
  }
}

async function generate() {
  if (!form.value.city) {
    ElMessage.warning('请选择目的地')
    return
  }
  generating.value = true
  try {
    const data = await routeApi.plan({
      city: form.value.city,
      mode: form.value.mode,
      days: form.value.days
    })
    result.value = data
    days.value = data?.days || []
    adjust.value = null
    await Promise.all([loadWeather(), loadEstimate()])
    ElMessage.success('行程已生成')
  } finally {
    generating.value = false
  }
}

async function doAdjust() {
  if (!form.value.city) {
    ElMessage.warning('请先选择目的地')
    return
  }
  adjusting.value = true
  try {
    const body = {
      destination: form.value.city,
      days: form.value.days,
      dayPlans: (days.value || []).map((d) => ({ title: d.title, anchor: d.anchor }))
    }
    if (form.value.tripId) body.tripId = form.value.tripId
    adjust.value = await planApi.adjust(body)
    if (!adjust.value.badCount) {
      ElMessage.success('天气良好，行程无需调整')
    } else {
      ElMessage.warning(`已生成调整方案，${adjust.value.badCount} 天需关注`)
    }
  } finally {
    adjusting.value = false
  }
}

async function loadMyTrips() {
  if (!auth.isLoggedIn) return
  const list = (await tripApi.my().catch(() => [])) || []
  myCaptainTrips.value = list.filter((t) => t.captainId === auth.user?.id)
}

watch(
  () => [form.value.city, form.value.days],
  () => {
    if (form.value.city) {
      loadWeather()
      loadEstimate()
    }
  }
)

onMounted(async () => {
  cities.value = (await spotApi.cities().catch(() => [])) || []
  if (cities.value.length && !form.value.city) {
    form.value.city = cities.value[0]
  }
  loadMyTrips()
})
</script>

<style scoped>
.ai-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 1000px) {
  .ai-layout {
    grid-template-columns: 1fr;
  }
}
.ai-side {
  min-width: 0;
}
.day-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.day-card {
  border-left: 4px solid var(--xlvq-primary);
  background: #fbfcfe;
  border-radius: 10px;
  padding: 12px 14px;
}
.day-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.day-badge {
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
}
.day-title {
  font-weight: 700;
}
.day-spots {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.mr-6 {
  margin-right: 6px;
}
.weather-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}
.weather-item {
  flex: 1;
  min-width: 76px;
  text-align: center;
  padding: 10px 6px;
  border-radius: 10px;
  background: #f7f9fc;
  border: 1px solid var(--xlvq-border);
}
.weather-item.bad {
  background: #fdf6ec;
  border-color: #f3d19e;
}
.w-week {
  font-weight: 600;
  font-size: 13px;
}
.w-icon {
  font-size: 24px;
  margin: 4px 0;
}
.w-text,
.w-rain {
  font-size: 12px;
  color: var(--xlvq-text-sub);
}
.w-temp {
  font-weight: 600;
  font-size: 13px;
}
.est-item {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px dashed var(--xlvq-border);
  font-size: 13px;
}
.est-item:last-child {
  border-bottom: none;
}
.est-price {
  color: var(--xlvq-orange);
  font-weight: 600;
}
.adj-item {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fbfcfe;
  border: 1px solid var(--xlvq-border);
}
.adj-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.adj-suggest {
  font-size: 13px;
  margin-top: 6px;
  color: var(--xlvq-text);
  line-height: 1.6;
}
.mt-12 {
  margin-top: 12px;
}
</style>
