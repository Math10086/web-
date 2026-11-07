<template>
  <div class="page-wrap">
    <div class="flex-between mb-16" style="flex-wrap: wrap; gap: 8px">
      <h2 class="page-title" style="margin: 0">✍️ 创作者中心</h2>
      <span class="sub-text">你的帖子表现 · 选题建议，只对你本人可见</span>
    </div>

    <el-empty v-if="!loading && !data.me" description="暂无数据" />

    <template v-if="data.me">
      <!-- 概览：账号成绩 -->
      <div class="x-card mb-16">
        <div class="flex-between" style="flex-wrap: wrap; gap: 12px">
          <div>
            <div style="font-size: 15px; font-weight: 500">
              {{ data.me.levelName }}
              <el-tag size="small" type="success" effect="plain" class="ml-8">
                平台第{{ data.me.rank }} / {{ data.me.rankTotal }}名
              </el-tag>
            </div>
            <div class="sub-text mt-12">
              博客分{{ data.me.score }}分 · 已发布{{ data.me.postCount }}篇
              <span v-if="data.me.draftCount">（另有{{ data.me.draftCount }}篇待审核/未通过）</span>
            </div>
          </div>
          <div v-if="data.me.nextLevelName" class="sub-text" style="text-align: right">
            距「{{ data.me.nextLevelName }}」还差
            <strong style="color: var(--xlvq-primary)">
              {{ Math.max(0, (data.me.nextLevelScore || 0) - data.me.score) }}
            </strong>
            分
            <div style="margin-top: 6px">
              <el-progress
                :percentage="levelProgress"
                :stroke-width="8"
                :show-text="false"
                style="width: 180px"
              />
            </div>
          </div>
        </div>

        <div class="kpi-row mt-16">
          <div class="kpi-item">
            <div class="kpi-num">{{ data.me.views }}</div>
            <div class="sub-text">总浏览</div>
          </div>
          <div class="kpi-item">
            <div class="kpi-num">{{ data.me.likes }}</div>
            <div class="sub-text">获赞</div>
          </div>
          <div class="kpi-item">
            <div class="kpi-num">{{ data.me.favorites }}</div>
            <div class="sub-text">被收藏</div>
          </div>
          <div class="kpi-item">
            <div class="kpi-num">{{ data.me.citations }}</div>
            <div class="sub-text">被引用</div>
          </div>
          <div class="kpi-item">
            <div class="kpi-num">{{ data.me.favoriteRate }}</div>
            <div class="sub-text">每千次阅读收藏数</div>
          </div>
        </div>
      </div>

      <!-- 结论 -->
      <div v-if="data.insights?.length" class="x-card mb-16">
        <div class="card-title">🧠 数据说了什么</div>
        <div v-for="(t, i) in data.insights" :key="i" class="insight-line">
          <span class="dot"></span>
          <span>{{ t }}</span>
        </div>
      </div>

      <div class="grid-2">
        <!-- 月度趋势 -->
        <div class="x-card">
          <div class="card-title">📈 每月浏览量</div>
          <div v-if="!data.trend?.length" class="sub-text">还没有足够的数据</div>
          <div v-show="data.trend?.length" ref="trendEl" style="height: 220px"></div>
        </div>

        <!-- 选题建议 -->
        <div class="x-card">
          <div class="card-title">💡 下一篇写什么</div>
          <div class="sub-text mb-16">
            站内正在升温、但你还没写过的主题（热度与首页词云同源）
          </div>
          <el-empty
            v-if="!data.topicIdeas?.length"
            description="热门话题你都写过，可以尝试换个角度"
            :image-size="60"
          />
          <div v-for="t in data.topicIdeas" :key="t.keyword" class="idea-item">
            <div class="flex-between">
              <strong>{{ t.keyword }}</strong>
              <span class="sub-text">热度{{ t.heat }}</span>
            </div>
            <div class="sub-text">{{ t.reason }}</div>
          </div>
        </div>
      </div>

      <!-- 逐篇表现 -->
      <div class="x-card mt-16">
        <div class="card-title">📄 逐篇表现</div>
        <div class="sub-text mb-16">
          「每千次阅读收藏数」越高，说明内容越有用——它不受曝光量影响，适合拿来比较不同选题
        </div>
        <el-table :data="data.posts" size="small" style="width: 100%">
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="city" label="城市" width="90" />
          <el-table-column prop="views" label="浏览" width="80" sortable />
          <el-table-column prop="likes" label="赞" width="70" sortable />
          <el-table-column prop="favorites" label="收藏" width="70" sortable />
          <el-table-column prop="citations" label="引用" width="70" sortable />
          <el-table-column label="千阅读收藏" width="110" sortable prop="favoriteRate">
            <template #default="{ row }">
              <span :class="row.favoriteRate >= data.me.favoriteRate ? 'good' : 'normal'">
                {{ row.favoriteRate }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="" width="80">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openPost(row.id)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { creatorApi } from '@/api'

const router = useRouter()
const data = ref({})
const loading = ref(true)
const trendEl = ref(null)
let chart = null

// 升级进度由后端按统一阈值算好下发（levelProgress），前端不再维护一份等级门槛
const levelProgress = computed(() => {
  const p = data.value.me?.levelProgress
  return typeof p === 'number' ? Math.max(0, Math.min(100, p)) : 100
})

function renderTrend() {
  const rows = data.value.trend || []
  if (!rows.length || !trendEl.value) return
  if (chart) chart.dispose()
  chart = echarts.init(trendEl.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 20, bottom: 28 },
    xAxis: { type: 'category', data: rows.map((r) => r.month) },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.views),
        areaStyle: { opacity: 0.15 },
        lineStyle: { width: 2 },
        itemStyle: { color: '#378ADD' },
        symbolSize: 6
      }
    ]
  })
}

function openPost(id) {
  router.push(`/posts/${id}`)
}

onMounted(async () => {
  try {
    data.value = (await creatorApi.insight()) || {}
  } finally {
    loading.value = false
  }
  await nextTick()
  renderTrend()
  window.addEventListener('resize', onResize)
})

function onResize() {
  chart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.kpi-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  border-top: 1px solid var(--el-border-color-lighter, #eee);
  padding-top: 16px;
}
.kpi-item {
  flex: 1 1 100px;
  text-align: center;
}
.kpi-num {
  font-size: 20px;
  font-weight: 500;
  color: var(--xlvq-primary, #2f80ed);
}
.insight-line {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  line-height: 1.7;
  font-size: 13px;
  padding: 4px 0;
}
.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--xlvq-primary, #2f80ed);
  margin-top: 9px;
  flex-shrink: 0;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 900px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
.idea-item {
  border: 1px solid var(--el-border-color-lighter, #eee);
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
}
.good {
  color: #1d9e75;
  font-weight: 500;
}
.normal {
  color: #888;
}
</style>
