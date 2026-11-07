<template>
  <div class="page-wrap">
    <!-- 找搭子已并入结伴出行：一个模块两个标签页（旧 /match 自动跳转到 ?tab=match） -->
    <el-tabs v-model="activeTab" class="mb-16" @tab-change="syncTab">
      <el-tab-pane label="🚩 结伴出行" name="trips" />
      <el-tab-pane label="🤝 找搭子" name="match" />
    </el-tabs>

    <template v-if="activeTab === 'trips'">
    <div class="flex-between mb-16">
      <h2 class="page-title" style="margin: 0">🚩 结伴出行 · 三校同城组队</h2>
      <div class="flex gap-8">
        <el-button
          v-if="auth.isBusiness"
          :icon="Suitcase"
          @click="$router.push('/trips/my')"
        >
          我的结伴
        </el-button>
        <el-button v-if="auth.isLoggedIn" :icon="Aim" @click="prefVisible = true">
          我的结伴偏好
        </el-button>
        <el-button type="primary" :icon="Promotion" @click="goCreate">发起结伴</el-button>
      </div>
    </div>

    <!-- 为你推荐 Top 3（按出行画像个性化推荐） -->
    <div v-if="auth.isLoggedIn && recommended.length" class="x-card mb-16">
      <div class="flex-between mb-12">
        <div class="card-title" style="margin: 0">⭐ 为你推荐 Top {{ recommended.length }}结伴团队</div>
        <span class="sub-text">
          {{
            profileSignal
              ? `依据你的行为画像（${profileSignal} 条行为信号）、结伴偏好与信誉分计算`
              : '依据你的结伴偏好、信誉分与成团机会计算'
          }}
        </span>
      </div>
      <div class="rec-grid">
        <div
          v-for="(r, i) in recommended"
          :key="r.trip.id"
          class="rec-card hoverable"
          @click="goDetail(r.trip.id)"
        >
          <div class="flex-between">
            <el-tag type="danger" effect="dark" size="small">No.{{ i + 1 }}</el-tag>
            <el-tag :type="matchTagType(r.matchScore)" effect="dark">
              匹配度{{ r.matchScore }}
            </el-tag>
          </div>
          <div class="rec-title">{{ r.trip.title }}</div>
          <div class="sub-text">📍 {{ r.trip.destination }} · 🕒 {{ formatTime(r.trip.departAt) }}</div>
          <div class="rec-reasons">
            <div v-for="d in (r.dims || []).slice(0, 3)" :key="d.key" class="reason-line">
              <span class="reason-label">{{ d.label }}</span>
              <span>{{ d.note }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="x-card mb-16">
      <div class="filter-row">
        <span class="filter-label">学校</span>
        <el-radio-group v-model="school" size="small" @change="load">
          <el-radio-button label="ALL">三校全部</el-radio-button>
          <el-radio-button v-if="auth.isLoggedIn" label="MINE">仅我的学校</el-radio-button>
          <el-radio-button label="CSU">中南大学</el-radio-button>
          <el-radio-button label="HUNNU">湖南师范大学</el-radio-button>
          <el-radio-button label="HNU">湖南大学</el-radio-button>
        </el-radio-group>
      </div>

      <div class="filter-row">
        <span class="filter-label">状态</span>
        <el-radio-group v-model="status" size="small" @change="load">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="OPEN">招募中</el-radio-button>
          <el-radio-button label="FULL">已满员</el-radio-button>
          <el-radio-button label="ONGOING">进行中</el-radio-button>
          <el-radio-button label="FINISHED">已结束</el-radio-button>
        </el-radio-group>
      </div>

      <div class="filter-row">
        <span class="filter-label">其他</span>
        <el-input
          v-model="destination"
          placeholder="按目的地筛选，如：张家界"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="sort" style="width: 170px" @change="load">
          <el-option label="最新发布" value="latest" />
          <el-option v-if="auth.isLoggedIn" label="匹配度优先" value="match" />
        </el-select>
      </div>
    </div>

    <el-empty v-if="!loading && !trips.length" description="暂无符合条件的结伴行程" />

    <div v-loading="loading" class="trip-grid">
      <div v-for="t in trips" :key="t.id" class="x-card hoverable" @click="goDetail(t.id)">
        <div class="flex-between mb-12">
          <div class="flex gap-8">
            <el-tag :type="TRIP_STATUS_TAG[t.status] || 'info'">
              {{ TRIP_STATUS_MAP[t.status] || t.status }}
            </el-tag>
            <el-tag v-if="t.matchScore != null" :type="matchTagType(t.matchScore)" effect="plain">
              匹配度{{ t.matchScore }}
            </el-tag>
          </div>
          <el-tag :type="RISK_TAG[t.riskLevel] || 'success'" effect="plain">
            {{ RISK_MAP[t.riskLevel] || '低风险' }}
          </el-tag>
        </div>
        <div class="trip-title">{{ t.title }}</div>
        <div class="sub-text mt-12" style="line-height: 2">
          <div>📍 目的地：{{ t.destination }}</div>
          <div>🕒 出发：{{ formatTime(t.departAt) }}</div>
          <div>🚄 方式：{{ TRANSPORT_MODE_MAP[t.transport] || t.transport || '待定' }}</div>
          <div>👥 人数：{{ t.approvedCount || 0 }}/{{ t.maxMembers }}人</div>
          <div>💰 预算：约 ¥{{ t.budget }} / 人</div>
        </div>

        <!-- 队伍偏好标签 -->
        <div class="tag-line mt-12">
          <el-tag size="small" effect="plain" type="primary">
            {{ PACE_SHORT_MAP[t.paceType] || '节奏适中' }}
          </el-tag>
          <el-tag v-if="t.prefDays" size="small" effect="plain">{{ t.prefDays }}天</el-tag>
          <el-tag
            size="small"
            effect="plain"
            :type="t.schoolScope === 'SAME' ? 'warning' : 'info'"
          >
            {{ SCHOOL_SCOPE_MAP[t.schoolScope] || '三校跨校' }}
          </el-tag>
          <el-tag v-for="tag in splitTags(t.interestTags)" :key="tag" size="small" effect="plain">
            {{ tag }}
          </el-tag>
        </div>

        <el-progress
          :percentage="memberPercent(t)"
          :stroke-width="8"
          class="mt-12"
          :status="memberPercent(t) >= 100 ? 'success' : ''"
        />
        <div class="author-card mt-12">
          <el-avatar
            :size="32"
            :src="t.captain?.avatar"
            class="clickable-avatar"
            title="查看队长资料"
            @click.stop="goUser(t.captainId)"
          >
            {{ t.captain?.nickname?.charAt(0) || '队' }}
          </el-avatar>
          <div
            style="flex: 1; font-size: 13px; cursor: pointer"
            @click.stop="goUser(t.captainId)"
          >
            队长：<strong>{{ t.captain?.nickname || '加载中' }}</strong>
            <span class="sub-text">· {{ SCHOOL_MAP[t.captain?.school] || '三校' }}</span>
          </div>
          <el-tag v-if="t.captain?.verified" type="success" size="small">已认证</el-tag>
        </div>
        <el-button
          class="mt-12"
          style="width: 100%"
          :type="isRecruiting(t) ? 'primary' : 'default'"
          :disabled="!isRecruiting(t) || memberPercent(t) >= 100"
          @click.stop="goDetail(t.id)"
        >
          {{ memberPercent(t) >= 100 ? '已满员' : isRecruiting(t) ? '查看并报名' : '查看详情' }}
        </el-button>
      </div>
    </div>

    </template>

    <!-- 找搭子标签页：登录用户内嵌撮合页，游客引导登录 -->
    <template v-else-if="activeTab === 'match'">
      <PeopleMatch v-if="auth.isLoggedIn" />
      <div v-else class="x-card">
        <el-empty description="找搭子需要登录并填写出行标签档案后使用">
          <el-button type="primary" @click="$router.push('/login')">去登录</el-button>
        </el-empty>
      </div>
    </template>

    <TripPrefDialog v-model="prefVisible" @saved="loadRecommend" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Aim, Promotion, Search, Suitcase } from '@element-plus/icons-vue'
import { tripApi } from '@/api'
import TripPrefDialog from '@/views/trips/TripPrefDialog.vue'
import PeopleMatch from '@/views/match/PeopleMatch.vue'
import {
  formatTime,
  matchTagType,
  PACE_SHORT_MAP,
  RISK_MAP,
  RISK_TAG,
  SCHOOL_MAP,
  SCHOOL_SCOPE_MAP,
  splitTags,
  TRANSPORT_MODE_MAP,
  TRIP_STATUS_MAP,
  TRIP_STATUS_TAG
} from '@/utils/format'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// 找搭子并入后的标签页：支持/trips?tab=match直达（旧/match重定向到这里）
const activeTab = ref(route.query.tab === 'match' ? 'match' : 'trips')
function syncTab(name) {
  router.replace({ query: { ...route.query, tab: name === 'trips' ? undefined : name } })
}

const loading = ref(false)
const trips = ref([])
const recommended = ref([])
/** 行为画像的信号条数（>0说明推荐里含行为推断的兴趣） */
const profileSignal = ref(0)
const prefVisible = ref(false)
// 默认「全部」：招募中和往期（进行中/已结束）行程一屏都能看到，避免以为过往行程不见了
const status = ref('')
const school = ref('ALL')
const destination = ref('')
const sort = ref('latest')

function isRecruiting(t) {
  return t.status === 'OPEN' || t.status === 'FULL' || t.status === 'PLANNING'
}

function memberPercent(t) {
  const cur = t.approvedCount ?? t.currentMembers ?? 0
  if (!t.maxMembers) return 0
  return Math.min(100, Math.round((cur / t.maxMembers) * 100))
}

async function load() {
  loading.value = true
  try {
    const params = { sort: sort.value }
    if (status.value) params.status = status.value
    if (school.value && school.value !== 'ALL') params.school = school.value
    if (destination.value.trim()) params.destination = destination.value.trim()
    const data = await tripApi.list(params)
    trips.value = asList(data)
  } finally {
    loading.value = false
  }
}

/** 兼容后端返回 {list:[...]}、裸数组，以及后端未重启时被SPA回退返回的HTML串 */
function asList(data) {
  if (Array.isArray(data)) return data
  if (data && Array.isArray(data.list)) return data.list
  return []
}

async function loadRecommend() {
  if (!auth.isLoggedIn) {
    recommended.value = []
    return
  }
  // 画像与推荐并行取：画像只用于给推荐加一句"依据"，失败不影响推荐
  const [data, prof] = await Promise.all([
    tripApi.recommend(3).catch(() => []),
    tripApi.getPrefProfile().catch(() => null)
  ])
  recommended.value = asList(data).filter((r) => r && r.trip)
  profileSignal.value = prof && prof.confidence && prof.confidence !== 'NONE' ? prof.signalCount : 0
}

function goCreate() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/trips/new' } })
    return
  }
  router.push('/trips/new')
}

function goDetail(id) {
  router.push(`/trips/${id}`)
}

/** 点队长头像/昵称看队长资料（全站统一用/profile?id= 看他人资料） */
function goUser(userId) {
  if (!userId) return
  router.push(`/profile?id=${userId}`)
}

onMounted(() => {
  load()
  loadRecommend()
})
</script>

<style scoped>
.clickable-avatar {
  cursor: pointer;
  transition: transform 0.15s ease;
}
.clickable-avatar:hover {
  transform: scale(1.08);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-row + .filter-row {
  margin-top: 12px;
}
.filter-label {
  width: 42px;
  font-weight: 600;
  font-size: 13px;
  color: var(--xlvq-text-2);
}
.trip-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.rec-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.rec-card {
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  padding: 12px 14px;
  cursor: pointer;
  background: var(--xlvq-card-2, #fafafa);
}
.rec-title {
  font-weight: 700;
  margin: 8px 0 4px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.rec-reasons {
  margin-top: 8px;
  border-top: 1px dashed var(--xlvq-border);
  padding-top: 8px;
}
.reason-line {
  font-size: 12px;
  color: var(--xlvq-text-2);
  line-height: 1.7;
  display: flex;
  gap: 6px;
}
.reason-label {
  flex: 0 0 56px;
  color: #2f80ed;
}
.tag-line {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.trip-title {
  font-weight: 700;
  font-size: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
@media (max-width: 1100px) {
  .trip-grid,
  .rec-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 600px) {
  .trip-grid,
  .rec-grid {
    grid-template-columns: 1fr;
  }
}
</style>
