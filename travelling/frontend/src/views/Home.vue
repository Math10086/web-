<template>
  <div class="page-wrap">
    <!-- 开屏欢迎横幅 -->
    <div class="gradient-banner mb-16">
      <h1>青春在路上，三校一起出发</h1>
      <p>
        中南大学 · 湖南师范大学 · 湖南大学 学生专属旅游社区<br />
        找圈层、看攻略、结伴同行、规划路线、记录足迹——家校知情，信誉护航，安全出游
      </p>
      <div class="mt-12" style="display: flex; gap: 10px; position: relative; z-index: 2">
        <!-- 快捷入口按身份展示：结伴 / 路线 / 论坛属 C 端前台能力，商家为纯商业合作方，改给商业入口 -->
        <template v-if="auth.isMerchant">
          <el-button round size="large" type="primary" @click="$router.push('/merchant')">
            🏪 商家中心
          </el-button>
          <el-button round size="large" @click="$router.push('/mall')">
            🛍️ 去商城逛逛
          </el-button>
          <el-button round size="large" @click="$router.push('/orders')">
            📦 店铺订单
          </el-button>
        </template>
        <template v-else>
          <el-button round size="large" @click="$router.push('/trips')">
            🚩 去找结伴
          </el-button>
          <el-button round size="large" @click="$router.push('/map')">
            🗺️ 规划路线
          </el-button>
          <el-button round size="large" @click="$router.push('/forum')">
            💬 本地论坛
          </el-button>
        </template>
      </div>
    </div>

    <div class="home-layout">
      <div class="home-main">
        <!-- 把个人画像里最有参考价值的一两条结论推到首页，用户不用进画像页也能看到 -->
        <div v-if="showDigest" class="x-card mb-16 digest-card">
          <div class="card-title">
            ✨ 平台对你的解读
            <span class="more-link" @click="$router.push('/portrait')">查看完整画像 →</span>
          </div>
          <div v-for="(t, i) in digest" :key="i" class="digest-line">
            <span class="digest-dot"></span>
            <span>{{ t }}</span>
          </div>
          <div v-if="digestEmpty" class="sub-text">
            多发几条结伴、多记几个足迹，这里就会开始有内容
          </div>
        </div>
        <!-- Banner 广告轮播 -->
        <el-card v-if="banners.length" shadow="never" body-style="padding:10px">
          <el-carousel
            height="260px"
            arrow="hover"
            indicator-position="outside"
            @change="onBannerChange"
          >
            <el-carousel-item
              v-for="ad in banners"
              :key="ad.id"
              @click="onAdClick(ad)"
            >
              <div class="banner-slide">
                <el-tag class="ad-flag" type="warning" effect="dark">广告</el-tag>
                <img :src="ad.image" :alt="ad.title" />
                <div class="banner-title">{{ ad.title }}</div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </el-card>

        <!-- 商业合作入口（商家视角：首页只保留经营相关入口，不展示社区内容） -->
        <div v-if="auth.isMerchant" class="x-card">
          <div class="card-title">
            🤝 商业合作中心
            <span class="more-link" @click="$router.push('/merchant')">进入商家中心 →</span>
          </div>
          <div class="post-grid">
            <div
              v-for="biz in BIZ_ENTRIES"
              :key="biz.title"
              class="x-card hoverable"
              style="padding: 16px"
              @click="$router.push(biz.path)"
            >
              <div style="font-size: 26px">{{ biz.icon }}</div>
              <div class="mt-12" style="font-weight: 700">{{ biz.title }}</div>
              <div class="sub-text">{{ biz.desc }}</div>
            </div>
          </div>
        </div>

        <!-- 热搜榜 -->
        <div v-if="!auth.isMerchant" class="x-card">
          <div class="card-title">
            🔥 论坛热搜榜
            <span class="more-link">基于浏览、点赞、收藏、引用加权</span>
          </div>
          <el-space wrap v-if="hotWords.length">
            <el-tag
              v-for="(w, i) in hotWords"
              :key="i"
              :type="i < 3 ? 'danger' : i < 6 ? 'warning' : 'info'"
              effect="plain"
              size="large"
              style="cursor: pointer"
              @click="searchKeyword(w)"
            >
              {{ i < 3 ? `Top${i + 1} ` : '' }}{{ w }}
            </el-tag>
          </el-space>
          <el-empty v-else description="暂无热搜数据" :image-size="60" />
        </div>

        <!-- 圈层入口 -->
        <div v-if="!auth.isMerchant" class="x-card">
          <div class="card-title">
            🎯 兴趣圈层
            <span class="more-link" @click="$router.push('/circles')">全部圈层 →</span>
          </div>
          <div class="circle-scroll">
            <div
              v-for="c in circles"
              :key="c.id"
              class="circle-mini"
              @click="$router.push(`/circles/${c.id}`)"
            >
              <div class="cm-icon">{{ c.icon || '🏕️' }}</div>
              <div style="font-size: 13px; font-weight: 600">{{ c.name }}</div>
              <div class="sub-text">{{ c.memberCount ?? 0 }}人加入</div>
            </div>
            <div class="circle-mini" @click="$router.push('/circles')">
              <div class="cm-icon" style="background: #fff3ee">➕</div>
              <div style="font-size: 13px">更多</div>
            </div>
          </div>
        </div>

        <!-- 精品博主推荐 -->
        <div v-if="!auth.isMerchant" class="x-card">
          <div class="card-title">⭐ 精品博主推荐</div>
          <div v-if="bloggers.length" class="blogger-row">
            <div
              v-for="(b, i) in bloggers.slice(0, 6)"
              :key="b.id"
              class="blogger-item x-card hoverable"
              style="padding: 14px"
              @click="$router.push(`/profile?id=${b.id}`)"
            >
              <el-badge
                v-if="i < 2"
                value="精品"
                type="danger"
                style="margin-right: 8px"
              >
                <el-avatar :size="56" :src="b.avatar">
                  {{ b.nickname?.charAt(0) }}
                </el-avatar>
              </el-badge>
              <el-avatar v-else :size="56" :src="b.avatar">
                {{ b.nickname?.charAt(0) }}
              </el-avatar>
              <div class="mt-12" style="font-weight: 700">{{ b.nickname }}</div>
              <div class="sub-text">{{ SCHOOL_MAP[b.school] || b.school || '三校学子' }}</div>
              <div class="sub-text mt-12">
                Lv.{{ b.bloggerLevel ?? 1 }} · 博主分{{ b.bloggerScore ?? 0 }}
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无博主数据" :image-size="60" />
        </div>

        <!-- 推荐攻略信息流（广告穿插第5位） -->
        <div v-if="!auth.isMerchant">
          <div class="card-title" style="margin-left: 2px">📖 热门推荐攻略</div>
          <div class="post-grid">
            <template v-for="(p, i) in hotPosts" :key="p.id">
              <PostCard :post="p" />
              <div v-if="i === 3 && feedAds.length" :key="`ad-${feedAds[0].id}`">
                <div class="post-card feed-ad-card" @click="onAdClick(feedAds[0])">
                  <el-tag class="feed-ad-tag" type="warning" effect="dark">广告</el-tag>
                  <img class="cover" :src="feedAds[0].image" :alt="feedAds[0].title" />
                  <div class="pc-body">
                    <div class="pc-title" style="color: var(--xlvq-orange)">
                      {{ feedAds[0].title }}
                    </div>
                    <div class="pc-meta">
                      <el-icon><Promotion /></el-icon>
                      赞助商推荐内容
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
          <el-empty v-if="!hotPosts.length" description="还没有攻略，快来抢沙发" />
        </div>
      </div>

      <!-- 侧边栏 -->
      <div class="home-side">
        <div v-if="!auth.isMerchant" class="x-card">
          <div class="card-title">☁️ 旅行热词云</div>
          <SimpleWordCloud :words="wordCloudData" categorize style="height: 260px" />
        </div>

        <div
          v-for="ad in sideAds"
          :key="ad.id"
          class="sidebar-ad"
          @click="onAdClick(ad)"
        >
          <el-tag class="feed-ad-tag" type="warning" effect="dark">广告</el-tag>
          <img :src="ad.image" :alt="ad.title" />
        </div>

        <!-- 热帖榜 Top5：与数据洞察页的热度榜共用同一接口，游客可见 -->
        <div v-if="!auth.isMerchant" class="x-card">
          <div class="card-title">
            🔥 热帖榜 Top5
            <span class="more-link" @click="$router.push('/insight?tab=hot')">完整榜单 →</span>
          </div>
          <div v-if="hotRankList.length" class="hot-rank">
            <div
              v-for="p in hotRankList"
              :key="p.id"
              class="hot-rank-item"
              @click="$router.push(`/posts/${p.id}`)"
            >
              <span class="rank-no" :class="p.rank <= 3 ? 'top' : ''">{{ p.rank }}</span>
              <div class="rank-main">
                <span class="rank-title">{{ p.title }}</span>
                <!-- 榜单文字旁加迷你柱条，用柱长表示热度 -->
                <div class="rank-bar" :style="{ width: heatBarPct(p.heat) + '%' }"></div>
              </div>
              <span class="rank-heat">{{ p.heat }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无热帖数据" :image-size="60" />
        </div>

        <div v-if="!auth.isMerchant" class="x-card" style="background: linear-gradient(160deg,#eaf3fe,#fff)">
          <div class="card-title">🛡️ 安全出行提示</div>
          <div class="sub-text" style="line-height: 2">
            ① 结伴需完成三校学籍认证；<br />
            ② 出行前设置家长/辅导员紧急联系人；<br />
            ③ 行程中可共享实时位置，家校知情；<br />
            ④ 建议购买旅游意外险，风险有兜底；<br />
            ⑤ 商城与导游服务由平台担保交易。
          </div>
          <el-button type="primary" plain class="mt-12" @click="$router.push('/trips')">
            查看结伴行程
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import SimpleWordCloud from '@/components/SimpleWordCloud.vue'
import PostCard from '@/components/PostCard.vue'
import {
  adApi,
  circleApi,
  hotApi,
  portraitApi,
  postApi,
  statsApi,
  userApi
} from '@/api'
import { SCHOOL_MAP } from '@/utils/format'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

const banners = ref([])
const sideAds = ref([])
const feedAds = ref([])
const hotWords = ref([])
const circles = ref([])
const bloggers = ref([])
const hotPosts = ref([])
const hotRankList = ref([])

const wordCloudData = ref([])

// 平台对当前用户的解读（取自个人画像的insights，首页只放最有用的两条）
const digest = ref([])
const digestLoaded = ref(false)
const showDigest = computed(
  () => auth.isLoggedIn && !auth.isMerchant && (digest.value.length > 0 || digestLoaded.value)
)
const digestEmpty = computed(() => digestLoaded.value && !digest.value.length)

async function loadDigest() {
  if (!auth.isLoggedIn || auth.isMerchant) return
  const p = await portraitApi.me().catch(() => null)
  // 没有结伴和足迹记录时，insights里只有补全画像的提示，首页不展示
  const list = Array.isArray(p?.insights) ? p.insights : []
  const hasMotion = (p?.stats?.tripCount || 0) > 0 || (p?.stats?.spotCount || 0) > 0
  digest.value = hasMotion ? list.slice(0, 2) : []
  digestLoaded.value = true
}

/** 商家（纯商业合作方）首页只展示经营入口 */
const BIZ_ENTRIES = [
  { icon: '🏪', title: '开店经营', desc: '入驻认证、上架商品、维护库存与价格', path: '/merchant' },
  { icon: '📣', title: '广告投放', desc: '投放首页 Banner / 信息流 / 侧边广告位', path: '/merchant?tab=ads' },
  { icon: '📦', title: '交易管理', desc: '查看店铺订单、发货与担保交易进度', path: '/orders' },
  { icon: '💬', title: '评价管理', desc: '查看买家评价并回复，维护店铺口碑', path: '/merchant?tab=reviews' }
]

async function loadAds() {
  const [b, f, s] = await Promise.allSettled([
    adApi.bySlot('BANNER'),
    adApi.bySlot('FEED'),
    adApi.bySlot('SIDEBAR')
  ])
  banners.value = [].concat(b.status === 'fulfilled' && b.value ? b.value : [])
  feedAds.value = [].concat(f.status === 'fulfilled' && f.value ? f.value : [])
  sideAds.value = [].concat(s.status === 'fulfilled' && s.value ? s.value : [])
  // Banner曝光上报
  banners.value.forEach((ad) => adApi.view(ad.id).catch(() => {}))
}

function onBannerChange(idx) {
  const ad = banners.value[idx]
  if (ad) adApi.view(ad.id).catch(() => {})
}

function onAdClick(ad) {
  if (!ad) return
  adApi.click(ad.id).catch(() => {})
  if (ad.link) {
    if (/^https?:\/\//.test(ad.link)) {
      window.open(ad.link, '_blank')
    } else if (ad.link.startsWith('/')) {
      router.push(ad.link)
    }
  }
}

function searchKeyword(w) {
  router.push({ path: '/posts', query: { keyword: w } })
}

async function loadHotSearch() {
  const data = await statsApi.hotSearch().catch(() => [])
  if (Array.isArray(data)) {
    hotWords.value = data.map((x) => (typeof x === 'string' ? x : x.keyword || x.word || x.name))
  } else if (data?.list) {
    hotWords.value = data.list.map((x) => (typeof x === 'string' ? x : x.keyword))
  }
}

async function loadWordCloud() {
  const words = await statsApi.hotWords().catch(() => [])
  const list = Array.isArray(words) ? words : []
  wordCloudData.value = list.map((w) =>
    typeof w === 'string' ? { name: w, value: 100 } : { name: w.name, value: w.value }
  )
}

async function loadHotRank() {
  const list = await hotApi.posts(5).catch(() => [])
  hotRankList.value = (list || []).slice(0, 5)
}

/** 迷你热度柱条宽度：以榜一为100% 基准，最低8% 保证可见 */
function heatBarPct(heat) {
  const max = Math.max(...hotRankList.value.map((p) => Number(p.heat) || 0), 1)
  return Math.max(8, Math.round(((Number(heat) || 0) * 100) / max))
}

async function loadAll() {
  const [circleData, bloggerData, postData] = await Promise.allSettled([
    circleApi.list(),
    userApi.rankings(),
    postApi.list({ sort: 'hot' })
  ])
  circles.value =
    circleData.status === 'fulfilled'
      ? (circleData.value?.list || circleData.value || []).slice(0, 10)
      : []
  bloggers.value =
    bloggerData.status === 'fulfilled'
        ? bloggerData.value?.list || bloggerData.value || []
      : []
  const posts =
    postData.status === 'fulfilled'
      ? postData.value?.list || postData.value || []
      : []
  hotPosts.value = posts.slice(0, 8)
}

onMounted(() => {
  loadAds()
  // 商家已退出前台：不加载热搜/词云/圈层/博主/攻略等社区内容
  if (!auth.isMerchant) {
    loadHotSearch()
    loadWordCloud()
    loadAll()
    loadHotRank()
    loadDigest()
  }
})

onBeforeUnmount(() => {
})
</script>

<style scoped>
.banner-slide {
  position: relative;
  height: 100%;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  background: #dcecff;
}
.banner-slide img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ad-flag {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
}
.banner-title {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24px 16px 12px;
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
}
.blogger-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.blogger-item {
  text-align: center;
}
.hot-rank {
  display: flex;
  flex-direction: column;
}
.hot-rank-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 2px;
  cursor: pointer;
  border-bottom: 1px dashed var(--xlvq-line, #eef1f5);
  font-size: 13px;
}
.hot-rank-item:last-child {
  border-bottom: none;
}
.hot-rank-item:hover .rank-title {
  color: var(--xlvq-orange, #ff7a45);
}
.rank-no {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  background: #f0f2f5;
  color: #8a94a3;
  font-size: 12px;
  font-weight: 700;
}
.rank-no.top {
  background: #fff1e8;
  color: #ff7a45;
}
.rank-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: #303133;
}
.rank-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.rank-bar {
  height: 4px;
  border-radius: 2px;
  background: linear-gradient(90deg, #ffd8b0, #ff7a45);
  opacity: 0.85;
}
.rank-heat {
  flex-shrink: 0;
  font-size: 12px;
  color: #b0b6bf;
}
.feed-ad-card .cover {
  height: 150px;
}
@media (max-width: 860px) {
  .blogger-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
.digest-line {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.7;
  padding: 3px 0;
}
.digest-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--xlvq-primary, #2f80ed);
  margin-top: 9px;
  flex-shrink: 0;
}
</style>
