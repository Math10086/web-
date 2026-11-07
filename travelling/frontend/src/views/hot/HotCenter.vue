<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <div>
        <h2 class="page-title" style="margin: 0">🔥 热度中心 · 热搜与博主推荐</h2>
        <div class="sub-text" style="margin-top: 4px">
          热搜词与榜单由平台真实互动行为（浏览 / 点赞 / 收藏 / 引用）加权计算，规则公开可解释
        </div>
      </div>
      <el-button :icon="Refresh" @click="loadAll" :loading="loading">刷新</el-button>
    </div>

    <!-- 精品博主推荐位：按引用量和浏览量自动进入 -->
    <div v-if="featured.length" class="featured-row mb-16">
      <div
        v-for="(b, i) in featured"
        :key="b.id"
        class="x-card featured-card"
        @click="goBlogger(b)"
      >
        <div class="featured-flag">{{ i === 0 ? '推荐位 ①' : '推荐位 ②' }}</div>
        <el-avatar :size="52" :src="b.avatar">{{ (b.nickname || '博')[0] }}</el-avatar>
        <div class="featured-main">
          <div class="featured-name">
            {{ b.nickname }}
            <el-tag size="small" :type="levelTag(b.level)" effect="dark">
              {{ b.levelName }}
            </el-tag>
          </div>
          <div class="sub-text">{{ SCHOOL_MAP[b.school] || '—' }} · 作品{{ b.postCount }}篇</div>
          <div class="featured-stats">
            <span>👁 {{ b.views }}</span>
            <span>👍 {{ b.likes }}</span>
            <span>⭐ {{ b.favorites }}</span>
            <span>🔗 被引用{{ b.citations }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="hot-grid">
      <!-- 词云 -->
      <div class="x-card hot-card">
        <div class="card-title">☁️ 社区热词云</div>
        <div v-if="keywords.length" class="cloud">
          <span
            v-for="w in keywords"
            :key="w.name"
            class="cloud-word"
            :style="{
              fontSize: cloudSize(w.value) + 'px',
              color: categoryColor(w.category),
              fontWeight: w.value >= maxKeyword * 0.6 ? 700 : 500
            }"
            :title="`热度${w.value}`"
          >
            {{ w.name }}
          </span>
        </div>
        <el-empty v-else description="暂无词云数据" :image-size="80" />
        <div class="cloud-legend">
          <span v-for="(c, k) in CATEGORY_LEGEND" :key="k" class="legend-item">
            <i :style="{ background: c }"></i>{{ CATEGORY_NAME[k] || k }}
          </span>
        </div>
      </div>

      <!-- 热帖榜 -->
      <div class="x-card hot-card">
        <div class="card-title">📈 论坛热帖榜 Top10</div>
        <div v-if="posts.length" class="rank-list">
          <div v-for="p in posts" :key="p.id" class="rank-item" @click="goPost(p)">
            <span class="rank-badge" :class="'rank-' + Math.min(p.rank, 4)">{{ p.rank }}</span>
            <div class="rank-body">
              <div class="rank-title">
                {{ p.title }}
                <el-tag v-if="p.trustedBadge" size="small" type="success" effect="plain">本地认证</el-tag>
              </div>
              <div class="sub-text rank-meta">
                {{ p.author?.nickname || '匿名' }} · {{ SCHOOL_MAP[p.author?.school] || '—' }}
                <template v-if="p.destination"> · 📍{{ p.destination }}</template>
              </div>
            </div>
            <div class="rank-heat">
              <div class="heat-value">{{ p.heat }}</div>
              <div class="sub-text">👁{{ p.views }} 👍{{ p.likes }} ⭐{{ p.favorites }}</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无帖子" :image-size="80" />
      </div>

      <!-- 热门城市榜 -->
      <div class="x-card hot-card">
        <div class="card-title">🏙️ 热门目的地城市榜</div>
        <div v-if="cities.length" class="city-list">
          <div v-for="(c, i) in cities" :key="c.city" class="city-item">
            <span class="city-rank">{{ i + 1 }}</span>
            <span class="city-name">{{ c.city }}</span>
            <div class="city-bar-wrap">
              <div class="city-bar" :style="{ width: cityBarWidth(c.heat) + '%' }"></div>
            </div>
            <span class="city-count">{{ c.heat }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无城市数据" :image-size="80" />
        <div class="sub-text" style="margin-top: 8px">
          热度 = 攻略×3+ 结伴×5+ 路线×2+ 足迹×4
        </div>
      </div>

      <!-- 博主评级榜 -->
      <div class="x-card hot-card">
        <div class="card-title">🏅 博主评级榜（浏览量 / 引用量加权）</div>
        <div v-if="bloggers.length" class="blogger-list">
          <div v-for="b in bloggers" :key="b.id" class="blogger-item" @click="goBlogger(b)">
            <span class="rank-badge" :class="'rank-' + Math.min(b.rank, 4)">{{ b.rank }}</span>
            <el-avatar :size="34" :src="b.avatar">{{ (b.nickname || '博')[0] }}</el-avatar>
            <div class="blogger-body">
              <div class="blogger-name">
                {{ b.nickname }}
                <el-tag size="small" :type="levelTag(b.level)" effect="plain">{{ b.levelName }}</el-tag>
              </div>
              <div class="sub-text">
                {{ SCHOOL_MAP[b.school] || '—' }} · {{ b.postCount }}篇 · 🔗{{ b.citations }}
              </div>
            </div>
            <div class="blogger-score">
              <div class="heat-value">{{ b.score }}</div>
              <div class="sub-text">博主分</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无博主数据" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { hotApi } from '@/api'
import { SCHOOL_MAP } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const posts = ref([])
const keywords = ref([])
const cities = ref([])
const bloggers = ref([])
const featured = ref([])

const CATEGORY_COLOR = {
  CITY: '#2f80ed',
  SPOT: '#52c41a',
  FOOD: '#ff7043',
  OUTDOOR: '#7e57c2',
  ACTIVITY: '#26a69a',
  SCENIC: '#ffa726',
  GUIDE: '#8d6e63',
  SOCIAL: '#ec407a',
  THEME: '#5c6bc0',
  OTHER: '#78909c'
}
const CATEGORY_LEGEND = {
  CITY: CATEGORY_COLOR.CITY,
  SPOT: CATEGORY_COLOR.SPOT,
  FOOD: CATEGORY_COLOR.FOOD,
  OUTDOOR: CATEGORY_COLOR.OUTDOOR,
  ACTIVITY: CATEGORY_COLOR.ACTIVITY,
  SCENIC: CATEGORY_COLOR.SCENIC
}
const CATEGORY_NAME = {
  CITY: '目的地城市',
  SPOT: '景点',
  FOOD: '美食',
  OUTDOOR: '户外',
  ACTIVITY: '玩法',
  SCENIC: '人文自然'
}

const maxKeyword = computed(() =>
  keywords.value.reduce((m, w) => Math.max(m, w.value), 1)
)
const maxCityHeat = computed(() =>
  cities.value.reduce((m, c) => Math.max(m, c.heat), 1)
)

function cloudSize(v) {
  const min = 14
  const max = 38
  return Math.round(min + (v / maxKeyword.value) * (max - min))
}
function categoryColor(cat) {
  return CATEGORY_COLOR[cat] || CATEGORY_COLOR.OTHER
}
function cityBarWidth(heat) {
  return Math.max(6, Math.round((heat / maxCityHeat.value) * 100))
}
function levelTag(level) {
  const map = { 0: 'info', 1: 'info', 2: '', 3: 'success', 4: 'warning', 5: 'danger' }
  return map[level] ?? 'info'
}

async function loadAll() {
  loading.value = true
  try {
    const [p, k, c, b] = await Promise.all([
      hotApi.posts(10).catch(() => []),
      hotApi.keywords(40).catch(() => []),
      hotApi.cities(12).catch(() => []),
      hotApi.bloggers(15).catch(() => ({ list: [], featured: [] }))
    ])
    posts.value = p || []
    keywords.value = k || []
    cities.value = c || []
    bloggers.value = b?.list || []
    featured.value = b?.featured || []
  } finally {
    loading.value = false
  }
}

function goPost(p) {
  router.push(`/posts/${p.id}`)
}
function goBlogger(b) {
  router.push(`/profile?id=${b.id}`)
}

onMounted(loadAll)
</script>

<style scoped>
.featured-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}
.featured-card {
  position: relative;
  display: flex;
  gap: 14px;
  align-items: center;
  cursor: pointer;
  border: 1px solid var(--xlvq-border);
  background: linear-gradient(135deg, #fffdf7 0%, #ffffff 60%);
}
.featured-flag {
  position: absolute;
  top: 0;
  right: 0;
  background: linear-gradient(135deg, #ffb300, #ff8a65);
  color: #fff;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 0 var(--xlvq-radius) 0 12px;
}
.featured-main {
  flex: 1;
  min-width: 0;
}
.featured-name {
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.featured-stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--xlvq-text-sub);
  margin-top: 6px;
  flex-wrap: wrap;
}
.hot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 16px;
}
.hot-card {
  min-height: 320px;
}
.cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  justify-content: center;
  padding: 18px 6px;
  min-height: 200px;
}
.cloud-word {
  line-height: 1.1;
  cursor: default;
  transition: transform 0.15s;
}
.cloud-word:hover {
  transform: scale(1.12);
}
.cloud-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-top: 6px;
  font-size: 12px;
  color: var(--xlvq-text-sub);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.legend-item i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}
.rank-list,
.blogger-list,
.city-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rank-item,
.blogger-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.rank-item:hover,
.blogger-item:hover {
  background: var(--xlvq-primary-light);
}
.rank-badge {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: #eef2f7;
  color: #8a99ad;
  flex-shrink: 0;
}
.rank-1 {
  background: linear-gradient(135deg, #ffb300, #ff8a65);
  color: #fff;
}
.rank-2 {
  background: linear-gradient(135deg, #b0bec5, #90a4ae);
  color: #fff;
}
.rank-3 {
  background: linear-gradient(135deg, #d7a86e, #c08b4f);
  color: #fff;
}
.rank-body,
.blogger-body {
  flex: 1;
  min-width: 0;
}
.rank-title {
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-meta,
.blogger-name {
  font-size: 12px;
}
.blogger-name {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}
.rank-heat,
.blogger-score {
  text-align: right;
  flex-shrink: 0;
}
.heat-value {
  font-weight: 700;
  color: var(--xlvq-orange);
  font-size: 15px;
}
.city-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.city-rank {
  width: 18px;
  color: var(--xlvq-text-sub);
  text-align: center;
}
.city-name {
  width: 62px;
  font-weight: 600;
}
.city-bar-wrap {
  flex: 1;
  height: 10px;
  background: #f0f3f8;
  border-radius: 6px;
  overflow: hidden;
}
.city-bar {
  height: 100%;
  background: linear-gradient(90deg, #69a6f8, var(--xlvq-primary));
  border-radius: 6px;
}
.city-count {
  width: 44px;
  text-align: right;
  color: var(--xlvq-text-sub);
}
</style>
