// 公共格式化工具

/**
 * 时间格式化：ISO字符串 -> yyyy-MM-dd HH:mm
 * 把ISO的T替换为空格并截取到分钟
 */
export function formatTime(value) {
  if (!value) return ''
  if (typeof value === 'number') {
    value = new Date(value).toISOString()
  }
  const str = String(value)
  if (/^\d{4}-\d{2}-\d{2}T/.test(str) || str.includes('T')) {
    return str.replace('T', ' ').slice(0, 16)
  }
  return str.slice(0, 16)
}

/** 仅日期yyyy-MM-dd */
export function formatDate(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 10)
}

/** 分钟数转可读时长：90 -> 1小时30分钟 */
export function minutesToText(min) {
  const n = Number(min)
  if (!n && n !== 0) return '—'
  if (n < 60) return `${n}分钟`
  // 超过一天按“天+小时”显示（如骑行4320分钟 → 约3天）
  if (n >= 1440) {
    const d = Math.floor(n / 1440)
    const h = Math.floor((n % 1440) / 60)
    return `约${d}天${h ? h +'小时' : ''}`
  }
  const h = Math.floor(n / 60)
  const m = n % 60
  return m ? `${h}小时${m}分钟` : `${h}小时`
}

/** 距离（km，后端返回数值公里） */
export function kmText(km) {
  const n = Number(km)
  if (Number.isNaN(n)) return '—'
  return n >= 1 ? `${n.toFixed(1)} km` : `${Math.round(n * 1000)} m`
}

/** 金额 */
export function money(v) {
  const n = Number(v)
  if (Number.isNaN(n)) return '¥0'
  return `¥${n.toFixed(2).replace(/\.00$/, '')}`
}

/* ========== 枚举文案映射 ========== */
export const SCHOOL_MAP = {
  CSU: '中南大学',
  HUNNU: '湖南师范大学',
  HNU: '湖南大学'
}

export const ROLE_MAP = {
  GUEST: '游客',
  USER: '普通用户',
  BLOGGER: '攻略博主',
  MERCHANT: '商家',
  GUIDE: '导游',
  ADMIN: '管理员'
}

export const POST_TYPE_MAP = {
  GUIDE: '攻略',
  MOMENT: '动态',
  QUESTION: '问答',
  LOCAL: '本地'
}

export const POST_TYPE_TAG_TYPE = {
  GUIDE: 'primary',
  MOMENT: 'success',
  QUESTION: 'warning',
  LOCAL: 'danger'
}

export const TRIP_STATUS_MAP = {
  OPEN: '招募中',
  FULL: '已满员',
  PLANNING: '招募中',
  ONGOING: '进行中',
  FINISHED: '已结束',
  CANCELLED: '已取消'
}

export const TRIP_STATUS_TAG = {
  OPEN: 'success',
  FULL: 'warning',
  PLANNING: 'success',
  ONGOING: 'primary',
  FINISHED: 'info',
  CANCELLED: 'danger'
}

/** 游玩节奏（结伴偏好，与后端TripMatchService.PACE_* 对齐） */
export const PACE_MAP = {
  ENERGETIC: '精力满满（特种兵式）',
  BALANCED: '节奏适中',
  RELAXED: '纯休闲放松'
}

export const PACE_SHORT_MAP = {
  ENERGETIC: '精力满满',
  BALANCED: '适中',
  RELAXED: '休闲放松'
}

export const PACE_OPTIONS = [
  { value: 'ENERGETIC', label: '精力满满（特种兵式）', desc: '起早贪黑、一天打卡多个点' },
  { value: 'BALANCED', label: '节奏适中', desc: '半天景点半天休息，劳逸结合' },
  { value: 'RELAXED', label: '纯休闲放松', desc: '睡到自然醒，重在体验' }
]

/** 结伴兴趣标签（发布队伍与个人偏好共用） */
export const TRIP_INTEREST_TAGS = [
  '徒步',
  '登山',
  '露营',
  '摄影',
  '美食',
  'Citywalk',
  '骑行',
  '自驾',
  '速通打卡',
  '博物馆',
  '古镇',
  '温泉'
]

export const SCHOOL_SCOPE_MAP = {
  ALL: '三校跨校',
  SAME: '仅本校'
}

/* ===== 家校知情与位置共享 ===== */

/** 紧急联系人关系 */
export const RELATION_MAP = {
  PARENT: '家长',
  COUNSELOR: '辅导员'
}

export const RELATION_TAG = {
  PARENT: 'primary',
  COUNSELOR: 'warning'
}

/** 家校知情通知类型 */
export const NOTICE_TYPE_MAP = {
  TRIP_REPORT: '行程报备',
  DEPART_REMIND: '出行提醒'
}

/** 位置共享授权状态 */
export const SHARE_STATUS_MAP = {
  ACTIVE: '生效中',
  REVOKED: '已撤销',
  EXPIRED: '已过期'
}

export const SHARE_STATUS_TAG = {
  ACTIVE: 'success',
  REVOKED: 'info',
  EXPIRED: 'warning'
}

/** 生成家长查看链接（后端返回的是/travelling/track/xxx相对路径） */
export function trackUrl(path) {
  if (!path) return ''
  const origin = window.location.origin
  const p = path.startsWith('/') ? path : `/${path}`
  return `${origin}${p}`
}

/** 逗号串 -> 标签数组，兼容中英文逗号、顿号与空格 */
export function splitTags(csv) {
  if (!csv) return []
  if (Array.isArray(csv)) return csv.filter(Boolean)
  return String(csv)
    .split(/[,，、/\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 按匹配度给徽章配色：>=80优、>=60良、其余一般 */
export function matchTagType(score) {
  const n = Number(score)
  if (Number.isNaN(n)) return 'info'
  if (n >= 80) return 'success'
  if (n >= 60) return 'warning'
  return 'info'
}

export const MEMBER_STATUS_MAP = {
  PENDING: '待审核',
  APPROVED: '已加入',
  REJECTED: '已拒绝',
  LEFT: '已退出'
}

export const MEMBER_STATUS_TAG = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  LEFT: 'info'
}

export const RISK_MAP = {
  LOW: '低风险',
  MEDIUM: '中风险',
  MIDDLE: '中风险',
  HIGH: '高风险'
}

export const RISK_TAG = {
  LOW: 'success',
  MEDIUM: 'warning',
  MIDDLE: 'warning',
  HIGH: 'danger'
}

/* ===== 结伴信誉投诉：同行人投诉 → 申诉 → 终审 ===== */

/** 投诉类型（文案与后端CreditService.complaintTypeName完全一致，勿单独改） */
export const COMPLAINT_TYPE_MAP = {
  NO_SHOW: '爽约',
  LOST_CONTACT: '行程中失联',
  MISBEHAVE: '言行不当',
  EXPENSE_DISPUTE: '费用不清'
}

export const COMPLAINT_TYPE_TAG = {
  NO_SHOW: 'danger',
  LOST_CONTACT: 'warning',
  MISBEHAVE: 'warning',
  EXPENSE_DISPUTE: 'info'
}

/** 各类型核实属实后的扣分档位（仅用于前端提示，真实扣分以后端为准） */
export const COMPLAINT_TYPE_PENALTY = {
  NO_SHOW: 15,
  LOST_CONTACT: 10,
  MISBEHAVE: 10,
  EXPENSE_DISPUTE: 10
}

export const COMPLAINT_STATUS_MAP = {
  PENDING: '待处理',
  RESOLVED: '已核实',
  REJECTED: '不属实',
  APPEALED: '申诉中',
  FINAL: '已终审'
}

export const COMPLAINT_STATUS_TAG = {
  PENDING: 'warning',
  RESOLVED: 'danger',
  REJECTED: 'info',
  APPEALED: 'primary',
  FINAL: 'success'
}

/**
 * 信誉分等级配色（与后端CreditService.levelOf的五档对齐）
 * 注意：国内习惯「高分=红/暖色」不适用于信誉分——这里是「越低越危险」，
 * 因此高危用红、优秀用绿，与股票涨跌配色无关。
 */
export function creditTagType(score) {
  const n = Number(score)
  if (Number.isNaN(n)) return 'info'
  if (n >= 110) return 'success'
  if (n >= 90) return 'primary'
  if (n >= 70) return 'warning'
  return 'danger'
}

export const TRANSPORT_MODE_MAP = {
  TRAIN: '高铁/火车',
  FLIGHT: '飞机',
  BUS: '长途大巴',
  CAR: '自驾/租车',
  CARPOOL: '拼车/顺风车',
  BIKE: '骑行/徒步'
}

export const SPOT_CATEGORY_MAP = {
  NATURE: '自然景点',
  HISTORY: '历史人文',
  TRENDY: '网红打卡',
  FOOD: '美食地标',
  SECRET: '小众秘境',
  THEME: '主题乐园',
  CUSTOM: '我的地点'
}

export const ROUTE_MODE_MAP = {
  OPTIMAL: '最优路线',
  SPEED: '特种兵速通',
  LEISURE: '休闲慢游',
  FOOD: '美食专线',
  SCENIC: '景点专线',
  WILD: '刺激野路',
  CITYWALK: '城市漫步'
}

export const ROUTE_MODE_COLOR = {
  OPTIMAL: '#2F80ED',
  SPEED: '#F5222D',
  LEISURE: '#52C41A',
  FOOD: '#FA8C16',
  SCENIC: '#722ED1',
  WILD: '#13C2C2',
  CITYWALK: '#EB2F96'
}

/**
 * 多日行程每日配色，顺序必须与后端RoutePlannerService.DAY_COLORS一致
 * （后端按天返回color，前端按同一下标生成地图折线样式）
 */
export const DAY_COLORS = [
  '#2F80ED',
  '#F5222D',
  '#52C41A',
  '#722ED1',
  '#FA8C16',
  '#13C2C2',
  '#EB2F96'
]

export const MAX_TRIP_DAYS = DAY_COLORS.length

/**
 * 费用估算预算档位（与后端ScrapedInfoService.BUDGET_* 对齐）
 * 原先「AI行程」与「信息浏览」两个页面各写了一遍，合并后只保留这一份。
 */
export const BUDGETS = [
  { label: '不限预算', value: 'NONE' },
  { label: '1000元以内', value: 'LE1000' },
  { label: '1500元以内', value: 'LE1500' },
  { label: '2000元以内', value: 'LE2000' },
  { label: '3000元以内', value: 'LE3000' }
]

export const ORDER_STATUS_MAP = {
  PENDING: '待付款',
  PAID: '待发货',
  SHIPPED: '待收货',
  DONE: '已完成',
  CANCELLED: '已取消'
}

export const ORDER_STATUS_TAG = {
  PENDING: 'info',
  PAID: 'warning',
  SHIPPED: 'primary',
  DONE: 'success',
  CANCELLED: 'danger'
}

/** 广告位（商家投放中心/后台广告审核） */
export const AD_SLOTS = [
  { label: '首页开屏 BANNER', value: 'BANNER' },
  { label: '信息流 FEED', value: 'FEED' },
  { label: '侧边栏 SIDEBAR', value: 'SIDEBAR' },
  { label: '启动页 SPLASH', value: 'SPLASH' },
  { label: '详情页 DETAIL', value: 'DETAIL' }
]

export const AD_SLOT_MAP = {
  BANNER: '首页 Banner',
  FEED: '信息流',
  SIDEBAR: '侧边栏',
  SPLASH: '启动页',
  DETAIL: '详情页'
}

export const AD_STATUS_MAP = {
  PENDING: '待审核',
  APPROVED: '投放中',
  REJECTED: '已驳回'
}

export const AD_STATUS_TAG = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger'
}

export const BOOKING_STATUS_MAP = {
  PENDING: '待确认',
  PAID: '已支付·待确认',
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
  SETTLED: '已结算',
  REVIEWED: '已评价',
  CANCELLED: '已取消'
}

export const BOOKING_STATUS_TAG = {
  PENDING: 'warning',
  PAID: 'warning',
  CONFIRMED: 'primary',
  COMPLETED: 'success',
  SETTLED: 'success',
  REVIEWED: 'success',
  CANCELLED: 'danger'
}

export const AUDIT_STATUS_MAP = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}

export const AUDIT_TAG = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger'
}

export const PRODUCT_CATEGORIES = [
  '户外装备',
  '背包箱包',
  '帐篷露营',
  '摄影器材',
  '服饰鞋靴',
  '零食饮品',
  '旅行日用'
]

/** 将逗号分隔的图片字符串拆为数组 */
export function splitImages(images) {
  if (!images) return []
  if (Array.isArray(images)) return images.filter(Boolean)
  return String(images)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}
