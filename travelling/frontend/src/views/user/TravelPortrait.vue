<template>
  <div class="page-wrap" v-loading="loading">
    <!-- ==================== ① 画像头部 ==================== -->
    <div class="x-card mb-16">
      <div class="portrait-head">
        <el-avatar :size="72" :src="p.user?.avatar">
          {{ p.user?.nickname?.charAt(0) || '旅' }}
        </el-avatar>
        <div class="head-main">
          <div class="head-name">
            <h2 class="head-title">{{ p.user?.nickname || '旅行者' }}</h2>
            <el-tag v-if="p.user?.verified" size="small" type="success" effect="plain">已认证</el-tag>
            <el-tag size="small" :type="creditTagType(p.user?.score)" effect="dark">
              信誉{{ p.user?.score ?? '—' }} · {{ p.user?.levelName || '—' }}
            </el-tag>
          </div>
          <div class="sub-text mt-8">
            {{ SCHOOL_MAP[p.user?.school] || '未填写学校' }}
            <template v-if="p.user?.studentNo"> · 学号{{ p.user.studentNo }}</template>
          </div>
          <div class="head-progress mt-8">
            <span class="sub-text">画像完整度</span>
            <el-progress
              class="progress-bar"
              :percentage="completenessPct"
              :stroke-width="10"
              :color="progressColor"
            />
          </div>
          <div class="sub-text mt-8" v-if="missing.length">
            补全「{{ missing.slice(0, 3).join('、') }}」等{{ missing.length }}项，可显著提升组队推荐精度
          </div>
        </div>
      </div>

      <!-- 出行统计 -->
      <div class="stat-row mt-16">
        <div class="stat-item" v-for="s in statCards" :key="s.label">
          <div class="stat-num">{{ s.value }}</div>
          <div class="sub-text">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- ==================== ② 兴趣标签 + 节奏偏好 ==================== -->
    <div class="two-col mb-16">
      <div class="x-card">
        <h3 class="block-title">兴趣标签画像</h3>
        <div class="sub-text mb-12">
          <el-tag size="small" effect="plain">自述</el-tag> 来自结伴偏好设置 ·
          <el-tag size="small" type="success" effect="plain">行为</el-tag> 来自你实际参加的行程
        </div>
        <div v-if="tagCloud.length" class="tag-cloud">
          <el-tag
            v-for="tag in tagCloud"
            :key="tag.name"
            :type="prefTagSet.has(tag.name) ? 'primary' : 'success'"
            :effect="prefTagSet.has(tag.name) ? 'plain' : 'light'"
            class="tag-item"
            :style="{ fontSize: tagFontSize(tag.count) + 'px' }"
          >
            {{ tag.name }}
            <span class="tag-count">×{{ tag.count }}</span>
          </el-tag>
        </div>
        <el-empty v-else description="还没有兴趣标签，去「结伴出行」设置偏好" :image-size="70" />
      </div>

      <div class="x-card">
        <h3 class="block-title">游玩节奏：自述 vs 实际</h3>
        <div class="pace-row mb-12">
          <span class="sub-text">自述偏好</span>
          <el-tag size="small" effect="dark">{{ p.paceProfile?.statedName || '节奏适中' }}</el-tag>
          <el-tag
            size="small"
            :type="p.paceProfile?.consistent ? 'success' : 'warning'"
            effect="plain"
          >
            {{ p.paceProfile?.consistent ? '与实际一致' : '与实际有偏差' }}
          </el-tag>
        </div>
        <div v-for="row in p.paceProfile?.actual || []" :key="row.pace" class="bar-row">
          <span class="bar-label">{{ row.name }}</span>
          <div class="bar-wrap">
            <div
              class="bar-fill"
              :style="{ width: paceWidth(row.count) + '%', background: barColor(row.pace) }"
            ></div>
          </div>
          <span class="bar-value">{{ row.count }}</span>
        </div>
        <div
          v-if="!p.paceProfile?.actual?.some((r) => r.count > 0)"
          class="sub-text mt-12"
        >
          还没有实际行程记录，参加一次结伴后这里会出现真实节奏分布
        </div>
      </div>
    </div>

    <!-- ==================== ③ 消费画像 ==================== -->
    <div class="x-card mb-16">
      <h3 class="block-title">消费画像（记账数据 · 个人承担口径）</h3>
      <div class="stat-row mt-8 mb-16">
        <div class="stat-item">
          <div class="stat-num">¥{{ expense.totalSpend ?? 0 }}</div>
          <div class="sub-text">累计消费</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">¥{{ expense.avgPerTrip ?? 0 }}</div>
          <div class="sub-text">场均花费</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">¥{{ expense.maxSingle ?? 0 }}</div>
          <div class="sub-text">单笔最高分摊</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">
            {{ expense.budgetUsage == null ? '—' : Math.round(expense.budgetUsage * 100) + '%' }}
          </div>
          <div class="sub-text">预算使用率</div>
        </div>
      </div>

      <div class="two-chart">
        <div>
          <div class="sub-text mb-8">消费类别构成</div>
          <div ref="catEl" class="chart-box"></div>
        </div>
        <div>
          <div class="sub-text mb-8">月度消费趋势</div>
          <div ref="monthEl" class="chart-box"></div>
        </div>
      </div>

      <template v-if="expense.topExpenses?.length">
        <h3 class="block-title mt-16">单笔支出 TOP5</h3>
        <el-table :data="expense.topExpenses" size="small" empty-text="还没有消费记录">
          <el-table-column label="项目" prop="title" min-width="180" show-overflow-tooltip />
          <el-table-column label="类别" width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.categoryName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="口径" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.scope === 'TEAM' ? 'warning' : 'info'">
                {{ row.scope === 'TEAM' ? '团队分摊' : '个人' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110">
            <template #default="{ row }">¥{{ row.amount }}</template>
          </el-table-column>
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="记一笔消费后，这里会生成你的消费画像" :image-size="70" />
    </div>

    <!-- ==================== ④ 目的地 + 同伴 ==================== -->
    <div class="two-col mb-16">
      <div class="x-card">
        <h3 class="block-title">目的地画像</h3>
        <el-table
          v-if="p.destinations?.length"
          :data="p.destinations"
          size="small"
          empty-text="还没有行程记录"
        >
          <el-table-column label="城市" prop="city" min-width="110" />
          <el-table-column label="结伴次数" width="90">
            <template #default="{ row }">{{ row.count }}</template>
          </el-table-column>
          <el-table-column label="打卡景点" width="90">
            <template #default="{ row }">{{ row.spots }}</template>
          </el-table-column>
          <el-table-column label="最近一次" width="120">
            <template #default="{ row }">{{ formatDate(row.lastAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="参加一次结伴，这里会出现你的足迹地图" :image-size="70" />
      </div>

      <div class="x-card">
        <h3 class="block-title">同伴画像（一起结伴最多的人）</h3>
        <div v-if="p.companions?.length" class="companion-grid">
          <div v-for="c in p.companions" :key="c.userId" class="companion-item">
            <el-avatar :size="44" :src="c.avatar">{{ c.nickname?.charAt(0) }}</el-avatar>
            <div class="companion-name">{{ c.nickname }}</div>
            <div class="sub-text">同行{{ c.count }}次</div>
          </div>
        </div>
        <el-empty v-else description="还没有一起结过伴的同学" :image-size="70" />
      </div>
    </div>

    <!-- ==================== ⑤ 洞察与数据价值 ==================== -->
    <div class="two-col">
      <div class="x-card">
        <h3 class="block-title">给你的出行洞察</h3>
        <div v-if="p.insights?.length" class="insight-list">
          <div v-for="(it, i) in p.insights" :key="i" class="insight-item">
            <span class="insight-dot"></span>
            <span>{{ it }}</span>
          </div>
        </div>
        <el-empty v-else description="多参加几次结伴，洞察会更准" :image-size="70" />
      </div>

      <div class="x-card">
        <h3 class="block-title">数据价值：从用户中来，到业务中去</h3>
        <div class="loop-flow">
          <div class="loop-node">用户行为</div>
          <span class="loop-arrow">→</span>
          <div class="loop-node">画像标签</div>
          <span class="loop-arrow">→</span>
          <div class="loop-node">精准匹配</div>
          <span class="loop-arrow">→</span>
          <div class="loop-node loop-last">同业合作</div>
        </div>
        <div class="sub-text mb-12">
          你的画像先反哺自己（更准的组队推荐与消费优惠），聚合后的群体偏好再输出给
          旅行社 / 酒店 / 景点，换取更贴合学生的线路与价格——一个双向、循环、有来有回的数据闭环。
        </div>
        <div v-if="coopHints.length" class="insight-list">
          <div v-for="(h, i) in coopHints" :key="i" class="insight-item">
            <span class="insight-dot loop-dot"></span>
            <span>{{ h }}</span>
          </div>
        </div>
        <el-alert
          class="mt-12"
          type="info"
          :closable="false"
          title="隐私边界：画像仅本人可见；对外只提供脱敏后的群体统计，不输出任何个人明细"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { portraitApi } from '@/api'
import { SCHOOL_MAP, creditTagType, formatDate, formatTime } from '@/utils/format'

const loading = ref(false)
const p = ref({})
const catEl = ref(null)
const monthEl = ref(null)
const charts = []

const expense = computed(() => p.value.expenseProfile || {})
const tagCloud = computed(() => p.value.tagCloud || [])
const missing = computed(() => p.value.dataValue?.missing || [])
const coopHints = computed(() => p.value.dataValue?.coopHints || [])
const completenessPct = computed(() =>
  Math.round((p.value.dataValue?.completeness ?? 0) * 100)
)
const prefTagSet = computed(
  () => new Set((p.value.pref?.interestTags || []).map((t) => String(t)))
)

const statCards = computed(() => {
  const s = p.value.stats || {}
  return [
    { label: '结伴行程', value: s.tripCount ?? 0 },
    { label: '担任队长', value: s.captainCount ?? 0 },
    { label: '已完成', value: s.finishedCount ?? 0 },
    { label: '进行中', value: s.ongoingCount ?? 0 },
    { label: '去过城市', value: s.cityCount ?? 0 },
    { label: '打卡景点', value: s.spotCount ?? 0 },
    { label: '累计天数', value: s.totalDays ?? 0 }
  ]
})

const progressColor = computed(() =>
  completenessPct.value >= 80 ? '#52C41A' : completenessPct.value >= 50 ? '#FA8C16' : '#F5222D'
)

function tagFontSize(count) {
  const max = Math.max(...(p.value.tagCloud || []).map((t) => t.count || 1), 1)
  return Math.round(12 + Math.min(6, (count / max) * 6))
}

const maxPace = computed(() =>
  Math.max(...(p.value.paceProfile?.actual || []).map((r) => r.count || 0), 1)
)
function paceWidth(c) {
  return Math.round(((c || 0) / maxPace.value) * 100)
}
function barColor(pace) {
  return { ENERGETIC: '#F5222D', BALANCED: '#2F80ED', RELAXED: '#52C41A' }[pace] || '#2F80ED'
}

async function load() {
  loading.value = true
  try {
    p.value = (await portraitApi.me().catch(() => ({}))) || {}
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function makeChart(el) {
  const c = echarts.init(el)
  charts.push(c)
  return c
}

function renderCharts() {
  const cats = (expense.value.byCategory || []).filter((c) => (c.amount || 0) > 0)
  if (catEl.value) {
    makeChart(catEl.value).setOption(
      {
        tooltip: { trigger: 'item', formatter: '{b}：¥{c}（{d}%）' },
        legend: { bottom: 0, type: 'scroll' },
        series: [
          {
            type: 'pie',
            radius: ['40%', '66%'],
            center: ['50%', '45%'],
            label: { formatter: '{b}\n¥{c}' },
            data: cats.length
              ? cats.map((c) => ({ name: c.name, value: c.amount }))
              : [{ name: '暂无消费', value: 1, itemStyle: { color: '#e8e8e8' } }]
          }
        ]
      },
      true
    )
  }
  const months = expense.value.byMonth || []
  if (monthEl.value) {
    makeChart(monthEl.value).setOption(
      {
        tooltip: { trigger: 'axis', valueFormatter: (v) => '¥' + v },
        grid: { left: 50, right: 20, top: 30, bottom: 30 },
        xAxis: { type: 'category', data: months.map((m) => m.month) },
        yAxis: { type: 'value' },
        series: [
          {
            type: 'bar',
            data: months.map((m) => m.amount),
            // 柱宽占槽位55%（上限40px），月度柱子更饱满、间距更小
            barWidth: '55%',
            barMaxWidth: 40,
            itemStyle: { color: '#2F80ED', borderRadius: [6, 6, 0, 0] },
            label: { show: months.length > 0, position: 'top', formatter: '¥{c}' }
          }
        ]
      },
      true
    )
  }
}

function onResize() {
  charts.forEach((c) => c.resize())
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.portrait-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.head-main {
  flex: 1;
  min-width: 0;
}
.head-name {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.head-title {
  margin: 0;
  font-size: 20px;
}
.head-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 460px;
}
.progress-bar {
  flex: 1;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat-item {
  text-align: center;
  padding: 10px 4px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary);
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .two-col,
  .two-chart {
    grid-template-columns: 1fr;
  }
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
.block-title {
  margin: 0 0 12px;
  font-size: 15px;
}
.two-chart {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag-item {
  height: auto;
  padding: 4px 10px;
}
.tag-count {
  opacity: 0.7;
  margin-left: 2px;
}
.pace-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.bar-label {
  width: 76px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  flex-shrink: 0;
}
.bar-wrap {
  flex: 1;
  height: 10px;
  border-radius: 5px;
  background: var(--el-fill-color);
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 5px;
  transition: width 0.4s ease;
}
.bar-value {
  width: 26px;
  text-align: right;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.companion-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
@media (max-width: 600px) {
  .companion-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.companion-item {
  text-align: center;
  padding: 10px 4px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.companion-name {
  font-size: 13px;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.insight-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.insight-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.6;
}
.insight-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
  margin-top: 6px;
  flex-shrink: 0;
}
.loop-dot {
  background: var(--el-color-success);
}
.loop-flow {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.loop-node {
  padding: 6px 12px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 13px;
}
.loop-last {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.loop-arrow {
  color: var(--el-text-color-secondary);
}
</style>
