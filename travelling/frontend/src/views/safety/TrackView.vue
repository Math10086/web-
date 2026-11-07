<template>
  <div class="track-page">
    <header class="track-header">
      <div class="th-inner">
        <div class="th-logo">
          <span>🏔️</span>
          <span>学旅圈</span>
          <span class="th-sub">· 家校知情 · 位置共享</span>
        </div>
        <div class="sub-text">本页面由学生本人限时授权，仅限紧急联系人查看</div>
      </div>
    </header>

    <main class="track-main">
      <!-- 加载中 -->
      <div v-if="loading" class="track-card center-card">
        <el-icon class="is-loading" :size="26"><Loading /></el-icon>
        <div class="sub-text mt-12">正在读取授权信息…</div>
      </div>

      <!-- 授权无效 / 已撤销 / 已过期 -->
      <div v-else-if="error" class="track-card center-card">
        <div class="err-icon">🔒</div>
        <h2 class="err-title">{{ errTitle }}</h2>
        <p class="sub-text" style="font-size: 14px">{{ error }}</p>
        <div class="tips-box mt-16">
          <div>· 授权链接由学生本人在「安全中心 · 位置共享」中生成</div>
          <div>· 链接到期或学生撤销后会自动失效</div>
          <div>· 如为紧急情况，请直接电话联系学生本人或拨打110 / 120</div>
        </div>
      </div>

      <!-- 正常内容 -->
      <template v-else>
        <div class="track-card hero-card">
          <el-avatar :size="58" :src="data.member?.avatar" class="hero-avatar">
            {{ data.member?.nickname?.charAt(0) || '游' }}
          </el-avatar>
          <div style="flex: 1">
            <div class="hero-name">
              {{ data.member?.nickname }}
              <el-tag v-if="data.member?.verified" type="success" size="small" class="ml-8">三校认证</el-tag>
            </div>
            <div class="sub-text">{{ data.member?.school || '在校学生' }}</div>
            <div class="hero-trip mt-8">
              正在参加结伴行程
              <strong>《{{ data.trip?.title }}》</strong>
              <el-tag size="small" :type="TRIP_STATUS_TAG[data.trip?.status] || 'info'" class="ml-8">
                {{ TRIP_STATUS_MAP[data.trip?.status] || data.trip?.status }}
              </el-tag>
            </div>
          </div>
          <div class="hero-remain">
            <div class="remain-num">{{ remainHours }}</div>
            <div class="sub-text">授权剩余（小时）</div>
          </div>
        </div>

        <div class="track-grid">
          <!-- 左：地图 + 位置 -->
          <div class="track-card">
            <div class="card-head">
              📍 实时位置
              <el-tag v-if="stale" type="warning" size="small" class="ml-8">位置可能已过期</el-tag>
              <el-tag v-else-if="data.location?.available" type="success" size="small" class="ml-8">较新</el-tag>
            </div>
            <div v-if="!hasTMapKey()" class="map-fallback">
              地图组件未配置密钥，以下为坐标信息（精确到小数点后5位）
            </div>
            <div v-show="hasTMapKey()" ref="mapEl" class="map-box"></div>

            <div v-if="data.location?.available" class="loc-box">
              <div class="loc-line">
                <span class="sub-text">坐标</span>
                <strong>{{ Number(data.location.lng).toFixed(5) }}, {{ Number(data.location.lat).toFixed(5) }}</strong>
              </div>
              <div class="loc-line">
                <span class="sub-text">最近更新</span>
                <strong>{{ formatTime(data.location.updatedAt) }}</strong>
                <span class="sub-text">（{{ agoText }}）</span>
              </div>
              <div v-if="stale" class="warn-line">
                ⚠️ 该位置已超过2小时未更新，可能不是当前实时位置，建议电话确认。
              </div>
            </div>
            <el-empty v-else description="学生尚未上报位置，可能还没开始行程" :image-size="60" />

            <div v-if="data.teammates?.length" class="team-box">
              <div class="card-head" style="font-size: 13px">👥 同行队友（{{ data.teammates.length }}位已上报位置）</div>
              <div v-for="t in data.teammates" :key="t.name" class="team-row">
                <span>{{ t.name }}</span>
                <span class="sub-text">
                  {{ Number(t.lng).toFixed(4) }}, {{ Number(t.lat).toFixed(4) }} ·
                  {{ formatTime(t.updatedAt) }}
                </span>
              </div>
              <div class="sub-text" style="margin-top: 6px">
                队友位置已脱敏，仅用于大致判断是否离队。
              </div>
            </div>
          </div>

          <!-- 右：行程与授权信息 -->
          <div class="track-col">
            <div class="track-card">
              <div class="card-head">🧭 行程信息</div>
              <div class="kv"><span class="sub-text">目的地</span><strong>{{ data.trip?.destination }}</strong></div>
              <div class="kv"><span class="sub-text">集合时间</span><strong>{{ formatTime(data.trip?.departAt) }}</strong></div>
              <div class="kv"><span class="sub-text">集合地点</span><strong>{{ data.trip?.meetPoint }}</strong></div>
              <div class="kv">
                <span class="sub-text">出行方式</span>
                <strong>{{ TRANSPORT_MODE_MAP[data.trip?.transport] || data.trip?.transport }}</strong>
              </div>
              <div class="kv">
                <span class="sub-text">风险等级</span>
                <el-tag size="small" :type="RISK_TAG[data.trip?.riskLevel] || 'success'">
                  {{ RISK_MAP[data.trip?.riskLevel] || '低风险' }}
                </el-tag>
              </div>
            </div>

            <div class="track-card">
              <div class="card-head">🔑 授权信息</div>
              <div class="kv">
                <span class="sub-text">被授权人</span>
                <strong>
                  {{ data.auth?.contactName }}
                  <el-tag size="small" :type="data.auth?.relation === 'COUNSELOR' ? 'warning' : 'primary'" class="ml-8">
                    {{ data.auth?.relationName }}
                  </el-tag>
                </strong>
              </div>
              <div class="kv"><span class="sub-text">授权时段</span>
                <strong>{{ formatTime(data.auth?.startAt) }} ~ {{ formatTime(data.auth?.endAt) }}</strong>
              </div>
              <div class="kv"><span class="sub-text">剩余有效期</span>
                <strong style="color: #fa8c16">{{ remainHours }}小时</strong>
              </div>
              <div class="kv"><span class="sub-text">查看次数</span><strong>{{ data.viewCount }}次</strong></div>
              <div v-if="data.auth?.note" class="kv">
                <span class="sub-text">学生留言</span><strong>{{ data.auth.note }}</strong>
              </div>
            </div>
          </div>
        </div>

        <!-- 家校知情通知 -->
        <div v-if="data.notices?.length" class="track-card">
          <div class="card-head">📮 家校知情通知记录</div>
          <div v-for="(n, i) in data.notices" :key="i" class="notice-row">
            <div class="flex-between">
              <el-tag size="small" type="primary" effect="plain">
                {{ NOTICE_TYPE_MAP[n.noticeType] || n.noticeType }}
              </el-tag>
              <span class="sub-text">{{ formatTime(n.createdAt) }}</span>
            </div>
            <div class="notice-body">{{ n.content }}</div>
          </div>
        </div>

        <!-- 安全提示 -->
        <div class="track-card tips-card">
          <div class="card-head">🛡️ 安全提示</div>
          <div v-for="(t, i) in data.tips || []" :key="i" class="tip-row">· {{ t }}</div>
        </div>
      </template>
    </main>

    <footer class="track-footer">
      <div>🏔️ 学旅圈 · 高校学生旅游社区与结伴出行平台 · 家校知情</div>
      <div>位置信息按需授权、限时可查、脱敏展示，遵循个人信息保护的最小化收集原则</div>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { safetyApi } from '@/api'
import { hasTMapKey, loadTMap, pinIcon } from '@/utils/tmap'
import {
  formatTime,
  NOTICE_TYPE_MAP,
  RISK_MAP,
  RISK_TAG,
  TRANSPORT_MODE_MAP,
  TRIP_STATUS_MAP,
  TRIP_STATUS_TAG
} from '@/utils/format'

const route = useRoute()

const loading = ref(true)
const error = ref('')
const data = ref(null)
const mapEl = ref(null)
const remainMinutes = ref(0)

let map = null
let markers = null
let timer = null

const stale = computed(() => !!data.value?.location?.stale)
const remainHours = computed(() => {
  const m = remainMinutes.value
  if (m <= 0) return 0
  return Math.round((m / 60) * 10) / 10
})
const agoText = computed(() => {
  const mins = data.value?.location?.minutesAgo
  if (mins === null || mins === undefined || mins < 0) return '时间未知'
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const h = Math.floor(mins / 60)
  const m = mins % 60
  return `${h} 小时${m} 分钟前`
})
const errTitle = computed(() => {
  const msg = error.value || ''
  if (msg.includes('过期')) return '该授权已过期'
  if (msg.includes('撤销')) return '该授权已被撤销'
  if (msg.includes('无效') || msg.includes('删除')) return '授权链接无效'
  return '无法打开该授权链接'
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await safetyApi.track(route.params.token)
    data.value = res
    remainMinutes.value = res?.auth?.remainMinutes ?? 0
  } catch (e) {
    error.value = e?.message || '授权链接无效或已失效'
  } finally {
    // 注意：地图容器在v-else分支里，必须等loading=false渲染完成后再初始化，
    // 否则mapEl还不存在，initMap会静默跳过 → 地图区域永远空白
    loading.value = false
    await nextTick()
    initMap()
  }
}

async function initMap() {
  if (!hasTMapKey() || !mapEl.value || map) return
  try {
    const TMap = await loadTMap()
    const loc = data.value?.location
    const center = loc?.available
      ? new TMap.LatLng(Number(loc.lat), Number(loc.lng))
      : new TMap.LatLng(28.2, 112.97)
    map = new TMap.Map(mapEl.value, {
      center,
      zoom: loc?.available ? 14 : 11,
      pitch: 0,
      rotation: 0
    })

    const styles = {
      me: new TMap.MarkerStyle({
        width: 32,
        height: 40,
        anchor: { x: 16, y: 40 },
        src: pinIcon('#f5222d', '我', 32)
      }),
      mate: new TMap.MarkerStyle({
        width: 22,
        height: 28,
        anchor: { x: 11, y: 28 },
        src: pinIcon('#909399', '友', 22)
      })
    }
    const geometries = []
    if (loc?.available) {
      geometries.push({ id: 'me', styleId: 'me', position: new TMap.LatLng(Number(loc.lat), Number(loc.lng)) })
    }
    ;(data.value?.teammates || []).forEach((t, i) => {
      geometries.push({
        id: 'mate' + i,
        styleId: 'mate',
        position: new TMap.LatLng(Number(t.lat), Number(t.lng))
      })
    })
    markers = new TMap.MultiMarker({ map, styles, geometries })
  } catch (e) {
    // 地图加载失败时降级为坐标文本，不影响信息查看
    map = null
  }
}

onMounted(() => {
  load()
  timer = setInterval(() => {
    if (remainMinutes.value > 0) {
      remainMinutes.value = Math.max(0, remainMinutes.value - 1)
    }
  }, 60000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (markers) {
    markers.setMap(null)
    markers = null
  }
  if (map) {
    map.destroy()
    map = null
  }
})
</script>

<style scoped>
.track-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef4ff 0%, var(--xlvq-bg, #f5f7fa) 260px);
}
.track-header {
  background: #fff;
  border-bottom: 1px solid var(--xlvq-border);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.th-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 14px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.th-logo {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 800;
  font-size: 17px;
  color: var(--xlvq-primary, #2f80ed);
}
.th-sub {
  font-weight: 500;
  font-size: 13px;
  color: var(--xlvq-text-sub);
}
.track-main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 18px;
}
.track-card {
  background: #fff;
  border: 1px solid var(--xlvq-border);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 14px;
}
.center-card {
  text-align: center;
  padding: 44px 20px;
}
.err-icon {
  font-size: 40px;
}
.err-title {
  margin: 8px 0;
}
.tips-box {
  display: inline-block;
  text-align: left;
  background: var(--xlvq-card-2, #fafafa);
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--xlvq-text-sub);
  line-height: 2;
}
.hero-card {
  display: flex;
  align-items: center;
  gap: 16px;
}
.hero-name {
  font-size: 19px;
  font-weight: 800;
}
.hero-trip {
  font-size: 14px;
}
.hero-remain {
  text-align: center;
  padding-left: 16px;
  border-left: 1px dashed var(--xlvq-border);
}
.remain-num {
  font-size: 26px;
  font-weight: 800;
  color: #fa8c16;
  line-height: 1.2;
}
.track-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 14px;
  align-items: start;
}
.track-col {
  display: flex;
  flex-direction: column;
}
.card-head {
  font-weight: 700;
  margin-bottom: 10px;
}
.map-box {
  width: 100%;
  height: 300px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--xlvq-border);
}
.map-fallback {
  height: 92px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--xlvq-card-2, #fafafa);
  border: 1px dashed var(--xlvq-border);
  border-radius: 10px;
  color: var(--xlvq-text-sub);
  font-size: 13px;
}
.loc-box,
.team-box {
  margin-top: 12px;
}
.loc-line,
.kv {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--xlvq-border);
  font-size: 13px;
}
.warn-line {
  margin-top: 8px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  color: #ad6800;
}
.team-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.notice-row {
  padding: 10px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.notice-body {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.9;
  color: var(--xlvq-text-sub);
  margin-top: 6px;
}
.tips-card .tip-row {
  font-size: 13px;
  line-height: 2;
  color: var(--xlvq-text-sub);
}
.track-footer {
  text-align: center;
  padding: 20px 14px 30px;
  color: var(--xlvq-text-sub);
  font-size: 12px;
  line-height: 2;
}
.ml-8 {
  margin-left: 8px;
}
.mt-8 {
  margin-top: 8px;
}
@media (max-width: 860px) {
  .track-grid {
    grid-template-columns: 1fr;
  }
  .hero-card {
    flex-wrap: wrap;
  }
}
</style>
