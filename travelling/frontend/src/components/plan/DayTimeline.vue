<template>
  <div class="tl-wrap">
    <!-- 空态 -->
    <div v-if="!plan.planResult" class="tl-empty">
      <div class="tl-empty-icon">🗓️</div>
      <div class="tl-empty-title">还没有行程</div>
      <div class="sub-text" style="line-height: 2; text-align: center">
        在左侧把目的地、天数定好，<br />
        点「一键规划行程」，这里会逐日排出时间轴
      </div>
    </div>

    <template v-else>
      <!-- ===== 顶部胶囊：总览 / 第 N 天 / 待安排（与地图图例联动） ===== -->
      <div class="day-pills">
        <span
          v-if="plan.isMultiDay"
          class="day-pill"
          :class="{ active: plan.activeDay === 0 && !showPending }"
          @click="gotoDay(0)"
        >
          总览
        </span>
        <span
          v-for="d in plan.isMultiDay ? plan.dayList : []"
          :key="d.day"
          class="day-pill"
          :class="{ active: plan.activeDay === d.day && !showPending }"
          @click="gotoDay(d.day)"
        >
          第{{ d.day }}天
        </span>
        <span
          v-if="pendingSpots.length"
          class="day-pill pending"
          :class="{ active: showPending }"
          @click="showPending = !showPending"
        >
          待安排{{ pendingSpots.length }}
        </span>
      </div>

      <!-- ===== 总览：跨天汇总 + 每天一条可跳转摘要 ===== -->
      <template v-if="isOverview">
        <div class="ov-stats">
          <div class="ov-stat">
            <span class="ov-num">{{ plan.planResult.dayCount || 1 }}</span>
            <span class="ov-lab">天</span>
          </div>
          <div class="ov-stat">
            <span class="ov-num">{{ plan.planResult.totalDistanceKm }}</span>
            <span class="ov-lab">公里</span>
          </div>
          <div class="ov-stat">
            <span class="ov-num">{{ plan.planResult.spots?.length || 0 }}</span>
            <span class="ov-lab">个景点</span>
          </div>
          <div class="ov-stat">
            <span class="ov-num">{{ minutesToText(plan.planResult.totalDurationMin) }}</span>
            <span class="ov-lab">总时长</span>
          </div>
        </div>

        <div class="ov-day" v-for="d in plan.dayList" :key="d.day" @click="plan.activeDay = d.day">
          <div class="ov-day-bar" :style="{ background: d.color }" />
          <div class="ov-day-main">
            <div class="ov-day-title">{{ d.title }}</div>
            <div class="ov-day-meta">
              {{ d.spotCount }}个景点 · {{ d.totalDistanceKm }} km ·
              {{ minutesToText(d.totalDurationMin) }} · {{ d.startTime }}–{{ d.endTime }}
            </div>
            <div class="ov-day-spots">
              <span v-for="s in (d.spots || []).slice(0, 6)" :key="s.id" class="ov-spot">
                {{ s.name }}
              </span>
              <span v-if="(d.spots || []).length > 6" class="ov-spot ghost">
                +{{ d.spots.length - 6 }}
              </span>
            </div>
          </div>
          <span class="ov-day-go">›</span>
        </div>
      </template>

      <!-- ===== 待安排：被模式过滤掉、没排进行程的景点 ===== -->
      <template v-else-if="showPending">
        <div class="sub-text" style="line-height: 1.9; margin-bottom: 10px">
          这些景点在「{{ plan.planModeLabel }}」模式下没有排进行程（模式会按分类/热度筛点，或一天排不下）。
          想去的话切到「✋ 手动勾选」并勾上它们后重新规划。
        </div>
        <div class="tl-card" v-for="s in pendingSpots" :key="s.id" @click="emit('focus', s)">
          <div class="tl-thumb" :style="thumbStyle(s)">
            <span class="tl-thumb-sym" :style="{ color: categoryColor(s.category) }">
              {{ categorySymbol(s.category) }}
            </span>
          </div>
          <div class="tl-main">
            <div class="tl-name">{{ s.name }}</div>
            <div class="tl-meta">
              <span class="tl-heat">🔥{{ s.heat ?? 0 }}</span>
              {{ SPOT_CATEGORY_MAP[s.category] || '景点' }}
            </div>
            <div class="tl-time">{{ s.description || '暂无简介' }}</div>
          </div>
        </div>
      </template>

      <!-- ===== 单日 / 某一天：携程式时间轴 ===== -->
      <template v-else>
        <div class="day-head">
          <span class="day-head-title">
            {{ plan.isMultiDay ? plan.activeDayData.title : `${plan.city} · ${plan.planModeLabel}` }}
          </span>
          <el-tag size="small" :type="RISK_TAG[dayRisk] || 'success'" effect="light">
            {{ RISK_MAP[dayRisk] || '低风险' }}
          </el-tag>
        </div>
        <div class="day-summary">
          🛣️ {{ plan.activeDayData.totalDistanceKm }} km ｜
          ⏱️ {{ minutesToText(plan.activeDayData.totalDurationMin) }} ｜
          🕘 {{ plan.activeDayData.startTime }}出发
        </div>

        <div class="tl-list">
          <div v-for="(item, i) in plan.activeDayData.schedule || []" :key="item.seq" class="tl-item">
            <div class="tl-rail">
              <span class="tl-dot" :style="{ background: railColor }">{{ item.seq }}</span>
              <span class="tl-line" />
            </div>
            <div class="tl-body">
              <div class="tl-card" @click="focusItem(item)">
                <div class="tl-thumb" :style="thumbStyle(spotOf(item))">
                  <img v-if="spotOf(item)?.image" :src="spotOf(item).image" :alt="item.name" />
                  <span v-else class="tl-thumb-sym" :style="{ color: categoryColor(spotOf(item)?.category) }">
                    {{ categorySymbol(spotOf(item)?.category) }}
                  </span>
                </div>
                <div class="tl-main">
                  <div class="tl-name">{{ item.name }}</div>
                  <div class="tl-meta">
                    <span class="tl-heat">🔥{{ spotOf(item)?.heat ?? 0 }}</span>
                    <span>建议游玩{{ stayText(item.stayMin) }}</span>
                  </div>
                  <div class="tl-time">
                    🕘 {{ item.arrival }} – {{ item.depart }}
                    <span class="tl-cat">{{ SPOT_CATEGORY_MAP[spotOf(item)?.category] || '' }}</span>
                  </div>
                </div>
              </div>

              <div v-if="item.restAfter" class="tl-rest">{{ item.restAfter }}</div>

              <!-- 段间交通：形如「步行12分钟 · 840米」 -->
              <div v-if="segOf(i)" class="tl-leg">
                <span class="tl-leg-ico">{{ segOf(i).travelMode === 'RIDE' ? '🚕' : '🚶' }}</span>
                {{ segOf(i).travelMin }}分钟 · {{ kmText(segOf(i).distanceKm) }}
                <span v-if="segOf(i).realRoad" class="tl-leg-real">沿真实道路</span>
                <span class="tl-leg-to">→ {{ segOf(i).to }}</span>
              </div>
            </div>
          </div>
        </div>

        <el-alert
          v-if="plan.activeDayData.advice"
          type="warning"
          :closable="false"
          class="mt-12"
          :title="plan.activeDayData.advice"
          show-icon
        />
      </template>

      <!-- ===== 底部：建议 / 提示 / 保存 ===== -->
      <el-alert
        v-if="plan.planResult.advice && !isOverview"
        type="info"
        :closable="false"
        class="mt-12"
        :title="plan.planResult.advice"
        show-icon
      />
      <el-alert
        v-if="plan.planResult.tips"
        type="info"
        :closable="false"
        class="mt-12"
        :title="plan.planResult.tips"
        show-icon
      />
      <el-button
        type="success"
        plain
        class="mt-12"
        style="width: 100%"
        :loading="plan.savingRoute"
        @click="plan.saveRoute"
      >
        💾 {{ plan.isMultiDay ? '保存整条多日行程' : '保存路线到我的路线' }}
      </el-button>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  RISK_MAP,
  RISK_TAG,
  SPOT_CATEGORY_MAP,
  kmText,
  minutesToText
} from '@/utils/format'
import { CATEGORY_STYLE } from '@/utils/tmap'
import { useTripPlan } from '@/composables/useTripPlan'

const emit = defineEmits(['focus'])
const plan = useTripPlan()

// 「待安排」= 候选景点里没排进行程的那些（模式筛点/一天排不下）
const showPending = ref(false)
const gotoDay = (day) => {
  showPending.value = false
  plan.activeDay = day
}
const planSpotIds = computed(() => new Set((plan.planResult?.spots || []).map((s) => s.id)))
const pendingSpots = computed(() =>
  (plan.spots || []).filter((s) => !planSpotIds.value.has(s.id))
)

// 单日行程也复用「总览」视图（数据在同一层），多日需要手动切到总览
const isOverview = computed(() => plan.isMultiDay && plan.activeDay === 0 && !showPending.value)

const railColor = computed(() => plan.activeDayData?.color || '#2F80ED')
const dayRisk = computed(() => plan.activeDayData?.riskLevel || plan.planResult?.riskLevel)

// schedule只带spotId，图片/分类/热度要从规划结果里的景点表回查
const spotMap = computed(() => {
  const m = {}
  ;(plan.planResult?.spots || []).forEach((s) => (m[String(s.id)] = s))
  return m
})
const spotOf = (item) => spotMap.value[String(item?.spotId)] || { name: item?.name }
const segOf = (i) => (plan.activeDayData?.segments || [])[i]

const categoryMeta = (cat) => CATEGORY_STYLE[cat] || CATEGORY_STYLE.NATURE
const categorySymbol = (cat) => categoryMeta(cat).symbol
const categoryColor = (cat) => categoryMeta(cat).color

function thumbStyle(spot) {
  const c = categoryColor(spot?.category)
  return { background: `linear-gradient(135deg, ${c}22, ${c}0d)` }
}
function stayText(min) {
  const n = Number(min) || 0
  if (n < 60) return `${n}分钟`
  const h = n / 60
  return `${Number.isInteger(h) ? h : h.toFixed(1)}小时`
}
function focusItem(item) {
  const s = spotOf(item)
  if (s?.lng || s?.lat) emit('focus', s)
}

// 重新规划后回到总览/当天，别停在「待安排」上
watch(
  () => plan.planResult,
  () => {
    showPending.value = false
  }
)
</script>

<style scoped>
.tl-wrap {
  display: flex;
  flex-direction: column;
}

/* ---------- 空态 ---------- */
.tl-empty {
  padding: 48px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.tl-empty-icon {
  font-size: 40px;
}
.tl-empty-title {
  font-weight: 700;
  font-size: 15px;
}

/* ---------- 顶部胶囊 ---------- */
.day-pills {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  margin-bottom: 12px;
  scrollbar-width: none;
}
.day-pills::-webkit-scrollbar {
  display: none;
}
.day-pill {
  flex: none;
  padding: 5px 14px;
  border-radius: 999px;
  background: #f0f3f8;
  color: #5b6b82;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
}
.day-pill:hover {
  background: #e6ecf7;
}
.day-pill.active {
  background: linear-gradient(135deg, #2f80ed, #56a3f5);
  color: #fff;
  box-shadow: 0 4px 12px rgba(47, 128, 237, 0.28);
}
.day-pill.pending {
  border: 1px dashed #c9d4e4;
  background: #fff;
  color: #8a94a6;
}
.day-pill.pending.active {
  border-style: solid;
  border-color: #2f80ed;
}

/* ---------- 总览 ---------- */
.ov-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}
.ov-stat {
  background: #fff;
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  padding: 10px 6px;
  text-align: center;
}
.ov-num {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #2f80ed;
  line-height: 1.3;
}
.ov-lab {
  font-size: 11.5px;
  color: #8a94a6;
}
.ov-day {
  display: flex;
  gap: 10px;
  align-items: stretch;
  background: #fff;
  border: 1px solid var(--xlvq-border);
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}
.ov-day:hover {
  box-shadow: 0 8px 20px rgba(31, 45, 61, 0.1);
  transform: translateY(-1px);
}
.ov-day-bar {
  width: 4px;
  border-radius: 3px;
  flex: none;
}
.ov-day-main {
  flex: 1;
  min-width: 0;
}
.ov-day-title {
  font-weight: 700;
  font-size: 13.5px;
  margin-bottom: 4px;
}
.ov-day-meta {
  font-size: 12px;
  color: #8a94a6;
  margin-bottom: 8px;
}
.ov-day-spots {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ov-spot {
  font-size: 11.5px;
  color: #5b6b82;
  background: #f4f7fc;
  border-radius: 6px;
  padding: 2px 7px;
}
.ov-spot.ghost {
  color: #a8b3c4;
}
.ov-day-go {
  align-self: center;
  color: #b6c1d1;
  font-size: 20px;
}

/* ---------- 单日 ---------- */
.day-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.day-head-title {
  font-weight: 700;
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.day-summary {
  font-size: 12px;
  color: #8a94a6;
  margin-bottom: 12px;
}

/* ---------- 时间轴 ---------- */
.tl-list {
  display: flex;
  flex-direction: column;
}
.tl-item {
  display: flex;
  gap: 10px;
}
.tl-rail {
  flex: none;
  width: 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.tl-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: #fff;
  font-size: 11.5px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  box-shadow: 0 2px 6px rgba(31, 45, 61, 0.18);
}
.tl-line {
  flex: 1;
  width: 2px;
  background: #e3eaf5;
  margin: 4px 0;
  border-radius: 1px;
}
.tl-body {
  flex: 1;
  min-width: 0;
  padding-bottom: 6px;
}

.tl-card {
  display: flex;
  gap: 10px;
  background: #fff;
  border: 1px solid var(--xlvq-border);
  border-radius: 12px;
  padding: 10px;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease, border-color 0.2s ease;
}
.tl-card:hover {
  border-color: #cfe0fa;
  box-shadow: 0 8px 20px rgba(31, 45, 61, 0.1);
  transform: translateY(-1px);
}
.tl-thumb {
  width: 76px;
  height: 60px;
  border-radius: 8px;
  flex: none;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2f8;
}
.tl-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.tl-thumb-sym {
  font-size: 22px;
}
.tl-main {
  flex: 1;
  min-width: 0;
}
.tl-name {
  font-weight: 700;
  font-size: 13.5px;
  line-height: 1.45;
  margin-bottom: 5px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tl-meta {
  font-size: 12px;
  color: #6b7a90;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.tl-heat {
  color: #ff8a65;
  font-weight: 600;
}
.tl-time {
  font-size: 12px;
  color: #8a94a6;
  margin-top: 3px;
  display: flex;
  justify-content: space-between;
  gap: 6px;
}
.tl-cat {
  color: #a8b3c4;
  flex: none;
}
.tl-rest {
  font-size: 12px;
  color: #fa8c16;
  padding: 6px 0 0 6px;
}
.tl-leg {
  font-size: 12px;
  color: #8a94a6;
  padding: 8px 0 10px 6px;
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}
.tl-leg-ico {
  font-size: 13px;
}
.tl-leg-real {
  color: #52c41a;
}
.tl-leg-to {
  color: #b6c1d1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 130px;
}
</style>
