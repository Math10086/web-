<template>
  <div v-loading="loading" class="page-wrap">
    <div class="flex-between mb-16">
      <div class="flex-center" style="gap: 10px">
        <el-button link type="primary" @click="goBack">← 返回行程</el-button>
        <h2 class="page-title" style="margin: 0">🧭 团队管理</h2>
      </div>
      <div class="flex-center" style="gap: 8px">
        <el-tag v-if="data" :type="data.isCaptain ? 'danger' : 'info'" effect="plain" round>
          {{ data.isCaptain ? '队长视角' : '队员视角' }}
        </el-tag>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>

    <el-empty
      v-if="!loading && !data"
      description="没能读取到团队信息（可能不是该行程的成员）"
    />

    <template v-if="data">
      <!-- ===== 行程概览 ===== -->
      <div class="x-card mb-16">
        <div class="flex-between">
          <div class="tm-title">{{ data.trip.title }}</div>
          <el-tag :type="TRIP_STATUS_TAG[data.trip.status] || 'info'">
            {{ TRIP_STATUS_MAP[data.trip.status] || data.trip.status }}
          </el-tag>
        </div>
        <div class="tm-meta mt-12">
          <span>📍 {{ data.trip.destination || '—' }}</span>
          <span>🕒 {{ formatTime(data.trip.departAt) }}</span>
          <span>🗓️ {{ data.trip.prefDays ? data.trip.prefDays + ' 天' : '—' }}</span>
          <span>👥 {{ data.approvedCount }}/{{ data.trip.maxMembers || '—' }}人</span>
          <span v-if="data.trip.transport">🚌 {{ data.trip.transport }}</span>
          <span v-if="data.trip.meetPoint">📌 {{ data.trip.meetPoint }}</span>
          <el-tag v-if="data.trip.riskLevel" :type="RISK_TAG[data.trip.riskLevel] || 'success'" size="small" effect="plain">
            {{ RISK_MAP[data.trip.riskLevel] || data.trip.riskLevel }}
          </el-tag>
        </div>
        <div v-if="data.trip.interestTags" class="tm-tags mt-12">
          <el-tag v-for="t in tagList(data.trip.interestTags)" :key="t" size="small" effect="plain">
            # {{ t }}
          </el-tag>
        </div>
      </div>

      <!-- ===== 一屏概览指标 ===== -->
      <div class="tm-stats mb-16">
        <div class="tm-stat">
          <div class="ts-num">{{ data.memberCount }}</div>
          <div class="ts-label">全队人数</div>
          <div class="ts-sub">已通过{{ data.approvedCount }} · 待审{{ data.pendingCount }}</div>
        </div>
        <div class="tm-stat">
          <div class="ts-num">¥{{ money(data.expenseTotal) }}</div>
          <div class="ts-label">团队总支出</div>
          <div class="ts-sub">人均 ¥{{ money(data.avgPerPerson) }}</div>
        </div>
        <div class="tm-stat">
          <div class="ts-num">
            {{ data.budgetUsage == null ? '—' : data.budgetUsage + '%' }}
          </div>
          <div class="ts-label">预算使用率</div>
          <div class="ts-sub">预算{{ data.trip.budget ? '¥' + money(data.trip.budget) : '未设' }}</div>
        </div>
        <div class="tm-stat">
          <div class="ts-num">{{ data.footprintTotal }}</div>
          <div class="ts-label">全队足迹</div>
          <div class="ts-sub">共{{ footprintCities }}座城市</div>
        </div>
        <div class="tm-stat">
          <div class="ts-num">{{ settledCount }}</div>
          <div class="ts-label">已结清成员</div>
          <div class="ts-sub">净额为零</div>
        </div>
      </div>

      <div class="tm-cols">
        <!-- ===== 左：队员 + 全员消费状态 ===== -->
        <div class="section-stack">
          <div class="x-card">
            <div class="card-title">
              👥 队员与消费状态
              <span v-if="data.pendingCount" class="pending-tip">
                {{ data.pendingCount }}人待审核
              </span>
            </div>

            <div class="tm-members">
              <div v-for="m in data.members" :key="m.userId" class="tm-member">
                <!-- 第一行：头像 + 资料 + 状态 -->
                <div class="tmm-top">
                  <el-avatar
                    :size="44"
                    :src="m.avatar"
                    class="clickable-avatar"
                    title="查看资料"
                    @click="goUser(m.userId)"
                  >
                    {{ m.nickname?.charAt(0) }}
                  </el-avatar>
                  <div class="tmm-id">
                    <div class="tmm-name clickable-name" @click="goUser(m.userId)">
                      {{ m.nickname || '用户' + m.userId }}
                      <el-tag v-if="m.isCaptain" type="danger" size="small" effect="plain" round>
                        队长
                      </el-tag>
                      <el-tag v-if="m.verified" type="success" size="small" effect="plain">三校认证</el-tag>
                    </div>
                    <div class="sub-text">
                      {{ SCHOOL_MAP[m.school] || m.school || '学校未填' }} · 信誉分
                      {{ m.creditScore ?? m.creditSnapshot ?? 100 }}
                    </div>
                    <div v-if="m.status === 'REJECTED' && m.reason" class="sub-text">
                      拒绝原因：{{ m.reason }}
                    </div>
                  </div>
                  <el-tag v-if="!m.isCaptain" :type="MEMBER_STATUS_TAG[m.status] || 'info'" size="small">
                    {{ MEMBER_STATUS_MAP[m.status] || m.status }}
                  </el-tag>
                </div>

                <!-- 第二行：足迹摘要 + 消费状态 -->
                <div class="tmm-grid">
                  <div class="tmm-cell">
                    <div class="cell-title">👣 足迹</div>
                    <div class="cell-value">
                      {{ m.footprint?.count || 0 }}处 ·
                      {{ m.footprint?.cityCount || 0 }}城
                    </div>
                    <div class="cell-sub">
                      <template v-if="m.footprint?.lastName">
                        最近：{{ m.footprint.lastName }}
                        <span v-if="m.footprint.lastVisitedAt">
                          （{{ formatDate(m.footprint.lastVisitedAt) }}）
                        </span>
                      </template>
                      <template v-else>还没有记录足迹</template>
                    </div>
                  </div>

                  <div class="tmm-cell">
                    <div class="cell-title">💰 消费</div>
                    <div class="cell-value">
                      已付 ¥{{ money(m.expense?.paid) }} · 应摊 ¥{{ money(m.expense?.owed) }}
                    </div>
                    <div class="cell-sub">
                      <el-tag :type="netTag(m.expense?.net)" size="small" effect="light">
                        {{ netText(m.expense?.net) }}
                      </el-tag>
                    </div>
                  </div>
                </div>

                <!-- 第三行：位置共享 + 队长审核动作 -->
                <div class="tmm-foot">
                  <span v-if="m.lng && m.lat" class="sub-text">
                    📡 最近位置{{ Number(m.lng).toFixed(4) }}, {{ Number(m.lat).toFixed(4) }}
                    <span v-if="m.locationAt">· {{ formatTime(m.locationAt) }}</span>
                  </span>
                  <span v-else class="sub-text">未共享位置</span>

                  <span
                    v-if="data.isCaptain && m.status === 'PENDING' && m.memberId"
                    class="flex-center"
                    style="gap: 8px"
                  >
                    <el-button size="small" type="success" @click="review(m, 'approve')">
                      通过
                    </el-button>
                    <el-button size="small" type="danger" plain @click="review(m, 'reject')">
                      拒绝
                    </el-button>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ===== 右：全队足迹时间轴 ===== -->
        <div class="section-stack">
          <div class="x-card">
            <div class="card-title">
              🗺️ 全队足迹
              <span class="sub-text" style="font-weight: 400; font-size: 12px">
                共{{ data.footprintTotal }}条
              </span>
            </div>

            <el-empty
              v-if="!data.footprintTimeline?.length"
              description="队员还没有记录足迹"
              :image-size="70"
            />

            <div class="tm-timeline">
              <div
                v-for="(f, i) in data.footprintTimeline"
                :key="i"
                class="tm-fp"
              >
                <el-avatar :size="30" :src="f.avatar">{{ f.nickname?.charAt(0) }}</el-avatar>
                <div class="tm-fp-main">
                  <div class="tm-fp-title">
                    <strong>{{ f.nickname }}</strong>
                    <span class="sub-text">到访</span>
                    <span class="tm-fp-name">{{ f.name }}</span>
                  </div>
                  <div class="sub-text">
                    {{ formatDate(f.visitedAt) }}
                    <span v-if="f.city"> · {{ f.city }}</span>
                  </div>
                  <div v-if="f.note" class="tm-fp-note">{{ f.note }}</div>
                </div>
                <img v-if="f.photo" class="tm-fp-photo" :src="f.photo" :alt="f.name" />
              </div>
            </div>
          </div>

          <div class="x-card">
            <div class="card-title">🧾 消费说明</div>
            <div class="sub-text" style="line-height: 2">
              ・「已付」＝该成员垫付的团队消费总额；「应摊」＝按 AA 分摊后应由他承担的部分<br />
              ・净额为正＝他垫多了、<strong>应收回</strong>；净额为负＝他应付给团队<br />
              ・当前团队总支出 <strong>¥{{ money(data.expenseTotal) }}</strong>，
              人均 <strong>¥{{ money(data.avgPerPerson) }}</strong>
            </div>
            <el-button class="mt-12" size="small" @click="$router.push('/expenses')">
              查看完整消费报表
            </el-button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tripApi } from '@/api'
import {
  MEMBER_STATUS_MAP,
  MEMBER_STATUS_TAG,
  RISK_MAP,
  RISK_TAG,
  SCHOOL_MAP,
  TRIP_STATUS_MAP,
  TRIP_STATUS_TAG,
  formatTime
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const tripId = Number(route.params.id)

const data = ref(null)
const loading = ref(false)

/** 点成员头像/昵称查看其个人资料（全站统一用/profile?id= 看他人资料） */
function goUser(userId) {
  if (!userId) return
  router.push(`/profile?id=${userId}`)
}

/** 全队足迹覆盖的城市数（按人汇总去重） */
const footprintCities = computed(() => {
  const set = new Set()
  ;(data.value?.members || []).forEach((m) =>
    (m.footprint?.cities || []).forEach((c) => set.add(c))
  )
  return set.size
})

/** 已结清人数：只统计在队的成员（队长+已加入），未处理/已退出的不算 */
const settledCount = computed(
  () =>
    (data.value?.members || [])
      .filter((m) => m.isCaptain || m.status === 'APPROVED')
      .filter((m) => Math.abs(Number(m.expense?.net || 0)) < 0.005).length
)

async function load() {
  loading.value = true
  try {
    data.value = await tripApi.team(tripId)
  } catch (e) {
    data.value = null
  } finally {
    loading.value = false
  }
}

async function review(m, action) {
  try {
    await tripApi.reviewMember(tripId, m.memberId, action)
    ElMessage.success(action === 'approve' ? `已通过${m.nickname} 的报名` : `已拒绝${m.nickname} 的报名`)
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function goBack() {
  router.push(`/trips/${tripId}`)
}

const money = (v) => Number(v || 0).toFixed(2)
const tagList = (csv) =>
  String(csv || '')
    .split(/[,，\s]+/)
    .filter(Boolean)

function formatDate(v) {
  if (!v) return '—'
  return String(v).slice(0, 10)
}

function netTag(net) {
  const v = Number(net || 0)
  if (Math.abs(v) < 0.005) return 'info'
  return v > 0 ? 'success' : 'warning'
}

function netText(net) {
  const v = Number(net || 0)
  if (Math.abs(v) < 0.005) return '已结清'
  return v > 0 ? `应收回 ¥${money(v)}` : `应补付 ¥${money(-v)}`
}

onMounted(load)
</script>

<style scoped>
.page-title {
  font-size: 20px;
}
.tm-title {
  font-size: 17px;
  font-weight: 800;
}
.tm-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  font-size: 13px;
  color: var(--xlvq-text-sub);
  align-items: center;
}
.tm-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* ---- 指标卡 ---- */
.tm-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
.tm-stat {
  background: #fff;
  border: 1px solid var(--xlvq-border);
  border-radius: 14px;
  padding: 14px 16px;
}
.ts-num {
  font-size: 22px;
  font-weight: 800;
  color: var(--xlvq-primary);
  line-height: 1.3;
}
.ts-label {
  font-size: 13px;
  font-weight: 600;
  margin-top: 2px;
}
.ts-sub {
  font-size: 11.5px;
  color: var(--xlvq-text-sub);
  margin-top: 2px;
}

/* ---- 两栏 ---- */
.tm-cols {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* ---- 队员卡片 ---- */
.clickable-avatar {
  cursor: pointer;
  transition: transform 0.15s ease;
}
.clickable-avatar:hover {
  transform: scale(1.06);
}
.clickable-name {
  cursor: pointer;
}
.clickable-name:hover {
  color: var(--el-color-primary);
}
.tm-members {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.tm-member {
  border: 1px solid var(--xlvq-border);
  border-radius: 12px;
  padding: 12px 14px;
  background: #fcfdff;
}
.tmm-top {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.tmm-id {
  flex: 1;
  min-width: 0;
}
.tmm-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-size: 14.5px;
}
.tmm-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
}
.tmm-cell {
  background: #fff;
  border: 1px dashed var(--xlvq-border);
  border-radius: 10px;
  padding: 8px 10px;
}
.cell-title {
  font-size: 11.5px;
  color: var(--xlvq-text-sub);
}
.cell-value {
  font-size: 13px;
  font-weight: 600;
  margin-top: 2px;
}
.cell-sub {
  font-size: 11.5px;
  color: var(--xlvq-text-sub);
  margin-top: 3px;
  line-height: 1.7;
}
.tmm-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--xlvq-border);
}
.pending-tip {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #e6a23c;
}

/* ---- 足迹时间轴 ---- */
.tm-timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 560px;
  overflow-y: auto;
}
.tm-fp {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.tm-fp-main {
  flex: 1;
  min-width: 0;
}
.tm-fp-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  flex-wrap: wrap;
}
.tm-fp-name {
  font-weight: 700;
  color: var(--xlvq-primary);
}
.tm-fp-note {
  font-size: 12px;
  color: #5b6b82;
  margin-top: 3px;
  line-height: 1.7;
}
.tm-fp-photo {
  width: 54px;
  height: 42px;
  object-fit: cover;
  border-radius: 8px;
  flex: none;
}

@media (max-width: 1180px) {
  .tm-stats {
    grid-template-columns: repeat(3, 1fr);
  }
  .tm-cols {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 700px) {
  .tm-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .tmm-grid {
    grid-template-columns: 1fr;
  }
}
</style>
