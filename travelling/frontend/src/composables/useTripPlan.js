/**
 * 「地图路线」功能的共享状态：目的地、天数、选点方式、预算与规划结果。
 * 规划、行程、信息三块内容共用这一份状态，切换页面回来还能看到上次的结果。
 * 选点方式用spotSelect区分：AUTO由后端按景点热度选点，MANUAL用用户勾选的景点。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi, planApi, routeApi, scrapedApi, spotApi, tripApi, weatherApi } from '@/api'
import { CATEGORY_STYLE } from '@/utils/tmap'
import { BUDGETS, MAX_TRIP_DAYS, ROUTE_MODE_COLOR, ROUTE_MODE_MAP, minutesToText } from '@/utils/format'
import { useAuthStore } from '@/store/auth'

let store = null

/** 获取共享状态（模块级单例：整个功能只有一份，切页面回来还能看到上次的规划） */
export function useTripPlan() {
  if (!store) store = createStore()
  return store
}

function createStore() {
  const auth = useAuthStore()

  /* ================= 城市与景点 ================= */
  const cities = ref([])
  const infoDestinations = ref([]) // 「出行信息」里采集数据覆盖的目的地（可留空=全部）
  const citiesLoaded = ref(false)
  const city = ref('长沙')
  const spots = ref([])
  const spotsLoading = ref(false)
  const spotKeyword = ref('')
  // 地图符号：默认全部景点分类都显示
  const categories = ref(Object.keys(CATEGORY_STYLE))
  const spotSelect = ref('AUTO')
  const selectedSpotIds = ref([])
  // 「自定义添加想去的地点」：不入景点库，随规划请求一起提交；按城市分别保留
  // 每项形如 { key, name, address, lng, lat, city }
  const customSpots = ref([])

  /* ================= 规划条件 ================= */
  const mode = ref('OPTIMAL')
  const tripDays = ref(3)
  const budgetLevel = ref('NONE')
  const startTime = ref('08:30')
  const startSpotId = ref(null)

  /* ================= 规划结果 ================= */
  const planning = ref(false)
  const planResult = ref(null)
  const activeDay = ref(0) // 0=总览，1..N = 第N天
  const savingRoute = ref(false)

  /* ================= 辅助决策 ================= */
  const weather = ref(null)
  const weatherLoading = ref(false)
  const estimate = ref(null)
  const estLoading = ref(false)
  const adjust = ref(null)
  const adjusting = ref(false)

  /* ================= 结伴联动 ================= */
  const myCaptainTrips = ref([])
  const tripId = ref(null)

  /* ================= AI助手对话 ================= */
  const aiMessages = ref([]) // [{ role: 'user' | 'ai', text, engine? }]
  const aiThinking = ref(false)
  // 解析引擎：llm = 后端大模型；rule = 本地关键词规则
  // 后端未配key或大模型超时/失败时，会自动降级到rule，页面照常可用
  const aiReady = ref(false) // 后端是否已接入大模型
  const aiProvider = ref('') // 大模型厂商，如「智谱GLM」
  const aiModelName = ref('') // 模型名，如glm-4-flash
  const aiEngine = ref('') // 最近一次实际使用的通道：'llm' | 'rule' | 'smart'
  const aiMemory = ref(null) // AI搭子的长期画像摘要 { memory, chatCount }（V2）

  /* ================= 计算属性 ================= */
  const isMultiDay = computed(() => (planResult.value?.days?.length || 0) > 1)
  const dayList = computed(() => planResult.value?.days || [])
  /** 当前查看的那一天（单日返回顶层结果；总览返回null） */
  const activeDayData = computed(() => {
    const r = planResult.value
    if (!r) return null
    if (!isMultiDay.value) return r
    if (activeDay.value === 0) return null
    return dayList.value.find((d) => d.day === activeDay.value) || dayList.value[0]
  })
  /** 地图上「总览」那条渐变色带 */
  const legendGradient = computed(() => dayList.value.map((d) => d.color).join(', '))
  const filteredSpots = computed(() => {
    const kw = spotKeyword.value.trim()
    return kw ? spots.value.filter((s) => s.name?.includes(kw)) : spots.value
  })
  /** 当前城市下用户自己添加的地点 */
  const activeCustomSpots = computed(() =>
    customSpots.value.filter((s) => (s.city || city.value) === city.value)
  )
  /** 本次参与选点的景点数（AI模式=全城，手动模式=已勾选；自定义地点始终计入） */
  const planSpotCount = computed(
    () =>
      (spotSelect.value === 'MANUAL' ? selectedSpotIds.value.length : spots.value.length) +
      activeCustomSpots.value.length
  )
  const planModeLabel = computed(() => ROUTE_MODE_MAP[planResult.value?.mode || mode.value] || '路线')
  /** 行程标题：如「长沙3日经典游 · 最优路线」（页面顶部标题栏用） */
  const tripTitle = computed(
    () =>
      `${city.value || '未选目的地'}${tripDays.value}日游 · ${
        ROUTE_MODE_MAP[planResult.value?.mode || mode.value] || '路线'
      }`
  )
  /** AI助手的一句话摘要（规划完成后回给用户看） */
  const aiSummary = computed(() => {
    const r = planResult.value
    if (!r) return '还没有行程，先告诉我你想去哪、玩几天吧。'
    const days = r.dayCount || r.days?.length || 1
    return `已生成${days} 天行程，共${r.spots?.length || 0} 个景点，总里程${
      r.totalDistanceKm ?? 0
    } km，约${minutesToText(r.totalDurationMin)}。${r.advice || ''}`
  })

  /* ================= 动作 ================= */
  /** 目的地下拉：景点城市 ∪ 采集数据城市（只加载一次） */
  async function ensureCities() {
    if (citiesLoaded.value) return cities.value
    const [spotCities, scrapedCities] = await Promise.all([
      spotApi.cities().catch(() => []),
      scrapedApi.cities().catch(() => [])
    ])
    const norm = (data) =>
      (Array.isArray(data) ? data : data?.list || [])
        .map((c) => (typeof c === 'string' ? c : c.city || c.name))
        .filter(Boolean)
    const list = norm(spotCities)
    if (!list.includes('长沙')) list.unshift('长沙')
    cities.value = list
    infoDestinations.value = [...new Set([...list, ...norm(scrapedCities)])]
    citiesLoaded.value = true
    return list
  }

  /** 载入当前城市的景点，并把「手动勾选」默认设为全选 */
  async function loadSpots() {
    spotsLoading.value = true
    try {
      const data = await spotApi.list({ city: city.value }).catch(() => [])
      spots.value = data?.list || data || []
      selectedSpotIds.value = spots.value.map((s) => s.id)
      startSpotId.value = null // 换城市后起点重置（默认改为热度最高景点）
      planResult.value = null
      activeDay.value = 0
      adjust.value = null
    } finally {
      spotsLoading.value = false
    }
  }

  function selectAllSpots() {
    selectedSpotIds.value = spots.value.map((s) => s.id)
  }

  function clearSpots() {
    selectedSpotIds.value = []
  }

  /**
   * 添加一个「我想去的地方」。
   * 必须已经解析出经纬度（由地点搜索得到），否则无法参与路线计算。
   * 同名同城市视为重复，不重复添加。
   */
  function addCustomSpot({ name, address, lng, lat, city: targetCity }) {
    const n = String(name || '').trim()
    if (!n || lng == null || lat == null) return false
    const target = targetCity || city.value
    if (customSpots.value.some((s) => s.name === n && s.city === target)) {
      ElMessage.info(`「${n}」已经在你的地点列表里了`)
      return false
    }
    customSpots.value.push({
      key: `custom_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      name: n,
      address: address || '',
      lng: Number(lng),
      lat: Number(lat),
      city: target
    })
    planResult.value = null // 条件已变，清掉旧行程避免误导
    return true
  }

  function removeCustomSpot(key) {
    customSpots.value = customSpots.value.filter((s) => s.key !== key)
    planResult.value = null
  }

  /** 清空当前城市的自定义地点（其他城市的保留） */
  function clearCustomSpots() {
    customSpots.value = customSpots.value.filter((s) => s.city !== city.value)
    planResult.value = null
  }

  /** 分类符号点击：地图上显示/隐藏这一类景点（顺序仍按符号表，便于对照图例） */
  function toggleCategory(key) {
    if (!CATEGORY_STYLE[key]) return
    const kept = new Set(categories.value)
    if (kept.has(key)) kept.delete(key)
    else kept.add(key)
    categories.value = Object.keys(CATEGORY_STYLE).filter((k) => kept.has(k))
  }

  async function selectCity(c) {
    if (!c) return
    city.value = c
    await loadSpots()
  }

  async function loadWeather() {
    if (!city.value) {
      weather.value = null
      return
    }
    weatherLoading.value = true
    try {
      weather.value = await weatherApi.forecast(city.value, tripDays.value)
    } catch (e) {
      weather.value = null
    } finally {
      weatherLoading.value = false
    }
  }

  async function loadEstimate() {
    if (!city.value) {
      estimate.value = null
      return
    }
    estLoading.value = true
    try {
      estimate.value = await scrapedApi.costEstimate({
        destination: city.value,
        days: tripDays.value,
        budgetLevel: budgetLevel.value
      })
    } catch (e) {
      estimate.value = null
    } finally {
      estLoading.value = false
    }
  }

  /** 生成行程：唯一一处调用POST/api/routes/plan，结果同时驱动地图折线、时间轴、天气与费用 */
  async function generate() {
    if (!city.value) {
      ElMessage.warning('请选择目的地')
      return
    }
    if (
      spotSelect.value === 'MANUAL' &&
      !selectedSpotIds.value.length &&
      !activeCustomSpots.value.length
    ) {
      ElMessage.warning('请至少勾选一个景点，或切换到「AI 自动选点」')
      return
    }
    planning.value = true
    try {
      const payload = {
        city: city.value,
        mode: mode.value,
        days: tripDays.value,
        startSpotId: startSpotId.value || null,
        startMinutes:
          Number(startTime.value?.slice(0, 2) || 8) * 60 + Number(startTime.value?.slice(3, 5) || 30)
      }
      if (spotSelect.value === 'MANUAL') payload.spotIds = selectedSpotIds.value
      // 自定义地点随规划一起提交（后端转成临时景点，始终纳入路线）
      if (activeCustomSpots.value.length) {
        payload.customSpots = activeCustomSpots.value.map((s) => ({
          name: s.name,
          lng: s.lng,
          lat: s.lat
        }))
      }

      const result = await routeApi.plan(payload)
      planResult.value = result
      activeDay.value = 0 // 多日默认先看总览
      ElMessage.success(
        result.multiDay
          ? `已生成${result.dayCount} 天行程（按地理位置自动分天）`
          : '行程已生成'
      )
    } finally {
      planning.value = false
    }
  }

  /**
   * 从一句话里解析出旅行条件（本地正则规则）。
   * 大模型通道不可用时降级到这里；「长沙3天」这类短句直接走本地解析，不调大模型。
   */
  function parseIntent(text) {
    const CN_NUM = { 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7 }
    // 「三天」「两天」等中文数字先转成阿拉伯数字，再统一用正则解析
    const t = String(text || '').replace(/([一二两三四五六七])\s*天/g, (m, c) => `${CN_NUM[c]}天`)
    const hit = {}

    const cityHit = cities.value.find((c) => c && t.includes(c))
    if (cityHit) hit.city = cityHit

    const dm = /(\d+)\s*(天|日)/.exec(t)
    if (dm) hit.tripDays = Math.min(MAX_TRIP_DAYS, Math.max(1, Number(dm[1])))

    const modeRules = [
      [/速通|特种兵|紧凑|赶时间/, 'SPEED'],
      [/休闲|慢游|放松|养老|悠着/, 'LEISURE'],
      [/美食|好吃|吃/, 'FOOD'],
      [/打卡|景区|看景|风光/, 'SCENIC'],
      [/野路|刺激|探险|户外野/, 'WILD'],
      [/citywalk|城市漫步|逛街|闲逛|漫步/i, 'CITYWALK'],
      [/最优|顺路|省时|高效/, 'OPTIMAL']
    ]
    for (const [re, key] of modeRules) {
      if (re.test(t)) {
        hit.mode = key
        break
      }
    }

    const bm = /(\d{3,5})\s*(元|块|米)/.exec(t)
    if (bm) {
      const limit = Number(bm[1])
      const sorted = BUDGETS.filter((b) => b.value !== 'NONE')
        .map((b) => ({ ...b, n: Number(String(b.value).replace('LE', '')) }))
        .sort((a, b) => a.n - b.n)
      const pick = sorted.find((b) => b.n >= limit) || sorted[sorted.length - 1]
      if (pick) hit.budgetLevel = pick.value
    }

    // 出发时间：支持「8点出发」「08:30」「下午3点」
    const tm = /(\d{1,2})\s*[:：]\s*(\d{2})/.exec(t) || /(\d{1,2})\s*点/.exec(t)
    if (tm && /出发|早上|上午|中午|下午|晚上|\d\s*点/.test(t)) {
      let h = Math.min(23, Number(tm[1]))
      if (/下午|晚上/.test(t) && h < 12) h += 12
      const m = tm[2] ? Math.min(59, Number(tm[2])) : 0
      hit.startTime = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
    }

    return hit
  }

  /** 解析结果里到底识别出了几个条件（用来判断这次问话有没有「听懂」） */
  function hitCount(hit) {
    return ['city', 'tripDays', 'mode', 'budgetLevel', 'startTime'].filter((k) => hit?.[k] != null)
      .length
  }

  /**
   * 请后端大模型解析一句话，未配置key、超时或返回异常时返回null，由调用方降级到本地规则。
   * 城市、模式、预算的合法性后端已校验，这里直接采信。
   */
  async function parseIntentByLlm(text) {
    if (!aiReady.value) return null
    const data = await aiApi.parseIntent(text)
    return data?.source === 'llm' ? data : null
  }

  /** 查询后端大模型接入状态（页面初始化时调一次） */
  async function fetchAiStatus() {
    try {
      const s = await aiApi.status()
      aiReady.value = !!s?.enabled
      aiProvider.value = s?.provider || ''
      aiModelName.value = s?.model || ''
    } catch (e) {
      aiReady.value = false
    }
  }

  /**
   * AI助手提问：解析 → 回填条件 → 重新规划 → 在对话里回一条摘要。
   * 解析走「大模型优先、本地规则兜底」双通道；返回一条回复文案，供页面直接显示。
   */
  async function askAi(text) {
    const msg = String(text || '').trim()
    if (!msg) return ''
    aiMessages.value.push({ role: 'user', text: msg })
    aiThinking.value = true
    try {
      // ①优先请大模型理解（静默失败：未配置/超时/报错都只返回null）
      let hit = null
      let engine = 'rule'
      let llmReply = ''
      try {
        const llm = await parseIntentByLlm(msg)
        if (llm) {
          // 即使这次一个条件都没解析出来，模型的那句话也能拿来回复用户
          llmReply = llm.reply || ''
          if (hitCount(llm)) {
            hit = llm
            engine = 'llm'
          }
        }
      } catch (e) {
        /* 静默降级到本地规则 */
      }
      // ②大模型不可用、或这次没听懂 → 本地关键词规则兜底
      if (!hit) {
        hit = parseIntent(msg)
        engine = 'rule'
      }
      aiEngine.value = engine

      const labels = []
      if (hit.city && cities.value.includes(hit.city)) {
        await selectCity(hit.city)
        labels.push(`目的地${hit.city}`)
      }
      if (hit.tripDays) {
        tripDays.value = hit.tripDays
        labels.push(`${hit.tripDays} 天`)
      }
      if (hit.mode && ROUTE_MODE_MAP[hit.mode]) {
        mode.value = hit.mode
        labels.push(`${ROUTE_MODE_MAP[hit.mode]}`)
      }
      if (hit.budgetLevel && BUDGETS.some((b) => b.value === hit.budgetLevel)) {
        budgetLevel.value = hit.budgetLevel
        labels.push(`预算${BUDGETS.find((b) => b.value === hit.budgetLevel)?.label}`)
      }
      if (hit.startTime) {
        startTime.value = hit.startTime
        labels.push(`${hit.startTime} 出发`)
      }

      if (!labels.length) {
        const reply =
          llmReply ||
          '没太听懂～可以这样说：「帮我规划长沙3天休闲游，预算1500元，8点出发」，' +
            '或者说「张家界2天速通」。'
        aiMessages.value.push({ role: 'ai', text: reply, engine })
        return reply
      }

      await generate()
      const reply = `好，已按「${labels.join('、')}」重新规划：${aiSummary.value}`
      aiMessages.value.push({ role: 'ai', text: reply, engine })
      return reply
    } catch (e) {
      const reply = `规划失败：${e?.message || '请检查网络或换组条件再试'}`
      aiMessages.value.push({ role: 'ai', text: reply, engine: aiEngine.value })
      return reply
    } finally {
      aiThinking.value = false
    }
  }

  /**
   * V2智能规划：后端一次完成画像选点、2-opt串线与拟人解释，
   * 返回与/routes/plan同构的行程，直接写入planResult，地图、时间轴、图例随之联动。
   * 失败（未登录、没识别出城市、服务异常）时降级回本地链路askAi。
   */
  async function smartPlanV2(text, round = 0) {
    const msg = String(text || '').trim()
    if (!msg) return ''
    aiMessages.value.push({ role: 'user', text: msg })
    aiThinking.value = true
    try {
      const planData = await aiApi.smartPlan(msg, round)
      // 后端没听出目的地 → 返回clarify（一句自然追问），比固定模板更像个活人
      if (planData?.clarify && planData.askText) {
        aiMessages.value.push({ role: 'ai', text: planData.askText, engine: 'llm' })
        return planData.askText
      }
      if (!planData?.spots?.length) throw new Error('empty')
      planResult.value = planData
      activeDay.value = 0
      aiEngine.value = 'smart'
      const reply =
        planData.aiExplanation ||
        `已按你的画像生成个性化路线（${planData.spots.length} 个点），路线已在地图上标出～`
      aiMessages.value.push({
        role: 'ai',
        text: reply,
        engine: 'smart',
        personalized: !!planData.aiPlan?.profile
      })
      return reply
    } catch (e) {
      // V2没接住（多半是没听出目的地）：撤掉刚push的用户消息，交给旧链路完整重放
      aiMessages.value.pop()
      return await askAi(text)
    } finally {
      aiThinking.value = false
    }
  }

  /** 拉取AI搭子的长期画像（地图页左栏「AI记得我」卡片展示） */
  async function fetchAiMemory() {
    try {
      const m = await aiApi.memory()
      aiMemory.value = m?.chatCount != null || m?.memory ? m : null
    } catch (e) {
      aiMemory.value = null // 未登录/无记忆：不展示
    }
  }

  /** 保存当前规划到「我的路线」 */
  async function saveRoute() {
    const r = planResult.value
    if (!r) return
    const multi = isMultiDay.value
    const modeLabel = ROUTE_MODE_MAP[r.mode || mode.value] || '路线'
    const { value: name } = await ElMessageBox.prompt('给这条路线起个名字', '保存路线', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: multi ? `${city.value}·${modeLabel}·${r.dayCount}日` : `${city.value}·${modeLabel}`,
      inputValidator: (v) => !!v?.trim() || '请输入路线名称'
    })
    savingRoute.value = true
    try {
      // 多日行程按整天保存（景点按天顺序拼接）；单日保存当前天的实际景点
      const spotIds = multi
        ? (r.spots || []).map((s) => s.id)
        : (activeDayData.value?.spots || []).map((s) => s.id)
      await routeApi.save({
        name: name.trim(),
        city: city.value,
        mode: r.mode || mode.value,
        spotIdsCsv: spotIds.join(','),
        color: r.color || ROUTE_MODE_COLOR[mode.value],
        totalDistance: r.totalDistanceKm,
        totalDuration: r.totalDurationMin,
        riskLevel: r.riskLevel,
        tips: multi
          ? `${r.dayCount} 天行程，共${spotIds.length} 个景点。${r.advice || ''}`
          : r.tips
      })
      ElMessage.success('路线已保存，可在个人中心查看')
    } finally {
      savingRoute.value = false
    }
  }

  /** 按天气生成动态调整方案（可顺带通知关联结伴行程的同行成员） */
  async function doAdjust() {
    if (!city.value) {
      ElMessage.warning('请先选择目的地')
      return
    }
    adjusting.value = true
    try {
      const body = {
        destination: city.value,
        days: tripDays.value,
        dayPlans: dayList.value.map((d) => ({ title: d.title, anchor: d.anchor }))
      }
      if (tripId.value) body.tripId = tripId.value
      adjust.value = await planApi.adjust(body)
      if (!adjust.value?.badCount) {
        ElMessage.success('天气良好，行程无需调整')
      } else {
        ElMessage.warning(`已生成调整方案，${adjust.value.badCount} 天需关注`)
      }
    } finally {
      adjusting.value = false
    }
  }

  async function loadMyTrips() {
    if (!auth.isLoggedIn) return
    const list = (await tripApi.my().catch(() => [])) || []
    myCaptainTrips.value = list.filter((t) => t.captainId === auth.user?.id)
  }

  /** 页面首次进入：城市+景点+天气+费用+大模型接入状态 */
  async function init() {
    await ensureCities()
    if (!cities.value.includes(city.value)) city.value = cities.value[0] || '长沙'
    if (!spots.value.length) await loadSpots()
    await Promise.all([loadWeather(), loadEstimate(), fetchAiStatus(), fetchAiMemory()])
    loadMyTrips()
  }

  // 目的地/天数/预算改变时，天气与费用估算自动重算（替换原来AI行程 页面里的watch）
  watch([city, tripDays, budgetLevel], () => {
    loadWeather()
    loadEstimate()
  })

  /* ================= 账号切换隔离（V4修复） =================
   * 本store是模块级单例（跨页面共享），如果不清理，上一个账号的
   * AI对话/规划路线/结伴数据会在退出登录、换账号后原样出现给
   * 下一个账号——表现为「我的AI对话跑到了别人账号上」。
   * 这里监听当前用户id：一旦变化（含登出变null），立即清空所有
   * 「个人相关」状态；城市/景点/天气等公共缓存保留。 */
  const currentUserId = computed(() => auth.user?.id ?? null)
  watch(currentUserId, (nv, ov) => {
    if (nv === ov) return
    aiMessages.value = []
    aiThinking.value = false
    aiMemory.value = null
    aiEngine.value = ''
    planResult.value = null
    activeDay.value = 0
    planning.value = false
    savingRoute.value = false
    adjust.value = null
    adjusting.value = false
    estimate.value = null
    myCaptainTrips.value = []
    tripId.value = null
  })

  return reactive({
    // 城市与景点
    cities,
    infoDestinations,
    city,
    spots,
    spotsLoading,
    spotKeyword,
    categories,
    spotSelect,
    selectedSpotIds,
    filteredSpots,
    customSpots,
    activeCustomSpots,
    // 规划条件
    mode,
    tripDays,
    budgetLevel,
    startTime,
    startSpotId,
    planSpotCount,
    // 规划结果
    planning,
    planResult,
    activeDay,
    activeDayData,
    dayList,
    isMultiDay,
    legendGradient,
    planModeLabel,
    tripTitle,
    savingRoute,
    // AI助手
    aiMessages,
    aiThinking,
    aiSummary,
    aiReady,
    aiProvider,
    aiModelName,
    aiEngine,
    aiMemory,
    // 辅助决策
    weather,
    weatherLoading,
    estimate,
    estLoading,
    adjust,
    adjusting,
    // 结伴
    myCaptainTrips,
    tripId,
    // 动作
    ensureCities,
    init,
    loadSpots,
    selectCity,
    selectAllSpots,
    clearSpots,
    addCustomSpot,
    removeCustomSpot,
    clearCustomSpots,
    toggleCategory,
    generate,
    parseIntent,
    parseIntentByLlm,
    fetchAiStatus,
    fetchAiMemory,
    askAi,
    smartPlanV2,
    loadWeather,
    loadEstimate,
    saveRoute,
    doAdjust,
    loadMyTrips
  })
}
