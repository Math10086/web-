<template>
  <div class="studio">
    <!-- ================= 顶部：行程标题栏 ================= -->
    <header class="sd-head">
      <div class="sd-head-left">
        <el-icon class="sd-back" @click="onBack"><Back /></el-icon>
        <span class="sd-title">{{ plan.tripTitle }}</span>
        <span class="sd-sub">出发{{ plan.startTime }}</span>
        <span class="sd-chip">📍 {{ plan.city || '—' }}</span>
        <span class="sd-chip">🗓️ {{ plan.tripDays }}天</span>
        <span class="sd-chip">🧭 {{ plan.planModeLabel }}</span>
      </div>
      <div class="sd-head-right">
        <span v-if="plan.planResult" class="sd-head-stat">
          {{ plan.planResult.totalDistanceKm }}km ·
          {{ minutesToText(plan.planResult.totalDurationMin) }} ·
          {{ plan.planResult.spots?.length || 0 }}个景点
        </span>
        <el-button
          v-if="plan.planResult"
          size="small"
          :loading="plan.savingRoute"
          @click="plan.saveRoute"
        >
          保存行程
        </el-button>
        <el-button size="small" :icon="Aim" @click="fitRoute">回到路线</el-button>
      </div>
    </header>

    <div class="sd-body">
      <!-- ================= ① 左栏：AI 行程助手（路线规划 / 出行比较属学生用户、博主、导游能力） ================= -->
      <aside v-if="auth.isBusiness" class="sd-col left">
        <div class="sd-col-head">
          <span class="sd-ai-badge">🤖 AI 搭子 · 记得你的偏好</span>
          <el-tooltip
            placement="bottom"
            content="和 TA 说「长沙2天，喜欢拍照和美食」，AI 会按你的画像选点、2-opt 优化路线并解释推荐理由"
          >
            <el-icon class="sd-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>

        <div class="sd-scroll">
          <!-- V2：AI 搭子的长期画像（越聊越懂你） -->
          <div v-if="plan.aiMemory?.memory" class="ai-bubble ai-memory-chip">
            <div class="ai-bubble-head">
              🧠 AI 记得我 · 已聊{{ plan.aiMemory.chatCount }}轮
            </div>
            <div class="ai-bubble-text clamp">{{ plan.aiMemory.memory }}</div>
          </div>

          <!-- 规划完成后的一句话摘要（携程式 AI 助手文案） -->
          <div v-if="plan.planResult" class="ai-bubble">
            <div class="ai-bubble-head">✨ 已为你规划好</div>
            <div class="ai-bubble-text">{{ plan.aiSummary }}</div>
          </div>

          <PlanFilter />
          <SpotPicker @focus="focusSpot" />
        </div>

        <div class="sd-foot">
          <!-- 当前解析引擎：本地 Ollama / 云端大模型 / 本地规则 -->
          <div class="ai-engine-tip" :class="{ on: plan.aiReady }">
            <template v-if="plan.aiReady">
              🧠 {{ plan.aiProvider }} · {{ plan.aiModelName }}
            </template>
            <template v-else>
              <span>⚙️ 未检测到模型，使用本地规则解析</span>
              <el-tooltip placement="top">
                <template #content>
                  启动本地 Ollama（qwen2.5:7b）即可获得自然语言理解能力<br />
                  或在后端 application.yml 的 xlvq.ai.api-key 填入云端密钥
                </template>
                <el-icon class="tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </div>
          <el-button class="ai-open-btn" type="primary" :icon="ChatDotRound" @click="toggleAi">
            打开 AI 搭子 · 对话式规划
          </el-button>
          <div class="ai-open-tip">对话框可拖动，规划结果会实时画在地图上</div>
        </div>
      </aside>

      <!-- 游客和商家：可看地图与公开出行信息，路线规划不可用 -->
      <aside v-else class="sd-col left">
        <div class="sd-col-head">
          <span class="sd-ai-badge">🔒 权限提示</span>
        </div>
        <div class="sd-scroll">
          <div class="x-card">
            <div class="card-title">
              {{ auth.isGuest ? '登录后可使用路线规划' : '当前身份不参与路线规划' }}
            </div>
            <div class="sub-text" style="line-height: 1.9">
              {{
                auth.isGuest
                  ? '游客可浏览地图、景点与公开出行信息。登录并完成三校学籍认证后，即可规划路线、比较出行方案、定制结伴偏好。'
                  : '商家账号仅可浏览地图与公开出行信息；路线规划 / 出行比较属学生用户（含博主、导游）的能力。'
              }}
            </div>
            <el-button
              v-if="auth.isGuest"
              type="primary"
              round
              class="mt-12"
              @click="router.push('/login')"
            >
              去登录
            </el-button>
          </div>
        </div>
      </aside>

      <!-- ================= ② 中栏：行程详情 / 辅助决策 / 出行信息 ================= -->
      <section class="sd-col mid">
        <div class="sd-col-head mid-head">
          <span
            v-for="t in midTabs"
            :key="t.key"
            class="mid-tab"
            :class="{ active: midTab === t.key }"
            @click="midTab = t.key"
          >
            {{ t.label }}
          </span>
          <span class="mid-head-right">
            <span
              v-if="!auth.isBusiness"
              class="sub-text"
              style="font-size: 12px; margin-right: 8px"
            >
              仅公开出行信息
            </span>
            <el-tooltip v-if="midTab === 'plan' && plan.planResult" content="重新适配地图视野">
              <el-icon class="mid-icon" @click="fitRoute"><FullScreen /></el-icon>
            </el-tooltip>
          </span>
        </div>

        <div class="sd-scroll mid-scroll">
          <DayTimeline v-if="midTab === 'plan'" @focus="focusSpot" />

          <template v-else-if="midTab === 'assist'">
            <WeatherCard />
            <CostEstimateCard />
            <AdjustCard />
          </template>

          <TransportInfo v-else />
        </div>
      </section>

      <!-- ================= ③ 右栏：地图 ================= -->
      <section class="sd-col map">
        <div ref="mapEl" class="x-map studio-map"></div>

        <!-- 左上：景点分类标签（点击显示 / 隐藏该类标注） -->
        <div class="map-chips">
          <span
            v-for="cat in categoryList"
            :key="cat.key"
            class="map-chip"
            :class="{ off: !plan.categories.includes(cat.key) }"
            @click="plan.toggleCategory(cat.key)"
          >
            <span class="mc-sym" :style="{ color: cat.color }">{{ cat.symbol }}</span>
            {{ cat.label }}
          </span>
        </div>

        <!-- 右侧：地图控件 -->
        <div class="map-ctrls">
          <el-tooltip placement="left" content="回到路线范围">
            <button class="mc-btn" @click="fitRoute">⛶</button>
          </el-tooltip>
          <el-tooltip placement="left" content="3D 鸟瞰 / 平面">
            <button class="mc-btn" :class="{ on: view3d }" @click="toggle3d">🧊</button>
          </el-tooltip>
          <el-tooltip
            v-if="auth.isBusiness"
            placement="left"
            content="点图添加：开启后点地图任意位置，即可加入想去的地点"
          >
            <button class="mc-btn" :class="{ on: pickMode }" @click="pickMode = !pickMode">
              ✚
            </button>
          </el-tooltip>
          <button class="mc-btn" @click="zoomBy(1)">＋</button>
          <button class="mc-btn" @click="zoomBy(-1)">－</button>
        </div>

        <!-- 左下：按天图例（点切换当前天，与中栏胶囊联动） -->
        <div v-if="plan.isMultiDay && mapReady" class="map-day-legend">
          <div
            class="mdl-item"
            :class="{ active: plan.activeDay === 0 }"
            @click="plan.activeDay = 0"
          >
            <span
              class="mdl-swatch"
              :style="{ background: `linear-gradient(90deg, ${plan.legendGradient})` }"
            />
            <span class="mdl-title">总览</span>
            <span class="mdl-meta">
              {{ plan.planResult.dayCount }}天 · {{ plan.planResult.totalDistanceKm }}km
            </span>
          </div>
          <div
            v-for="d in plan.dayList"
            :key="d.day"
            class="mdl-item"
            :class="{ active: plan.activeDay === d.day }"
            @click="plan.activeDay = d.day"
          >
            <span class="mdl-swatch" :style="{ background: d.color }" />
            <span class="mdl-title">第{{ d.day }}天</span>
            <span class="mdl-meta">{{ d.spotCount }}点 · {{ d.totalDistanceKm }}km</span>
          </div>
        </div>

        <div v-if="pickMode" class="map-tip">
          ✚ 点图添加中：点击地图任意位置，即可加入「想去的地点」
        </div>
        <div v-else-if="plan.planResult && mapReady" class="map-tip">
          {{ view3d ? '🧊 3D 鸟瞰' : '🗺️ 平面视图' }} · 沿真实道路计算
        </div>

        <div v-if="!mapReady && !mapError" class="map-mask">
          <el-icon class="is-loading" :size="22"><Loading /></el-icon>
          <div class="sub-text mt-12">3D 地图加载中…</div>
        </div>

        <div v-if="mapError" class="map-mask">
          <div style="font-size: 26px">🗺️</div>
          <div style="font-weight: 700; margin: 8px 0">3D 地图需要配置腾讯位置服务 key</div>
          <div class="sub-text" style="line-height: 1.9; max-width: 420px">
            1. 打开
            <el-link type="primary" href="https://lbs.qq.com/dev/console/key/manage" target="_blank">
              lbs.qq.com/dev/console/key/manage
            </el-link>
            （微信/QQ 登录 → 个人认证 → 新建应用 → 创建 key）<br />
            2. 把 key 填到 <code>frontend/.env.local</code> 的 <code>VITE_TMAP_KEY=</code> 后面<br />
            3. 重启前端（<code>npm run dev</code>）即可看到3D 地图
          </div>
          <div class="sub-text mt-12">
            左侧条件、中栏行程、天气与费用估算不受影响，可正常使用。
          </div>
        </div>
      </section>
    </div>

    <!-- ================= ④ AI 搭子：悬浮球 + 独立对话窗（规划结果实时画在地图上） ================= -->
    <template v-if="auth.isBusiness">
      <transition name="ai-win-pop">
        <section v-if="aiOpen" class="ai-win" :style="{ left: win.x + 'px', top: win.y + 'px' }">
          <header class="ai-win-head" @mousedown="startDrag">
            <div class="ai-win-ava">🤖</div>
            <div class="ai-win-meta">
              <div class="ai-win-name">AI 旅行搭子</div>
              <div class="ai-win-sub">
                <i class="ai-dot" :class="{ on: plan.aiReady }"></i>
                {{ plan.aiReady ? plan.aiProvider + ' · ' + plan.aiModelName : '本地规则解析' }}
              </div>
            </div>
            <div class="ai-win-acts" @mousedown.stop>
              <el-tooltip content="最小化" placement="bottom">
                <el-icon @click="aiOpen = false"><Minus /></el-icon>
              </el-tooltip>
            </div>
          </header>

          <div ref="aiScroll" class="ai-win-body">
            <div v-if="plan.aiMemory?.memory" class="ai-bubble ai-memory-chip">
              <div class="ai-bubble-head">🧠 AI 记得我 · 已聊{{ plan.aiMemory.chatCount }}轮</div>
              <div class="ai-bubble-text clamp">{{ plan.aiMemory.memory }}</div>
            </div>

            <div v-if="!plan.aiMessages.length" class="ai-welcome">
              <div class="ai-welcome-emoji">🧭</div>
              <div class="ai-welcome-title">想去哪儿？说一句就行</div>
              <div class="ai-welcome-sub">
                我会先读懂你的话（城市 / 时长 / 喜欢的玩法 / 点名要去的地方），
                再按你的画像挑点、优化顺序，最后把路线直接画到右边的地图上。
              </div>
            </div>

            <div
              v-for="(m, i) in plan.aiMessages"
              :key="i"
              class="ai-msg"
              :class="m.role === 'user' ? 'me' : 'ai'"
            >
              <div v-if="m.role === 'ai' && m.engine" class="ai-engine" :class="m.engine">
                {{
                  m.engine === 'smart'
                    ? '🤖 画像选点 + 2-opt 路线'
                    : m.engine === 'llm' || m.engine === 'ollama'
                      ? '🧠 本地 Ollama 理解'
                      : '⚙️ 本地规则解析'
                }}
              </div>
              <div class="ai-msg-text">{{ m.text }}</div>
            </div>

            <div v-if="plan.aiThinking" class="ai-msg ai">
              <div class="ai-typing">
                <i></i><i></i><i></i>
                <span>正在理解你的话并排路线…</span>
              </div>
            </div>
          </div>

          <div class="ai-win-quick">
            <span v-for="q in aiQuick" :key="q" class="ai-quick" @click="askQuick(q)">{{ q }}</span>
          </div>

          <footer class="ai-win-foot">
            <el-input
              v-model="aiInput"
              type="textarea"
              :rows="2"
              resize="none"
              maxlength="400"
              placeholder="比如：我就想去省博物馆，和4个室友，一个下午，从麓南校区出发"
              @keydown.enter.exact.prevent="submitAi"
            />
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="plan.planning"
              @click="submitAi"
            >
              发送
            </el-button>
          </footer>
        </section>
      </transition>

      <button class="ai-ball" :class="{ open: aiOpen }" @click="toggleAi">
        <span v-if="!aiOpen" class="ai-ball-ring"></span>
        <span class="ai-ball-icon">{{ aiOpen ? '✕' : '🤖' }}</span>
        <span v-if="!aiOpen" class="ai-ball-label">AI 搭子</span>
      </button>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Back, Promotion, Aim, QuestionFilled, FullScreen, ChatDotRound, Minus } from '@element-plus/icons-vue'
import AdjustCard from '@/components/plan/AdjustCard.vue'
import CostEstimateCard from '@/components/plan/CostEstimateCard.vue'
import DayTimeline from '@/components/plan/DayTimeline.vue'
import PlanFilter from '@/components/plan/PlanFilter.vue'
import SpotPicker from '@/components/plan/SpotPicker.vue'
import TransportInfo from '@/components/plan/TransportInfo.vue'
import WeatherCard from '@/components/plan/WeatherCard.vue'
import { CATEGORY_STYLE, hasTMapKey, loadTMap, mixWhite, numIcon, pinIcon } from '@/utils/tmap'
import { DAY_COLORS, ROUTE_MODE_COLOR, SPOT_CATEGORY_MAP, minutesToText } from '@/utils/format'
import { useTripPlan } from '@/composables/useTripPlan'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const plan = useTripPlan()
const auth = useAuthStore()

// 中栏板块（原「AI行程」「信息浏览」的内容都在这里）。
// 行程详情（路线规划）与辅助决策（动态行程调整）属学生用户/博主/导游；
// 游客与商家仅可查看地图与「出行信息」公开部分。
const midTabs = computed(() =>
  auth.isBusiness
    ? [
        { key: 'plan', label: '行程详情' },
        { key: 'assist', label: '辅助决策' },
        { key: 'transport', label: '出行信息' }
      ]
    : [{ key: 'transport', label: '出行信息' }]
)
const midTab = ref('plan')
const view3d = ref(true)
const aiInput = ref('')
// ===== AI搭子悬浮窗状态 =====
const aiOpen = ref(false)
const aiScroll = ref(null)
const win = ref({ x: 0, y: 0 })
// 对话里第几次「换一条路」：命中换路线口语时自增，后端据此换一批备选景点，地图才会真的重画
const aiRound = ref(0)
const REPLAN_RE =
  /换(一|条|个|种|上|掉)?[^，。！？]{0,3}(路|路线|行程)|换个|重新(规划|排|安排|来|换)|再来(一)?(条|遍|次)|另一条|其他路线|别的路线|不走这|不喜欢|不满意|不想要|变一变|重排|重来/
const aiQuick = ['长沙2天，拍照 + 美食', '省博物馆半天，13:30出发', '和室友一起，别太累', '张家界2天速通']

/** 悬浮窗默认贴在右下角（避开悬浮球） */
function placeWin() {
  const w = 400
  const h = Math.min(560, window.innerHeight - 160)
  win.value = {
    x: Math.max(12, window.innerWidth - w - 28),
    y: Math.max(76, window.innerHeight - h - 110)
  }
}

function toggleAi() {
  if (!aiOpen.value && !win.value.x) placeWin()
  aiOpen.value = !aiOpen.value
  if (aiOpen.value) nextTick(scrollAiToEnd)
}

function scrollAiToEnd() {
  const el = aiScroll.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 拖动窗口：按住标题栏移动，限制在视口内 */
function startDrag(e) {
  const startX = e.clientX
  const startY = e.clientY
  const origin = { ...win.value }
  const move = (ev) => {
    win.value = {
      x: Math.min(Math.max(0, origin.x + ev.clientX - startX), window.innerWidth - 260),
      y: Math.min(Math.max(0, origin.y + ev.clientY - startY), window.innerHeight - 80)
    }
  }
  const up = () => {
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', up)
  }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}

/** 快捷气泡：点一下直接发出去 */
function askQuick(q) {
  aiInput.value = q
  submitAi()
}

watch(
  () => plan.aiMessages.length,
  () => nextTick(scrollAiToEnd)
)

// 身份切换后若当前板块不可用，自动落到第一个可用板块
watch(
  midTabs,
  (list) => {
    if (list.length && !list.some((t) => t.key === midTab.value)) {
      midTab.value = list[0].key
    }
  },
  { immediate: true }
)

const categoryList = computed(() =>
  Object.entries(CATEGORY_STYLE).map(([key, v]) => ({ key, ...v }))
)

const mapEl = ref(null)
let TMapRef = null
let map = null
let spotLayer = null // 景点标注图层（MultiMarker）
let routeLineLayer = null // 路线图层（MultiPolyline）
let routeNumLayer = null // 路线顺序编号图层（MultiMarker）
let infoWindow = null // 点击标注弹出的信息窗
const spotById = {}
const mapReady = ref(false)
const mapError = ref(false)

function lngOf(s) {
  return Number(s.lng ?? s.longitude)
}
function latOf(s) {
  return Number(s.lat ?? s.latitude)
}
function hasCoord(s) {
  return !!latOf(s) && !!lngOf(s)
}

/* ============ 「点图添加」：直接在地图上点一下，把该位置加成想去的地点 ============ */
const pickMode = ref(false)

async function onMapPick(evt) {
  if (!pickMode.value) return
  const ll = evt?.latLng
  const lat = typeof ll?.getLat === 'function' ? ll.getLat() : ll?.lat
  const lng = typeof ll?.getLng === 'function' ? ll.getLng() : ll?.lng
  if (lat == null || lng == null) return
  pickMode.value = false
  try {
    const { value } = await ElMessageBox.prompt(
      `坐标${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}，给它起个名字：`,
      '添加想去的地点',
      {
        confirmButtonText: '添加',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：天府广场',
        inputValidator: (v) => !!String(v || '').trim() || '请输入地点名称'
      }
    )
    const ok = plan.addCustomSpot({
      name: value,
      lng,
      lat,
      city: plan.city,
      address: '地图点选'
    })
    if (ok) ElMessage.success(`已添加「${value.trim()}」，点「一键规划行程」纳入路线`)
  } catch (e) {
    /* 取消 */
  }
}

async function initMap() {
  if (!hasTMapKey()) {
    mapError.value = true
    return
  }
  try {
    const TMap = await loadTMap()
    TMapRef = TMap
    // 真3D地图：baseMap.features加入building3d显示立体楼块
    map = new TMap.Map(mapEl.value, {
      center: new TMap.LatLng(28.2, 112.97),
      zoom: view3d.value ? 15 : 12,
      pitch: view3d.value ? 45 : 0,
      rotation: view3d.value ? 30 : 0,
      baseMap: { type: 'vector', features: ['base', 'building3d'] }
    })

    // 景点标注：每个分类一种样式
    const spotStyles = {}
    Object.keys(CATEGORY_STYLE).forEach((key) => {
      const c = CATEGORY_STYLE[key]
      spotStyles[key] = new TMap.MarkerStyle({
        width: 30,
        height: 38,
        anchor: { x: 15, y: 38 },
        src: pinIcon(c.color, c.symbol)
      })
    })
    spotLayer = new TMap.MultiMarker({ map, styles: spotStyles, geometries: [] })

    // 路线：按出行模式预置多种颜色样式
    const routeStyles = {}
    Object.keys(ROUTE_MODE_COLOR).forEach((key) => {
      routeStyles[key] = new TMap.PolylineStyle({
        color: ROUTE_MODE_COLOR[key],
        width: 6,
        borderWidth: 2,
        borderColor: '#ffffff',
        lineCap: 'round'
      })
    })
    // 多日行程：每天一种颜色，另配一套"向白色淡化"的样式，用于弱化非当前天
    DAY_COLORS.forEach((c, i) => {
      const key = 'd' + (i + 1)
      routeStyles[key] = new TMap.PolylineStyle({
        color: c,
        width: 7,
        borderWidth: 2,
        borderColor: '#ffffff',
        lineCap: 'round'
      })
      routeStyles[key + 'dim'] = new TMap.PolylineStyle({
        color: mixWhite(c, 0.66),
        width: 4,
        borderWidth: 2,
        borderColor: '#ffffff',
        lineCap: 'round'
      })
    })
    routeLineLayer = new TMap.MultiPolyline({ map, styles: routeStyles, geometries: [] })

    // 路线顺序编号（最多20个，样式需在创建时确定）
    const numStyles = {}
    for (let i = 1; i <= 20; i++) {
      numStyles['num' + i] = new TMap.MarkerStyle({
        width: 24,
        height: 24,
        anchor: { x: 12, y: 12 },
        src: numIcon(i)
      })
    }
    // 多日行程：每天的编号底色跟随当天配色（最多7天×每天12个点）
    DAY_COLORS.forEach((c, di) => {
      for (let n = 1; n <= 12; n++) {
        numStyles['d' + (di + 1) + 'n' + n] = new TMap.MarkerStyle({
          width: 24,
          height: 24,
          anchor: { x: 12, y: 12 },
          src: numIcon(n, 24, c)
        })
      }
    })
    routeNumLayer = new TMap.MultiMarker({ map, styles: numStyles, geometries: [] })

    infoWindow = new TMap.InfoWindow({
      map,
      position: new TMap.LatLng(28.2, 112.97),
      offset: { x: 0, y: -42 },
      enableCustom: true
    })
    infoWindow.close()

    spotLayer.on('click', (evt) => {
      const s = spotById[evt.geometry.id]
      if (s) openSpotInfo(s)
    })

    // 「点图添加」模式下的地图点击：把点击位置加成自定义地点
    map.on('click', onMapPick)

    mapReady.value = true
  } catch (e) {
    console.warn('腾讯地图初始化失败：', e?.message || e)
    mapError.value = true
  }
}

function spotInfoHtml(s) {
  return `
    <div class="spot-popup">
      ${s.image ? `<img class="sp-img" src="${s.image}" alt="${s.name}"/>` : ''}
      <div class="sp-title">${s.name}
        <span style="color:#FF8A65;font-size:12px">🔥${s.heat ?? 0}</span>
      </div>
      <div class="sub-text" style="margin-bottom:4px">
        ${SPOT_CATEGORY_MAP[s.category] || ''} · ${s.city || ''}
      </div>
      <div class="sp-desc">${s.description || s.intro || ''}</div>
    </div>`
}

function openSpotInfo(s) {
  if (!infoWindow) return
  infoWindow.setPosition(new TMapRef.LatLng(latOf(s), lngOf(s)))
  infoWindow.setContent(spotInfoHtml(s))
  infoWindow.open()
}

/** 自定义地点 → 与景点同构的对象（category = CUSTOM，复用同一套标注样式） */
function customAsSpot(c) {
  return {
    id: c.key,
    name: c.name,
    category: 'CUSTOM',
    city: c.city,
    lng: c.lng,
    lat: c.lat,
    heat: 0,
    description: c.address ? `自定义地点 · ${c.address}` : '用户自定义地点'
  }
}

function drawSpotMarkers() {
  if (!map) return
  const enabled = new Set(plan.categories)
  // 景点库+用户自己添加的地点，都按分类图例显示/隐藏
  const list = [...plan.spots, ...plan.activeCustomSpots.map(customAsSpot)].filter(
    (s) => enabled.has(s.category) && hasCoord(s)
  )
  Object.keys(spotById).forEach((k) => delete spotById[k])
  list.forEach((s) => {
    spotById[s.id] = s
  })
  spotLayer.setGeometries(
    list.map((s) => ({
      id: String(s.id),
      styleId: CATEGORY_STYLE[s.category] ? s.category : 'NATURE',
      position: new TMapRef.LatLng(latOf(s), lngOf(s))
    }))
  )
}

function boundsOf(list) {
  const bounds = new TMapRef.LatLngBounds()
  list.forEach((p) => bounds.extend(p))
  return bounds
}

function fitToSpots(list) {
  if (!map) return
  const valid = list.filter(hasCoord)
  if (!valid.length) {
    map.setCenter(new TMapRef.LatLng(28.2, 112.97))
    map.setZoom(12)
    return
  }
  map.fitBounds(boundsOf(valid.map((s) => new TMapRef.LatLng(latOf(s), lngOf(s)))), {
    padding: 120
  })
}

/** 景点列表点击：定位并弹出信息窗 */
function focusSpot(s) {
  if (!map || !hasCoord(s)) return
  map.setCenter(new TMapRef.LatLng(latOf(s), lngOf(s)))
  map.setZoom(15)
  openSpotInfo(s)
}

/**
 * 把规划结果画到地图上：
 * - 单日：一条模式配色的折线+顺序编号
 * - 多日：每天一条自己的颜色，非当前天淡化；总览时显示全部编号，选中某天只显示该天编号
 */
function drawRouteOnMap(fit = true) {
  if (!map || !routeLineLayer) return
  const r = plan.planResult
  if (!r) {
    routeLineLayer.setGeometries([])
    routeNumLayer.setGeometries([])
    return
  }

  // ===== 单日 =====
  if (!plan.isMultiDay) {
    const routeMode = ROUTE_MODE_COLOR[r.mode] ? r.mode : plan.mode
    const points = (r.polyline || []).map((p) => new TMapRef.LatLng(p[1], p[0]))
    routeLineLayer.setGeometries(
      points.length ? [{ id: 'route', styleId: routeMode, paths: points }] : []
    )
    // 顺序编号只标在景点上（折线点是真实道路的密集采样点，不能逐点标号）
    routeNumLayer.setGeometries(
      (r.spots || [])
        .filter(hasCoord)
        .slice(0, 20)
        .map((s, i) => ({
          id: 'num' + i,
          styleId: 'num' + (i + 1),
          position: new TMapRef.LatLng(latOf(s), lngOf(s))
        }))
    )
    if (fit && points.length) map.fitBounds(boundsOf(points), { padding: 130 })
    return
  }

  // ===== 多日 =====
  const lines = []
  const nums = []
  plan.dayList.forEach((d, di) => {
    const key = 'd' + (di + 1)
    const points = (d.polyline || []).map((p) => new TMapRef.LatLng(p[1], p[0]))
    if (points.length > 1) {
      const dim = plan.activeDay !== 0 && plan.activeDay !== d.day
      lines.push({ id: key, styleId: dim ? key + 'dim' : key, paths: points })
    }
    // 编号：总览显示所有天（每天从1开始编），选中某天只显示那一天
    if (plan.activeDay === 0 || plan.activeDay === d.day) {
      const daySpots = (d.spots || []).filter(hasCoord).slice(0, 12)
      daySpots.forEach((s, i) => {
        nums.push({
          id: key + 's' + i,
          styleId: key + 'n' + (i + 1),
          position: new TMapRef.LatLng(latOf(s), lngOf(s))
        })
      })
    }
  })
  routeLineLayer.setGeometries(lines)
  routeNumLayer.setGeometries(nums)
  if (fit) fitRoute()
}

/** 视野适配：总览看全部天，选中某天只看那一天；没有规划结果则回到全部景点 */
function fitRoute() {
  if (!map) return
  const r = plan.planResult
  if (!r) {
    fitToSpots(plan.spots)
    return
  }
  const src = plan.isMultiDay
    ? plan.activeDay === 0
      ? plan.dayList.flatMap((d) => d.polyline || [])
      : plan.dayList.find((d) => d.day === plan.activeDay)?.polyline || []
    : r.polyline || []
  const points = src.map((p) => new TMapRef.LatLng(p[1], p[0]))
  if (points.length) map.fitBounds(boundsOf(points), { padding: 130 })
  else fitToSpots(plan.spots)
}

function toggle3d() {
  view3d.value = !view3d.value
  if (!map) return
  if (view3d.value) {
    // 真3D：抬高俯仰角并旋转视角，立体楼块随之显现
    map.setPitch(45)
    map.setRotation(30)
    map.setZoom(Math.max(map.getZoom(), 15))
  } else {
    map.setPitch(0)
    map.setRotation(0)
    nextTick(fitRoute)
  }
}

function zoomBy(d) {
  if (!map) return
  const z = map.getZoom() + d
  map.setZoom(Math.min(20, Math.max(3, z)))
}

async function submitAi() {
  const text = String(aiInput.value || '').trim()
  if (!text || plan.planning) return
  aiInput.value = ''
  if (!aiOpen.value) aiOpen.value = true
  // 「换条路/再来一条/不喜欢」→ 累加轮次，让后端换一批点；说别的要求则回到首轮
  aiRound.value = REPLAN_RE.test(text) ? aiRound.value + 1 : 0
  const before = plan.planResult
  // V2：画像打分+2-opt + AI解释；失败自动降级旧链路
  await plan.smartPlanV2(text, aiRound.value)
  // 只有真的产出了新路线才飞到路线并提示（纯追问时不打扰地图）
  if (plan.planResult && plan.planResult !== before) {
    await nextTick()
    fitRoute()
    ElMessage.success('路线已同步到地图')
  }
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}

function onResize() {
  map?.resize?.()
}

// 地图就绪/景点变化 → 重绘标注；没有规划结果时把视野收到城市景点
watch(
  [mapReady, () => plan.spots, () => plan.activeCustomSpots],
  () => {
    if (!mapReady.value) return
    drawSpotMarkers()
    if (!plan.planResult) {
      fitToSpots([...plan.spots, ...plan.activeCustomSpots.map(customAsSpot)])
    }
  },
  { immediate: true }
)
// 分类勾选变化 → 只重绘标注
watch(() => plan.categories, () => drawSpotMarkers(), { deep: true })
// 规划结果/当前查看的天变化 → 重绘路线
watch(() => plan.planResult, () => drawRouteOnMap(true))
watch(() => plan.activeDay, () => drawRouteOnMap(true))

onMounted(async () => {
  // 旧地址/ai-plan → tab=assist、/info → tab=transport（当前身份不可用时自动落到可用板块）
  const t = route.query.tab
  if ((t === 'assist' || t === 'transport') && midTabs.value.some((x) => x.key === t)) {
    midTab.value = t
  }
  initMap()
  window.addEventListener('resize', onResize)
  await plan.init()
  nextTick(onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  map?.destroy()
  map = null
  mapReady.value = false
})
</script>

<style scoped>
/* ================= 整屏三栏骨架 ================= */
.studio {
  --col-left: 300px;
  --col-mid: 376px;
  height: calc(100vh - 154px);
  min-height: 620px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 16px;
}

/* 顶部标题栏 */
.sd-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--xlvq-shadow);
  padding: 10px 16px;
}
.sd-head-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}
.sd-back {
  font-size: 17px;
  color: #6b7a90;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.18s ease;
}
.sd-back:hover {
  background: #f0f4fa;
}
.sd-title {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}
.sd-sub {
  font-size: 12.5px;
  color: #8a94a6;
}
.sd-chip {
  font-size: 12px;
  font-weight: 600;
  color: var(--xlvq-primary);
  background: var(--xlvq-primary-light);
  border-radius: 20px;
  padding: 3px 10px;
  white-space: nowrap;
}
.sd-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.sd-head-stat {
  font-size: 12.5px;
  color: #6b7a90;
  white-space: nowrap;
}

/* 三栏容器 */
.sd-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: var(--col-left) var(--col-mid) minmax(360px, 1fr);
  gap: 12px;
}
.sd-col {
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--xlvq-shadow);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  position: relative;
}
.sd-col-head {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 14px;
  border-bottom: 1px solid var(--xlvq-border);
}
.sd-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
}
.sd-foot {
  flex: none;
  padding: 10px 12px;
  border-top: 1px solid var(--xlvq-border);
  background: #fbfcfe;
}

/* ================= 左栏：AI助手 ================= */
.sd-ai-badge {
  font-size: 14px;
  font-weight: 700;
}
.sd-help {
  margin-left: auto;
  color: #b6c1d1;
  cursor: help;
}
.ai-bubble {
  background: linear-gradient(135deg, #f2f8ff, #eaf3fe);
  border: 1px solid #d9e8fd;
  border-radius: 12px;
  padding: 10px 12px;
  margin-bottom: 14px;
}
.ai-bubble-head {
  font-size: 12px;
  font-weight: 700;
  color: var(--xlvq-primary);
  margin-bottom: 4px;
}
.ai-bubble-text {
  font-size: 12.5px;
  color: #3d4d63;
  line-height: 1.8;
}
/* V2：「AI记得我」画像卡的渐变外观与文本截断 */
.ai-memory-chip {
  background: linear-gradient(135deg, #f6f0ff, #ece2fd);
  border-color: #e0d2fc;
  cursor: default;
}
.ai-memory-chip .ai-bubble-head {
  color: #7c3aed;
}
.ai-memory-chip .ai-bubble-text.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.7;
}
.ai-thread {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--xlvq-border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ai-msg {
  font-size: 12.5px;
  line-height: 1.75;
  padding: 8px 10px;
  border-radius: 10px;
  word-break: break-word;
}
.ai-msg.me {
  background: #eaf3fe;
  color: #2b4a70;
  align-self: flex-end;
  max-width: 92%;
}
.ai-msg.ai {
  background: #f5f7fb;
  color: #4b5a70;
}
/* 每条AI回答上方标注这条是「大模型」还是「本地规则」解析的 */
.ai-engine {
  display: inline-block;
  font-size: 11px;
  line-height: 1.6;
  padding: 0 6px;
  border-radius: 4px;
  margin-bottom: 5px;
}
.ai-engine.llm {
  background: #eaf1ff;
  color: #2f6fe4;
}
.ai-engine.rule {
  background: #f0f2f5;
  color: #8996a8;
}
/* 底部输入框上方的引擎状态条 */
.ai-engine-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11.5px;
  color: #8996a8;
  margin-bottom: 6px;
  padding-left: 2px;
}
.ai-engine-tip.on {
  color: #2f6fe4;
}
.ai-engine-tip .tip-icon {
  cursor: help;
  color: #b6c1d1;
}

/* ================= 中栏 ================= */
.mid {
  background: #f7f9fc;
}
/* 标签填满整行高度，选中下划线正好压在头部底边上（携程式） */
.mid-head {
  background: #fff;
  gap: 16px;
  align-items: stretch;
}
.mid-tab {
  font-size: 14px;
  color: #6b7a90;
  cursor: pointer;
  position: relative;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  padding-top: 1px;
  transition: color 0.18s ease;
}
.mid-tab:hover {
  color: var(--xlvq-primary);
}
.mid-tab.active {
  color: var(--xlvq-text);
}
.mid-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 2px 2px 0 0;
  background: var(--xlvq-primary);
}
.mid-head-right {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
}
.mid-icon {
  color: #8a94a6;
  cursor: pointer;
  font-size: 15px;
}
.mid-icon:hover {
  color: var(--xlvq-primary);
}
/* 中栏里嵌套的信息卡：去掉阴影、改为细边框，避免"卡片套卡片" */
.mid-scroll :deep(.x-card) {
  box-shadow: none;
  border: 1px solid var(--xlvq-border);
  padding: 14px;
  margin-bottom: 12px;
}
.mid-scroll :deep(.transport-stack) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ================= 右栏：地图 ================= */
.map {
  background: #dfe6f0;
}
/* 覆盖全局 .x-map的固定560px高度：这里要铺满右栏 */
.x-map.studio-map {
  position: absolute;
  inset: 0;
  height: 100%;
  width: 100%;
  border-radius: 0;
}
.map-chips {
  position: absolute;
  left: 12px;
  top: 12px;
  right: 56px;
  z-index: 4;
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}
.map-chips::-webkit-scrollbar {
  display: none;
}
.map-chip {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  font-weight: 600;
  color: #3d4d63;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 999px;
  padding: 6px 12px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.14);
  transition: all 0.18s ease;
  user-select: none;
}
.map-chip:hover {
  transform: translateY(-1px);
}
.map-chip.off {
  color: #a8b3c4;
  background: rgba(255, 255, 255, 0.78);
}
.map-chip.off .mc-sym {
  filter: grayscale(1);
  opacity: 0.5;
}
.mc-sym {
  font-size: 12px;
}
.map-ctrls {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 4;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mc-btn {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.16);
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  color: #3d4d63;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.18s ease;
}
.mc-btn:hover {
  background: #fff;
  color: var(--xlvq-primary);
}
.mc-btn.on {
  background: var(--xlvq-primary);
  color: #fff;
}
.map-day-legend {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 4;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 10px;
  padding: 8px;
  box-shadow: 0 6px 18px rgba(31, 45, 61, 0.16);
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 236px;
}
.mdl-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 7px;
  font-size: 12.5px;
  cursor: pointer;
  transition: background 0.2s ease;
}
.mdl-item:hover {
  background: rgba(47, 128, 237, 0.08);
}
.mdl-item.active {
  background: rgba(47, 128, 237, 0.14);
  font-weight: 600;
}
.mdl-swatch {
  width: 14px;
  height: 6px;
  border-radius: 3px;
  flex: none;
}
.mdl-title {
  flex: 1;
  white-space: nowrap;
}
.mdl-meta {
  color: #8a94a6;
  font-size: 11.5px;
  flex: none;
}
.map-tip {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 4;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 8px;
  padding: 5px 10px;
  font-size: 11.5px;
  color: #6b7a90;
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.12);
}
.map-mask {
  position: absolute;
  inset: 0;
  z-index: 5;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 20px;
}

/* ================= AI搭子：悬浮球+独立对话窗 ================= */
.ai-ball {
  position: fixed;
  right: 26px;
  bottom: 28px;
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px 12px 14px;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(135deg, #7c5cff 0%, #5b8cff 100%);
  box-shadow: 0 10px 26px rgba(92, 92, 255, 0.38), 0 2px 6px rgba(0, 0, 0, 0.08);
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}
.ai-ball:hover {
  transform: translateY(-3px) scale(1.03);
  box-shadow: 0 14px 32px rgba(92, 92, 255, 0.45);
}
.ai-ball.open {
  padding: 12px;
  background: #fff;
  color: #5b5bd6;
  box-shadow: 0 8px 22px rgba(30, 30, 60, 0.18);
}
.ai-ball-icon {
  font-size: 22px;
  line-height: 1;
}
.ai-ball-label {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.ai-ball-ring {
  position: absolute;
  inset: -6px;
  border-radius: 999px;
  border: 2px solid rgba(124, 92, 255, 0.45);
  animation: ai-pulse 2.2s ease-out infinite;
}
@keyframes ai-pulse {
  0% { transform: scale(0.92); opacity: 0.9; }
  70% { transform: scale(1.16); opacity: 0; }
  100% { transform: scale(1.16); opacity: 0; }
}

.ai-win {
  position: fixed;
  z-index: 3001;
  width: 400px;
  height: min(560px, calc(100vh - 160px));
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  background: #fff;
  border: 1px solid #eceaf8;
  box-shadow: 0 24px 60px rgba(31, 29, 72, 0.22), 0 2px 8px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}
.ai-win-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  cursor: move;
  background: linear-gradient(135deg, #f4f1ff 0%, #eef4ff 100%);
  border-bottom: 1px solid #efedf9;
  user-select: none;
}
.ai-win-ava {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  font-size: 18px;
  background: linear-gradient(135deg, #7c5cff, #5b8cff);
  box-shadow: 0 4px 10px rgba(92, 92, 255, 0.28);
}
.ai-win-meta { flex: 1; min-width: 0; }
.ai-win-name { font-size: 14px; font-weight: 800; color: #241f3d; }
.ai-win-sub {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #7a7690;
  margin-top: 2px;
}
.ai-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c9c6d8;
}
.ai-dot.on { background: #2bb673; box-shadow: 0 0 0 3px rgba(43, 182, 115, 0.16); }
.ai-win-acts {
  display: flex;
  gap: 6px;
  color: #8b87a0;
  cursor: pointer;
}
.ai-win-acts .el-icon:hover { color: #5b5bd6; }
.ai-win-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  background: #fbfaff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-welcome {
  text-align: center;
  padding: 18px 10px 8px;
  color: #6b6685;
}
.ai-welcome-emoji { font-size: 30px; }
.ai-welcome-title { font-size: 14px; font-weight: 800; color: #2c2749; margin: 6px 0; }
.ai-welcome-sub { font-size: 12px; line-height: 1.85; color: #7d7896; }
.ai-msg-text { white-space: pre-wrap; word-break: break-word; }
.ai-msg { max-width: 100%; }
.ai-win-quick {
  display: flex;
  gap: 6px;
  padding: 8px 12px;
  overflow-x: auto;
  border-top: 1px solid #f2f0fa;
  background: #fff;
}
.ai-quick {
  flex: none;
  font-size: 11px;
  padding: 5px 10px;
  border-radius: 999px;
  color: #5b5bd6;
  background: #f3f1ff;
  border: 1px solid #e6e2ff;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.18s ease;
}
.ai-quick:hover { background: #e9e4ff; transform: translateY(-1px); }
.ai-win-foot {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 10px 12px 12px;
  border-top: 1px solid #f2f0fa;
  background: #fff;
}
.ai-win-foot :deep(.el-textarea__inner) {
  font-size: 13px;
  border-radius: 12px;
  background: #f7f6fd;
  box-shadow: none;
  border: 1px solid #eae7f7;
}
.ai-typing {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6b6685;
}
.ai-typing i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #a99cf5;
  animation: ai-bounce 1.1s infinite ease-in-out;
}
.ai-typing i:nth-child(2) { animation-delay: 0.15s; }
.ai-typing i:nth-child(3) { animation-delay: 0.3s; }
.ai-typing span { margin-left: 6px; }
@keyframes ai-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-4px); opacity: 1; }
}
.ai-win-pop-enter-active,
.ai-win-pop-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
  transform-origin: bottom right;
}
.ai-win-pop-enter-from,
.ai-win-pop-leave-to {
  opacity: 0;
  transform: scale(0.94) translateY(10px);
}
.ai-open-btn { width: 100%; margin-top: 8px; }
.ai-open-tip {
  margin-top: 6px;
  font-size: 11px;
  color: #9a96ad;
  text-align: center;
}

/* ================= 窄屏降级：改成上下堆叠 ================= */
@media (max-width: 1280px) {
  .studio {
    --col-left: 268px;
    --col-mid: 330px;
  }
}
@media (max-width: 1024px) {
  .studio {
    height: auto;
    min-height: 0;
  }
  .sd-body {
    grid-template-columns: 1fr;
  }
  .sd-col {
    height: 480px;
  }
  .sd-col.map {
    height: 460px;
  }
}
</style>
