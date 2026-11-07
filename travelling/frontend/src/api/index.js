import axios from 'axios'
import { ElMessage } from 'element-plus'

// 统一axios实例：baseURL=/api，由vite代理转发到http://localhost:8080
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：自动携带Bearer Token
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('xlvq_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

function clearAuthAndRedirect() {
  localStorage.removeItem('xlvq_token')
  localStorage.removeItem('xlvq_user')
  const hash = window.location.hash
  if (!window.location.pathname.endsWith('/login') && !hash.includes('/login')) {
    const redirect = encodeURIComponent(
      window.location.pathname + window.location.search + window.location.hash
    )
    // hash模式与history模式均兼容；带上前端挂载前缀（/travelling/）
    window.location.href = `${import.meta.env.BASE_URL}login?redirect=${redirect}`
  }
}

// 响应拦截器：统一处理 {code,message,data}
// config.silent = true的请求不弹错误提示（用于「可降级」的接口，例如大模型未配置）
// config.silentForbidden = true时，仅403（权限不足）不弹提示，其余错误照常提示
// （用于「当前角色本来就无权访问、页面对该角色也隐藏了」的接口，避免一进页面就弹一串红字）
function shouldSilence(config, code) {
  return !!config?.silent || (!!config?.silentForbidden && code === 403)
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 401) {
        clearAuthAndRedirect()
        return Promise.reject(new Error(body.message || '登录已失效'))
      }
      if (!shouldSilence(response.config, body.code)) {
        ElMessage.error(body.message || '请求失败')
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      clearAuthAndRedirect()
      return Promise.reject(error)
    }
    if (error.config?.silent) return Promise.reject(error)
    const msg =
      error.response?.data?.message ||
      (error.code === 'ECONNABORTED'
        ? '请求超时，请稍后重试'
        : error.message || '网络异常，请稍后重试')
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

/* ================= 认证 ================= */
export const authApi = {
  register: (data) => http.post('/auth/register', data),
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me')
}

/* ================= 用户 ================= */
export const userApi = {
  get: (id) => http.get(`/users/${id}`),
  /** 头像信息卡：学院、专业、届别、认证徽章、信誉标签与标签档案，对外公开 */
  card: (id) => http.get(`/users/${id}/card`),
  updateProfile: (data) => http.put('/users/profile', data),
  verify: (data) => http.post('/users/verify', data),
  applyBlogger: () => http.post('/users/apply-blogger'),
  adminUpdate: (id, data) => http.put(`/users/${id}/admin`, data),
  rankings: () => http.get('/users/rankings/bloggers'),
  search: (keyword) => http.get('/users', { params: { keyword } })
}

/* ================= 标签字典 ================= */
export const tagApi = {
  /** 全量字典，按scope(USER/POST/GUIDE) -> dimension分组 */
  dict: () => http.get('/tags/dict'),
  dictOfScope: (scope) => http.get(`/tags/dict/${scope}`),
  /** 冷启动核心标签（新用户必须填的4个维度） */
  core: () => http.get('/tags/core')
}

/* ================= 半熟人撮合与契合度推荐 ================= */
export const matchApi = {
  /** 给我推荐契合度最高的N个人 */
  people: (limit = 10) => http.get('/match/people', { params: { limit } }),
  /** 我与指定用户的契合度明细（逐维解释） */
  detail: (targetId) => http.get(`/match/people/${targetId}`),
  pref: () => http.get('/match/pref'),
  savePref: (data) => http.put('/match/pref', data),
  /** 我的档案是否已填满核心标签 */
  poolStatus: () => http.get('/match/pool-status')
}

/* ================= 匿名聚合洞察（对外公开，游客可见） ================= */
// 商家对这组公开看板接口无权限（后端403），且页面对商家隐藏了看板标签，
// 故silentForbidden：403静默、其他错误照常提示，避免进页面弹一串红字
export const insightApi = {
  /** 看板概览：门槛/数据量/脱敏机制说明 */
  summary: () => http.get('/insight/summary', { silentForbidden: true }),
  /** 目的地热度指数（公开脱敏聚合） */
  destinationHeat: () => http.get('/insight/destination-heat', { silentForbidden: true }),
  /** 学生消费画像（公开脱敏聚合） */
  consumptionProfile: () => http.get('/insight/consumption-profile', { silentForbidden: true }),
  /** 兴趣—目的地—消费 桑基流转（公开脱敏聚合） */
  interestFlow: () => http.get('/insight/interest-flow', { silentForbidden: true })
}

/* ================= 圈层 ================= */
export const circleApi = {
  list: () => http.get('/circles'),
  create: (data) => http.post('/circles', data),
  detail: (id) => http.get(`/circles/${id}`),
  join: (id) => http.post(`/circles/${id}/join`),
  leave: (id) => http.post(`/circles/${id}/leave`),
  members: (id) => http.get(`/circles/${id}/members`)
}

/* ================= 帖子 ================= */
export const postApi = {
  list: (params) => http.get('/posts', { params }),
  create: (data) => http.post('/posts', data),
  detail: (id) => http.get(`/posts/${id}`),
  update: (id, data) => http.put(`/posts/${id}`, data),
  remove: (id) => http.delete(`/posts/${id}`),
  like: (id) => http.post(`/posts/${id}/like`),
  favorite: (id) => http.post(`/posts/${id}/favorite`),
  cite: (id) => http.post(`/posts/${id}/cite`),
  comments: (id) => http.get(`/posts/${id}/comments`),
  addComment: (id, content) => http.post(`/posts/${id}/comments`, { content }),
  pending: () => http.get('/posts/pending'),
  audit: (id, data) => http.post(`/posts/${id}/audit`, data)
}

/* ================= 结伴出行 ================= */
export const tripApi = {
  list: (params) => http.get('/trips', { params }),
  create: (data) => http.post('/trips', data),
  detail: (id) => http.get(`/trips/${id}`),
  /** 队长团队管理页聚合数据（队员+足迹+消费） */
  team: (id) => http.get(`/trips/${id}/team`),
  /** 组队经历：本队成员（含队长）的历史结伴记录 */
  histories: (id) => http.get(`/trips/${id}/histories`),
  apply: (id, reason, pref) => http.post(`/trips/${id}/apply`, { reason, pref }),
  reviewMember: (id, memberId, action) => {
    // 前端页面习惯传 'approve'/'reject'，后端要求 'APPROVED'/'REJECTED'
    const normalized =
      action === 'approve' || action === 'APPROVED' ? 'APPROVED' : 'REJECTED'
    return http.post(`/trips/${id}/members/${memberId}/review`, { action: normalized })
  },
  updateStatus: (id, status) => http.post(`/trips/${id}/status`, { status }),
  my: () => http.get('/trips/my'),
  shareLocation: (id, lng, lat) =>
    http.post(`/trips/${id}/share-location`, { lng, lat }),
  addExpense: (id, data) => http.post(`/trips/${id}/expenses`, data),
  rate: (id, data) => http.post(`/trips/${id}/rate`, data),
  // 结伴偏好与智能推荐
  recommend: (limit = 3) => http.get('/trips/recommend', { params: { limit } }),
  getPref: () => http.get('/trips/pref'),
  /** 动态兴趣画像：系统从足迹/发帖/报名/圈子里推断出的兴趣与依据 */
  getPrefProfile: () => http.get('/trips/pref/profile'),
  savePref: (data) => http.put('/trips/pref', data)
}

/* ================= 景点 ================= */
export const spotApi = {
  list: (params) => http.get('/spots', { params }),
  cities: () => http.get('/spots/cities'),
  detail: (id) => http.get(`/spots/${id}`),
  create: (data) => http.post('/spots', data)
}

/* ================= 路线规划 ================= */
export const routeApi = {
  plan: (data) => http.post('/routes/plan', data),
  save: (data) => http.post('/routes', data),
  list: (params) => http.get('/routes', { params }),
  detail: (id) => http.get(`/routes/${id}`)
}

/* ================= 地点搜索定位（地名 → 经纬度） ================= */
export const geoApi = {
  /** 返回候选地点列表；source表示来源（poi/spot/none），reason为降级原因 */
  search: (keyword, city) =>
    http.get('/geo/search', { params: { keyword, city: city || undefined }, silent: true })
}

/* ================= 需求洞察（商家/导游端） ================= */
export const demandApi = {
  /** 供需缺口+出行方式占比+预算分布+主题需求 */
  insight: () => http.get('/demand/insight')
}

/* ================= 创作者中心（博主的自有数据回喂） ================= */
export const creatorApi = {
  /** 博客分/等级/排名+逐篇表现+月度趋势+结论+选题建议 */
  insight: () => http.get('/creator/insight')
}

/* ================= 文件上传 ================= */
export const uploadApi = {
  /** 上传图片，返回/images/footprints/xxx.jpg这种可直接使用的路径 */
  image: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

/* ================= 足迹 ================= */
export const footprintApi = {
  mine: () => http.get('/footprints/mine'),
  user: (userId) => http.get(`/footprints/user/${userId}`),
  create: (data) => http.post('/footprints', data),
  remove: (id) => http.delete(`/footprints/${id}`)
}

/* ================= 出行方式 ================= */
export const transportApi = {
  // 出发地+目的地：比较两城之间的所有出行方式
  list: (origin, destination) =>
    http.get('/transport', { params: { origin, destination: destination || undefined } }),
  // 任意两城：库内基准优先，缺失方式由12306采集+腾讯地图里程计算补足
  // silent：后端未升级（没有该接口）时静默失败，前端自动退回「只查库」的老逻辑
  plan: (origin, destination) =>
    http.get('/transport/plan', { params: { origin, destination }, silent: true }),
  // 当前有数据的出发地/目的地（下拉框选项）
  cities: () => http.get('/transport/cities')
}

/* ================= 紧急联系人/保险 ================= */
export const emergencyApi = {
  list: () => http.get('/emergency'),
  create: (data) => http.post('/emergency', data),
  update: (id, data) => http.put(`/emergency/${id}`, data),
  remove: (id) => http.delete(`/emergency/${id}`)
}

export const insuranceApi = {
  purchase: (data) => http.post('/insurance/purchase', data),
  mine: () => http.get('/insurance/mine')
}

/* ================= 消息/通知 ================= */
export const messageApi = {
  conversations: () => http.get('/messages/conversations'),
  withPeer: (peerId) => http.get('/messages', { params: { peerId } }),
  send: (toId, content) => http.post('/messages', { toId, content })
}

export const notificationApi = {
  list: () => http.get('/notifications'),
  read: () => http.put('/notifications/read')
}

/* ================= 商家 ================= */
export const merchantApi = {
  apply: (data) => http.post('/merchants/apply', data),
  list: (status) =>
    http.get('/merchants', { params: status ? { status } : {} }),
  mine: () => http.get('/merchants/mine'),
  audit: (id, approve) =>
    http.post(`/merchants/${id}/audit`, { approve })
}

/* ================= 商品/订单 ================= */
export const productApi = {
  list: (params) => http.get('/products', { params }),
  detail: (id) => http.get(`/products/${id}`),
  /** 商家销售洞察：交易量/季度高峰/品类结构/备货建议（仅商家本人） */
  merchantInsight: () => http.get('/products/merchant/insight'),
  /** 商品平替/升级替代（公开，游客可看） */
  alternatives: (id) => http.get(`/products/${id}/alternatives`, { silent: true }),
  create: (data) => http.post('/products', data),
  update: (id, data) => http.put(`/products/${id}`, data)
}

export const orderApi = {
  create: (data) => http.post('/orders', data),
  mine: () => http.get('/orders/mine'),
  shop: () => http.get('/orders/shop'),
  ship: (id) => http.post(`/orders/${id}/ship`),
  confirm: (id) => http.post(`/orders/${id}/confirm`)
}

/* ================= 导游 ================= */
export const guideApi = {
  apply: (data) => http.post('/guides/apply', data),
  /** 导游档案列表：默认只返回已认证（APPROVED）；传 'all' 走管理后台审核队列（仅管理员） */
  profiles: (status) =>
    http.get('/guides/profiles', {
      params: status === 'all' ? { status: 'all' } : {}
    }),
  /** 我自己的导游申请（任意状态）；未申请返回null。老后端无此接口时静默忽略 */
  myProfile: () => http.get('/guides/my-profile', { silent: true }),
  /** 导游公开主页：过往接待经历+评价+在售服务（游客可看） */
  publicProfile: (userId) => http.get(`/guides/${userId}/public`),
  auditProfile: (id, approve) =>
    http.post(`/guides/profiles/${id}/audit`, { approve }),
  services: (destination) =>
    http.get('/guides/guide-services', {
      params: destination ? { destination } : {}
    }),
  createService: (data) => http.post('/guides/guide-services', data),
  updateService: (id, data) =>
    http.put(`/guides/guide-services/${id}`, data),
  bookings: (role) =>
    http.get('/guides/guide-bookings', { params: role ? { role } : {} }),
  createBooking: (data) => http.post('/guides/guide-bookings', data),
  confirmBooking: (id) => http.post(`/guides/guide-bookings/${id}/confirm`),
  completeBooking: (id) => http.post(`/guides/guide-bookings/${id}/complete`),
  reviewBooking: (id, data) => http.post(`/guides/guide-bookings/${id}/review`, data),
  cancelBooking: (id) => http.post(`/guides/guide-bookings/${id}/cancel`)
}

/* ================= 广告 ================= */
export const adApi = {
  bySlot: (slot) => http.get('/ads', { params: { slot } }),
  /** 商家投放中心：我的广告（含曝光/点击/审核状态） */
  mine: () => http.get('/ads/mine'),
  admin: () => http.get('/ads/admin'),
  create: (data) => http.post('/ads', data),
  audit: (id, approve) => http.post(`/ads/${id}/audit`, { approve }),
  view: (id) => http.post(`/ads/${id}/view`),
  click: (id) => http.post(`/ads/${id}/click`)
}

/* ================= 商品评价（商家「评价管理」） ================= */
export const productReviewApi = {
  /** 某商品的公开评价 */
  byProduct: (productId) => http.get('/product-reviews', { params: { productId } }),
  /** 我发表过的评价（订单页用于判断已评价） */
  mine: () => http.get('/product-reviews/mine'),
  /** 商家：本店收到的评价+统计 */
  merchant: () => http.get('/product-reviews/merchant'),
  create: (data) => http.post('/product-reviews', data),
  reply: (id, reply) => http.post(`/product-reviews/${id}/reply`, { reply })
}

/* ================= 平台结算（管理后台） ================= */
export const settlementApi = {
  overview: () => http.get('/admin/settlements'),
  payout: (type, targetId) => http.post('/admin/settlements/payout', { type, targetId })
}

/* ================= 举报 ================= */
export const reportApi = {
  create: (data) => http.post('/reports', data),
  list: () => http.get('/reports'),
  handle: (id, result) => http.post(`/reports/${id}/handle`, { result })
}

/* ================= 信誉分体系与同行人投诉申诉 ================= */
export const creditApi = {
  profile: () => http.get('/credit/profile'),
  brief: (userId) => http.get(`/credit/users/${userId}`),
  myComplaints: () => http.get('/credit/complaints/mine'),
  tripComplaints: (tripId) => http.get(`/trips/${tripId}/complaints`),
  createComplaint: (tripId, data) => http.post(`/trips/${tripId}/complaints`, data),
  appeal: (id, reason) => http.post(`/credit/complaints/${id}/appeal`, { reason }),
  handle: (id, action, result) => http.post(`/credit/complaints/${id}/handle`, { action, result }),
  /** 管理员投诉工作台：全量投诉+KPI + 类型分布（支持status/type/keyword/page/size） */
  adminComplaints: (params) => http.get('/credit/complaints/admin', { params }),
  confirmSettled: (tripId) => http.post(`/expenses/team/${tripId}/settle-confirm`)
}

/* ================= 个人出行画像（标签、记账、足迹、结伴记录聚合） ================= */
export const portraitApi = {
  me: () => http.get('/portrait/me'),
  of: (userId) => http.get(`/portrait/users/${userId}`)
}

/* ================= 天气与动态行程调整 ================= */
export const weatherApi = {
  forecast: (city, days = 3) => http.get('/weather', { params: { city, days } })
}

export const planApi = {
  adjust: (data) => http.post('/plans/adjust', data),
  mine: () => http.get('/plans/mine')
}

/* ================= 大模型（AI行程助手 · 一句话解析+个性化AI搭子） ================= */
// 带silent：大模型「未配置/超时/失败」属于预期内的降级，不该弹红字提示。
// chat单独给120s超时：本地Ollama首次对话要把模型加载进内存，比较慢
export const aiApi = {
  /** 后端是否已接入大模型（前端据此显示「大模型/本地规则」标识） */
  status: () => http.get('/ai/status', { silent: true }),
  /**
   * 把一句话解析成结构化行程条件
   * @returns {Object} 解析出的行程条件；source为llm或rule
   */
  parseIntent: (text) => http.post('/ai/parse-intent', { text }, { silent: true }),
  /**
   * V2智能规划：后端完成画像选点、2-opt串线与拟人解释。
   * @param round 换一条路的次数（0=首次，>0时后端换一批备选景点）
   * @returns {Object} 与/routes/plan同构的行程，另含aiPlan（画像打分、解释文案、引擎信息）
   */
  smartPlan: (text, round = 0) =>
    http.post('/ai/smart-plan', { text, round: String(round) }, { silent: true, timeout: 120000 }),
  /** 和AI旅行搭子聊一句（画像注入+两层记忆，见后端AiChatService） */
  chat: (text) =>
    http.post('/ai/chat', { text }, { silent: true, timeout: 120000 }),
  /** 对话历史（时间正序） */
  chatHistory: () => http.get('/ai/chat/history', { silent: true }),
  /** 清空对话历史（不动AI的长期记忆） */
  clearHistory: () => http.delete('/ai/chat/history', { silent: true }),
  /** AI记得我什么：画像摘要+对话轮数 */
  memory: () => http.get('/ai/memory', { silent: true }),
  /** 彻底遗忘：清空AI的长期记忆 */
  forgetMemory: () => http.delete('/ai/memory', { silent: true })
}

/* ================= 消费与费用管理 ================= */
export const expenseApi = {
  add: (data) => http.post('/expenses', data),
  personal: (userId) => http.get('/expenses/personal', { params: userId ? { userId } : {} }),
  myTeams: () => http.get('/expenses/my-teams'),
  team: (tripId) => http.get(`/expenses/team/${tripId}`),
  remove: (id) => http.delete(`/expenses/${id}`)
}

/* ================= 数据采集与“只看不订”信息浏览 ================= */
export const scrapedApi = {
  list: (params) => http.get('/scraped', { params }),
  cities: () => http.get('/scraped/cities'),
  origins: () => http.get('/scraped/origins'),
  costEstimate: (params) => http.get('/scraped/cost-estimate', { params }),
  refresh: () => http.post('/scraped/refresh'),
  crawl: () => http.post('/scraped/crawl'),
  // 定向采集：只抓用户选定的「出发地 → 目的地」这条线路（车次）
  crawlRoute: (origin, destination) =>
    http.post('/scraped/crawl-route', null, { params: { origin, destination } }),
  crawlBatches: () => http.get('/scraped/crawl/batches')
}

/* ================= 热度中心（热搜/词云/博主评级） ================= */
export const hotApi = {
  posts: (limit = 10) => http.get('/hot/posts', { params: { limit } }),
  keywords: (limit = 40) => http.get('/hot/keywords', { params: { limit } }),
  cities: (limit = 12) => http.get('/hot/cities', { params: { limit } }),
  bloggers: (limit = 20) => http.get('/hot/bloggers', { params: { limit } })
}

/* ================= 安全机制：家校知情与位置共享 ================= */
export const safetyApi = {
  /** 队长：出行前一键通知全队成员的家长与辅导员 */
  notifyDeparture: (tripId) => http.post('/safety/notify', { tripId }),
  /** 某行程的家校知情通知记录 */
  notices: (tripId) => http.get('/safety/notices', { params: { tripId } }),
  /** 我收到的家校知情通知 */
  myNotices: () => http.get('/safety/notices/mine'),
  /** 创建/续期位置共享授权 */
  createShare: (data) => http.post('/safety/shares', data),
  /** 我的位置共享授权列表 */
  shares: (tripId) => http.get('/safety/shares', { params: tripId ? { tripId } : {} }),
  /** 撤销授权 */
  revokeShare: (id) => http.post(`/safety/shares/${id}/revoke`),
  /** 家长查看页数据（公开，无需登录） */
  track: (token) => http.get(`/safety/track/${token}`)
}

/* ================= 数据看板 ================= */
export const statsApi = {
  overview: () => http.get('/stats/overview'),
  spotHeat: () => http.get('/stats/spot-heat'),
  circleActivity: () => http.get('/stats/circle-activity'),
  postTrend: () => http.get('/stats/post-trend'),
  // 首页热词/热搜属C端前台内容，商家与管理员无权限（后端403）；
  // 页面上也不对这两类角色展示，故403静默，避免进首页弹红字
  hotWords: () => http.get('/stats/hot-words', { silentForbidden: true }),
  hotSearch: () => http.get('/stats/hot-search', { silentForbidden: true }),
  cityActivity: () => http.get('/stats/city-activity'),
  spotVisits: () => http.get('/stats/spot-visits'),
  routeRanking: () => http.get('/stats/route-ranking')
}

export default {
  authApi,
  userApi,
  tagApi,
  matchApi,
  insightApi,
  postApi,
  circleApi,
  tripApi,
  spotApi,
  routeApi,
  footprintApi,
  transportApi,
  emergencyApi,
  insuranceApi,
  messageApi,
  notificationApi,
  merchantApi,
  productApi,
  orderApi,
  guideApi,
  adApi,
  reportApi,
  creditApi,
  portraitApi,
  productReviewApi,
  settlementApi,
  demandApi,
  creatorApi,
  scrapedApi,
  weatherApi,
  planApi,
  aiApi,
  geoApi,
  expenseApi,
  hotApi,
  safetyApi,
  statsApi
}
