<template>
  <div class="page-wrap">
    <!-- 找导游与导游接单（我的预约）已合并为一个模块：tab=bookings 时展示预约/接单视图 -->
    <el-tabs v-model="view" class="mb-16" @tab-change="syncView">
      <el-tab-pane :label="auth.isGuide ? '🧭 服务广场' : '🧭 找导游'" name="list" />
      <el-tab-pane v-if="auth.isLoggedIn" :label="auth.isGuide ? '🎫 接单管理' : '🎫 我的预约'" name="bookings" />
    </el-tabs>

    <!-- 预约 / 接单视图：独立组件按需挂载 -->
    <GuideBookings v-if="view === 'bookings'" embedded />

    <template v-else>
    <!-- 顶部：找导游 + 我的申请状态 -->
    <div class="gradient-banner mb-16" style="padding: 22px 30px">
      <div class="flex-between" style="position: relative; z-index: 2; flex-wrap: wrap; gap: 12px">
        <div>
          <h1 style="font-size: 24px">🧭 找导游 · 认证向导带你玩</h1>
          <p>
            认证导游 / 本地达人兼职向导，按次付费、平台代收担保；<br />
            平台仅收10% 手续费，90% 直达导游
          </p>
        </div>
        <div class="flex gap-12" style="align-items: center; flex-wrap: wrap">
          <!-- 任何登录用户都能在这里找到自己预约过的导游订单（导游接单入口已收归导游账号） -->
          <el-button
            v-if="auth.isLoggedIn"
            :icon="Tickets"
            @click="goBookings('traveler')"
          >
            我的导游预约
          </el-button>
          <template v-if="myProfile">
            <el-tag
              :type="myProfile.status === 'APPROVED' ? 'success' : myProfile.status === 'PENDING' ? 'warning' : 'danger'"
              size="large"
            >
              我的导游申请：{{ AUDIT_STATUS_MAP[myProfile.status] || myProfile.status }}
            </el-tag>
            <el-button
              v-if="myProfile.status === 'APPROVED'"
              type="warning"
              :icon="Promotion"
              @click="openService()"
            >
              发布导游服务
            </el-button>
            <!-- 接单管理入口就是外层的「接单管理」标签页，这里不再重复放按钮 -->
          </template>
          <el-button v-else type="warning" :icon="Promotion" size="large" @click="applyVisible = true">
            申请成为导游
          </el-button>
        </div>
      </div>
    </div>

    <!-- 搜索与筛选：找导游的核心入口 -->
    <div class="x-card mb-16">
      <div class="flex gap-12" style="flex-wrap: wrap; align-items: center">
        <el-input
          v-model="keyword"
          class="finder-search"
          placeholder="搜城市、主题或向导昵称，如：张家界 / 徒步 / 大山"
          :prefix-icon="Search"
          clearable
        />
        <el-select v-model="sortBy" style="width: 160px">
          <el-option label="综合推荐" value="smart" />
          <el-option label="评分最高" value="rating" />
          <el-option label="价格从低到高" value="priceAsc" />
          <el-option label="接单最多" value="orders" />
        </el-select>
        <span class="sub-text">
          共{{ matchedGuides.length }}位向导 / {{ matchedServiceCount }}项服务
        </span>
        <el-button v-if="hasFilter" link type="primary" @click="resetFilter">重置筛选</el-button>
      </div>

      <div v-if="destOptions.length" class="filter-row">
        <span class="filter-label">目的地</span>
        <el-tag
          class="filter-chip"
          :effect="destFilter === '' ? 'dark' : 'plain'"
          :type="destFilter === '' ? 'primary' : 'info'"
          @click="destFilter = ''"
        >
          全部
        </el-tag>
        <el-tag
          v-for="d in destOptions"
          :key="d"
          class="filter-chip"
          :effect="destFilter === d ? 'dark' : 'plain'"
          :type="destFilter === d ? 'primary' : 'info'"
          @click="destFilter = d"
        >
          {{ d }}
        </el-tag>
      </div>

      <div v-if="themeOptions.length" class="filter-row">
        <span class="filter-label">主题</span>
        <el-tag
          class="filter-chip"
          :effect="themeFilter === '' ? 'dark' : 'plain'"
          :type="themeFilter === '' ? 'primary' : 'info'"
          @click="themeFilter = ''"
        >
          全部
        </el-tag>
        <el-tag
          v-for="t in themeOptions"
          :key="t"
          class="filter-chip"
          :effect="themeFilter === t ? 'dark' : 'plain'"
          :type="themeFilter === t ? 'primary' : 'info'"
          @click="themeFilter = t"
        >
          {{ t }}
        </el-tag>
      </div>
    </div>

    <!-- 向导列表 -->
    <div v-loading="loading" class="section-stack">
      <el-empty
        v-if="!loading && !matchedGuides.length"
        :description="hasFilter ? '没有符合条件的小向导，试试放宽筛选条件' : '暂时还没有导游，攻略博主可申请成为首批向导'"
      />

      <div v-for="g in matchedGuides" :key="g.id" class="x-card">
        <div class="flex gap-16" style="align-items: flex-start; flex-wrap: wrap">
          <el-tooltip content="查看 TA 的接待经历与评价" placement="top">
            <el-avatar
              :size="64"
              :src="g.avatar"
              class="gp-avatar"
              @click="openProfile(g)"
            >
              {{ g.nickname?.charAt(0) }}
            </el-avatar>
          </el-tooltip>
          <div style="flex: 1; min-width: 220px">
            <div style="font-size: 17px; font-weight: 700">
              <span class="gp-name-link" @click="openProfile(g)">{{ g.nickname }}</span>
              <el-tag
                :type="g.supplyTier === 'CERTIFIED_GUIDE' ? 'success' : 'warning'"
                size="small"
                class="ml-8"
              >
                {{ g.supplyTierLabel || '认证导游' }}
              </el-tag>
              <el-tag v-if="g.userId === auth.user?.id" size="small" type="warning" class="ml-8">我</el-tag>
              <el-button link type="primary" size="small" class="ml-8" @click="openProfile(g)">
                主页 ›
              </el-button>
            </div>
            <div class="sub-text mt-12">{{ SCHOOL_MAP[g.school] || g.school || '三校达人' }}</div>
            <div class="mt-12">
              <el-rate :model-value="Number(g.ratingAvg) || 0" disabled size="small" show-score />
              <span class="sub-text" style="margin-left: 10px">
                接单{{ g.orderCount ?? 0 }}次
              </span>
            </div>
            <div class="mt-12">
              <el-tag
                v-for="t in splitThemes(g.themes)"
                :key="t"
                size="small"
                class="ml-8"
                type="info"
                effect="plain"
              >
                {{ t }}
              </el-tag>
            </div>
            <div class="sub-text mt-12" style="line-height: 1.7">{{ g.intro }}</div>
          </div>
        </div>

        <el-divider v-if="servicesOf(g).length" style="margin: 14px 0" />

        <div v-if="servicesOf(g).length" class="service-grid">
          <div v-for="s in servicesOf(g)" :key="s.id" class="service-item">
            <div style="font-weight: 700">
              {{ s.title }}
              <el-tag v-if="s.priceTierLabel" size="small" type="info" effect="plain" class="ml-8">
                {{ s.priceTierLabel }}
              </el-tag>
            </div>
            <div class="sub-text mt-12">📍 {{ s.destination }} ｜ {{ s.themes }}</div>
            <div class="sub-text mt-12">🗓️ {{ s.schedule }}</div>
            <div class="sub-text" style="line-height: 1.6">{{ s.intro }}</div>
            <div class="flex-between mt-12">
              <span style="color: #f5222d; font-weight: 800; font-size: 16px">
                ¥{{ s.price }}<span class="sub-text">/次</span>
              </span>
              <div class="flex gap-8">
                <el-button v-if="isMyService(s, g)" size="small" @click="openService(s)">
                  管理
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="primary"
                  round
                  @click="openBooking(s)"
                >
                  立即预约
                </el-button>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="sub-text mt-12">该向导暂未发布可预约的服务</div>
      </div>
    </div>

    <!-- 导游申请 -->
    <el-dialog v-model="applyVisible" title="申请成为导游（向导）" width="520px">
      <el-alert
        type="info"
        :closable="false"
        class="mb-12"
        title="资深攻略博主、本地达人、本地论坛活跃用户均可申请；审核通过后可发布导游服务接单，平台担保交易并按服务分佣。"
      />
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-width="84px">
        <el-form-item label="擅长主题" prop="themes">
          <el-input v-model="applyForm.themes" placeholder="如：Citywalk、美食探店、徒步露营，逗号分隔" />
        </el-form-item>
        <el-form-item label="个人介绍" prop="intro">
          <el-input v-model="applyForm.intro" type="textarea" :rows="4" placeholder="介绍你的旅行经历、擅长城市与讲解风格" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 服务发布/编辑 -->
    <el-dialog
      v-model="serviceVisible"
      :title="serviceForm.id ? '管理导游服务' : '发布导游服务'"
      width="520px"
    >
      <el-form :model="serviceForm" label-width="84px">
        <el-form-item label="服务标题" required>
          <el-input v-model="serviceForm.title" placeholder="如：长沙老城 Citywalk 半日讲解" />
        </el-form-item>
        <el-form-item label="目的地" required>
          <el-input v-model="serviceForm.destination" placeholder="如：长沙" />
        </el-form-item>
        <el-form-item label="主题">
          <el-input v-model="serviceForm.themes" placeholder="如：美食探店,Citywalk" />
        </el-form-item>
        <el-form-item label="价格（次）">
          <el-input-number v-model="serviceForm.price" :min="0" :step="20" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排期">
          <el-input v-model="serviceForm.schedule" placeholder="如：周末/节假日全天，需提前2天预约" />
        </el-form-item>
        <el-form-item label="服务介绍">
          <el-input v-model="serviceForm.intro" type="textarea" :rows="3" placeholder="讲解路线、集合方式、包含与不含项目" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitService">保存服务</el-button>
      </template>
    </el-dialog>

    <!-- 预约 -->
    <el-dialog v-model="bookingVisible" title="预约导游服务" width="460px">
      <div v-if="bookingService" class="mb-12">
        <strong>{{ bookingService.title }}</strong>
        <div class="sub-text">
          {{ bookingService.destination }} · ¥{{ bookingService.price }}/次 · 平台担保交易
        </div>
      </div>
      <el-form label-width="84px">
        <el-form-item label="出行日期" required>
          <el-date-picker
            v-model="bookingForm.tripDate"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注需求">
          <el-input v-model="bookingForm.remark" type="textarea" :rows="3" placeholder="人数、偏好、集合地点等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bookingVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBooking">确认预约</el-button>
      </template>
    </el-dialog>

    <!-- 向导公开主页：过往接待经历 + 游客评价 + 在售服务 -->
    <GuideProfileDrawer
      v-model="profileVisible"
      :user-id="profileUserId"
      @book="openBooking"
    />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion, Search, Tickets } from '@element-plus/icons-vue'
import { guideApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import { AUDIT_STATUS_MAP, SCHOOL_MAP } from '@/utils/format'
import { useRoute } from 'vue-router'
import GuideBookings from '@/views/guide/GuideBookings.vue'
import GuideProfileDrawer from '@/components/guide/GuideProfileDrawer.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/* ---------- 视图切换：找导游/预约接单（导游接单已并入本页） ---------- */
const view = ref(route.query.tab === 'bookings' && auth.isLoggedIn ? 'bookings' : 'list')
function syncView(name) {
  router.replace({ query: { tab: name === 'list' ? undefined : name } })
}
/** 页内跳转到预约/接单视图（支持指定接单页内部页签） */
function goBookings(inner) {
  view.value = 'bookings'
  router.replace({ query: { tab: 'bookings', ...(inner ? { inner } : {}) } })
}

const loading = ref(false)
const saving = ref(false)
const guides = ref([])
const services = ref([])
const myProfile = ref(null)

/* ---------- 导档案扁平化：兼容「扁平字段」与「{profile,user} 嵌套」两种返回 ---------- */
function flattenProfile(row) {
  const p = row?.profile || row || {}
  const u = row?.user || row || {}
  return {
    id: p.id,
    userId: p.userId ?? u.userId ?? u.id,
    nickname: row?.nickname ?? u.nickname ?? p.nickname ?? '匿名向导',
    avatar: row?.avatar ?? u.avatar ?? p.avatar ?? '',
    school: row?.school ?? u.school ?? p.school ?? '',
    themes: p.themes,
    intro: p.intro,
    status: p.status,
    supplyTier: p.supplyTier ?? row?.supplyTier,
    supplyTierLabel: p.supplyTierLabel ?? row?.supplyTierLabel,
    ratingAvg: p.ratingAvg,
    ratingCount: p.ratingCount,
    orderCount: p.orderCount
  }
}

/* ---------- 服务扁平化：兼容「扁平字段」与「{service,guide} 嵌套」两种返回 ---------- */
function flattenService(row) {
  const s = row?.service || row || {}
  const g = row?.guide || {}
  return {
    id: s.id,
    guideId: s.guideId,
    guideName: row?.guideName ?? g.nickname ?? s.guideName,
    title: s.title,
    destination: s.destination,
    themes: s.themes,
    price: s.price,
    schedule: s.schedule,
    intro: s.intro,
    status: s.status,
    supplyTier: row?.supplyTier ?? s.supplyTier,
    supplyTierLabel: row?.supplyTierLabel ?? s.supplyTierLabel,
    priceTierLabel: row?.priceTierLabel ?? s.priceTierLabel,
    ratingAvg: row?.ratingAvg ?? s.ratingAvg,
    orderCount: row?.orderCount ?? s.orderCount
  }
}

/* ---------- 搜索/筛选/排序 ---------- */
const keyword = ref('')
const destFilter = ref('')
const themeFilter = ref('')
const sortBy = ref('smart')

const hasFilter = computed(
  () => !!keyword.value.trim() || !!destFilter.value || !!themeFilter.value
)

function resetFilter() {
  keyword.value = ''
  destFilter.value = ''
  themeFilter.value = ''
}

const destOptions = computed(() => {
  const set = new Set()
  services.value.forEach((s) => s.destination && set.add(s.destination))
  return [...set]
})

const themeOptions = computed(() => {
  const set = new Set()
  services.value.forEach((s) => splitThemes(s.themes).forEach((t) => set.add(t)))
  guides.value.forEach((g) => splitThemes(g.themes).forEach((t) => set.add(t)))
  return [...set].slice(0, 10)
})

function serviceMatch(s) {
  if (destFilter.value && s.destination !== destFilter.value) return false
  if (themeFilter.value && !splitThemes(s.themes).includes(themeFilter.value)) return false
  return true
}

function guideMatchByKeyword(g, list) {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return true
  const hay = [
    g.nickname,
    g.themes,
    g.intro,
    ...list.map((s) => `${s.title} ${s.destination} ${s.themes}`)
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
  return hay.includes(kw)
}

/** 命中筛选的向导（连带只显示命中的服务） */
const matchedGuides = computed(() => {
  const kw = keyword.value.trim()
  const out = []
  for (const g of guides.value) {
    let list = services.value.filter((s) => sameGuide(s, g))
    if (destFilter.value || themeFilter.value) list = list.filter(serviceMatch)
    // 用了筛选条件时，没有命中服务的向导不展示
    if ((destFilter.value || themeFilter.value || kw) && !list.length) continue
    let snapshot = { ...g, matchedServices: list }
    if (!guideMatchByKeyword(g, list)) continue
    snapshot.minPrice = list.length ? Math.min(...list.map((s) => Number(s.price) || 0)) : 0
    out.push(snapshot)
  }
  const byRating = (a, b) => (Number(b.ratingAvg) || 0) - (Number(a.ratingAvg) || 0)
  if (sortBy.value === 'rating') return out.sort(byRating)
  if (sortBy.value === 'orders') return out.sort((a, b) => (b.orderCount || 0) - (a.orderCount || 0))
  if (sortBy.value === 'priceAsc') return out.sort((a, b) => a.minPrice - b.minPrice)
  return out.sort((a, b) => byRating(a, b) || (b.orderCount || 0) - (a.orderCount || 0))
})

const matchedServiceCount = computed(() =>
  matchedGuides.value.reduce((n, g) => n + (g.matchedServices?.length || 0), 0)
)

const applyVisible = ref(false)
const applyFormRef = ref()
const applyForm = ref({ themes: '', intro: '' })
const applyRules = {
  themes: [{ required: true, message: '填写擅长主题', trigger: 'blur' }],
  intro: [{ required: true, message: '填写个人介绍', trigger: 'blur' }]
}

const serviceVisible = ref(false)
const serviceForm = ref({})

const bookingVisible = ref(false)
const bookingService = ref(null)
const bookingForm = ref({ tripDate: '', remark: '' })

/* ---------- 向导公开主页（抽屉） ---------- */
const profileVisible = ref(false)
const profileUserId = ref(null)
function openProfile(g) {
  profileUserId.value = g.userId || g.id
  profileVisible.value = true
}

function splitThemes(t) {
  if (!t) return []
  return String(t).split(/[,，、]/).filter(Boolean)
}

/** 服务是否属于该向导（兼容guideId/userId两种关联口径） */
function sameGuide(s, g) {
  return (
    s.guideId === g.id ||
    s.guideId === g.userId ||
    s.guideProfileId === g.id ||
    s.userId === g.userId ||
    (!!s.guideName && s.guideName === g.nickname)
  )
}

function servicesOf(g) {
  return g.matchedServices || services.value.filter((s) => sameGuide(s, g))
}

function isMyService(s, g) {
  return auth.isLoggedIn && (s.guideId === auth.user?.id || g.userId === auth.user?.id)
}

async function load() {
  loading.value = true
  try {
    // 对外的「找导游」只看已认证向导：不带status=all（那是管理员审核队列，学生请求会403）
    const [profileRes, serviceRes] = await Promise.allSettled([
      guideApi.profiles(),
      guideApi.services()
    ])
    const rawProfiles =
      profileRes.status === 'fulfilled' ? profileRes.value?.list || profileRes.value || [] : []
    guides.value = rawProfiles
      .map(flattenProfile)
      .filter((p) => p.status === 'APPROVED' && p.userId)

    const rawServices =
      serviceRes.status === 'fulfilled' ? serviceRes.value?.list || serviceRes.value || [] : []
    services.value = rawServices.map(flattenService)

    // 我的申请状态（独立接口，老后端没有时静默忽略）
    if (auth.isLoggedIn) {
      try {
        const mine = await guideApi.myProfile()
        myProfile.value = mine ? flattenProfile(mine) : null
      } catch (e) {
        myProfile.value = guides.value.find((p) => p.userId === auth.user?.id) || null
      }
    }
  } finally {
    loading.value = false
  }
}

async function submitApply() {
  await applyFormRef.value.validate()
  saving.value = true
  try {
    await guideApi.apply({ ...applyForm.value })
    ElMessage.success('申请已提交，请等待审核')
    applyVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openService(row) {
  serviceForm.value = row
    ? { ...row }
    : { title: '', destination: '', themes: '', price: 100, schedule: '', intro: '' }
  serviceVisible.value = true
}

async function submitService() {
  if (!serviceForm.value.title || !serviceForm.value.destination) {
    ElMessage.warning('请填写标题与目的地')
    return
  }
  saving.value = true
  try {
    if (serviceForm.value.id) {
      await guideApi.updateService(serviceForm.value.id, { ...serviceForm.value })
    } else {
      await guideApi.createService({ ...serviceForm.value })
    }
    ElMessage.success('服务已保存')
    serviceVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openBooking(s) {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/guides' } })
    return
  }
  bookingService.value = s
  bookingForm.value = { tripDate: '', remark: '' }
  bookingVisible.value = true
}

async function submitBooking() {
  if (!bookingForm.value.tripDate) {
    ElMessage.warning('请选择出行日期')
    return
  }
  saving.value = true
  try {
    await guideApi.createBooking({
      serviceId: bookingService.value.id,
      tripDate: bookingForm.value.tripDate,
      remark: bookingForm.value.remark
    })
    ElMessage.success('预约已提交，等待导游确认，费用由平台代收担保')
    bookingVisible.value = false
    goBookings('traveler')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ml-8 {
  margin-left: 8px;
}
.finder-search {
  width: 320px;
}
.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.filter-label {
  font-size: 13px;
  color: var(--xlvq-text-sub);
  margin-right: 2px;
}
.filter-chip {
  cursor: pointer;
  user-select: none;
}
.gp-avatar {
  cursor: pointer;
  transition: transform 0.15s;
}
.gp-avatar:hover {
  transform: scale(1.06);
}
.gp-name-link {
  cursor: pointer;
}
.gp-name-link:hover {
  color: var(--xlvq-primary);
}
.service-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.service-item {
  border: 1px solid var(--xlvq-border);
  border-radius: 12px;
  padding: 12px 14px;
}
@media (max-width: 860px) {
  .service-grid {
    grid-template-columns: 1fr;
  }
  .finder-search {
    width: 100%;
  }
}
</style>
