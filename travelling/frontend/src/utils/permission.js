/**
 * 角色与功能权限模型
 *
 *   GUEST未登录游客，只能浏览公开内容（检索/地图/信息浏览的公开部分）
 *   USER普通用户（学生），C端前台全部能力
 *   BLOGGER攻略博主，普通用户能力+内容经营，可申请导游认证
 *   GUIDE导游，博主能力+发布服务与接单
 *   MERCHANT商家/赞助商，只做商业合作（开店、投放、交易、评价），不进前台社区
 *   ADMIN管理员，只负责管理后台（审核、举报、用户治理、结算、数据看板）
 */

export const ROLES = {
  GUEST: 'GUEST',
  USER: 'USER',
  BLOGGER: 'BLOGGER',
  GUIDE: 'GUIDE',
  MERCHANT: 'MERCHANT',
  ADMIN: 'ADMIN'
}

/** C端业务角色：学生用户/博主/导游（结伴、路线规划、足迹、消费报表、家校安全） */
export const BUSINESS_ROLES = [ROLES.USER, ROLES.BLOGGER, ROLES.GUIDE]

/** C端前台角色（可浏览社区、论坛、热度榜、地图、导游服务等公开内容）—— 商家已退出前台，故不含MERCHANT */
export const FRONT_ROLES = [ROLES.GUEST, ...BUSINESS_ROLES]

/** 商城相关角色（含商家：商家是商业合作方，需要进商城/店铺/订单） */
export const SHOP_ROLES = [...FRONT_ROLES, ROLES.MERCHANT]

/** 公开浏览角色（历史上等价于「不登录也能看」；保留给商城等场景） */
export const PUBLIC_ROLES = SHOP_ROLES

/** 已登录的C端角色（不含管理员，管理员只走后台） */
export const LOGIN_ROLES = [...BUSINESS_ROLES, ROLES.MERCHANT]

/** 全部角色 */
export const ALL_ROLES = [...SHOP_ROLES, ROLES.ADMIN]

/** 角色中文名 */
export const ROLE_NAME = {
  GUEST: '游客',
  USER: '普通用户',
  BLOGGER: '攻略博主',
  GUIDE: '导游',
  MERCHANT: '商家',
  ADMIN: '管理员'
}

/** 各角色一句话职能说明（用于权限说明弹窗/页头提示） */
export const ROLE_DESC = {
  GUEST: '未登录状态：仅可浏览公开攻略、圈层、论坛、地图与出行信息，参与互动需登录并完成三校认证',
  USER: 'C 端前台全量用户：社区互动、结伴出行、路线规划、足迹记录、消费报表、家校知情与位置共享',
  BLOGGER: '在普通用户能力之上，可经营攻略内容、参与博主评级与精品推荐，并可申请导游认证；认证通过后以导游身份接单',
  GUIDE: '可发布导游服务、接收向导订单并按比例分成，同时保留普通用户与博主全部能力',
  MERCHANT: '纯商业合作方：可入驻开店、上架商品、投放广告位、处理店铺订单与回复商品评价；不参与结伴出行、社区互动、路线规划等前台功能',
  ADMIN: '仅负责管理后台：内容 / 广告 / 资质审核、举报监管、用户治理、交易结算与平台数据可视化'
}

/**
 * 顶部导航菜单（含各菜单可见角色）。
 * 管理员不出现在任何C端菜单中，只保留「管理后台」；
 * 商家只保留 首页/商城/商家中心/消息/个人中心。
 */
export const MENU = [
  { path: '/', label: '首页', roles: SHOP_ROLES },
  { path: '/circles', label: '社区圈层', roles: FRONT_ROLES },
  { path: '/forum', label: '本地论坛', roles: FRONT_ROLES },
  // 「找搭子」已并入结伴出行页（/trips?tab=match），「热度榜」已并入数据洞察页（/insight?tab=hot），
  // 不再单独占导航位；旧地址/match、/hot保留重定向
  { path: '/trips', label: '结伴出行', roles: [ROLES.GUEST, ...BUSINESS_ROLES] },
  { path: '/safety', label: '安全中心', roles: BUSINESS_ROLES },
  { path: '/map', label: '地图路线', roles: FRONT_ROLES },
  // AI旅行搭子：个性化聊天（记忆按用户隔离，游客引导先登录）
  { path: '/ai-chat', label: 'AI 搭子', roles: FRONT_ROLES },
  // 匿名聚合洞察：公开脱敏看板，游客（未登录）也能看，含热度榜标签
  { path: '/insight', label: '数据洞察', roles: [...FRONT_ROLES, ROLES.MERCHANT] },
  { path: '/footprint', label: '足迹', roles: BUSINESS_ROLES },
  { path: '/expenses', label: '消费报表', roles: BUSINESS_ROLES },
  // 创作者中心已并入「数据洞察」页的标签（/insight?tab=creator），不再单独占导航位；
  // 旧地址/creator保留重定向
  { path: '/mall', label: '商城', roles: SHOP_ROLES },
  // 「导游接单」已并入找导游页的第二个标签（仅登录用户可见该标签；导游视角页面内
  // 与后端接口双重限定GUIDE），旧/guide-bookings路由只做重定向
  { path: '/guides', label: '找导游', roles: FRONT_ROLES },
  { path: '/merchant', label: '商家中心', roles: [ROLES.MERCHANT] },
  // 商业闭环 · 需求洞察已并入「数据洞察」页的第三个标签（仅商家/导游可见该标签）
  { path: '/messages', label: '消息', roles: LOGIN_ROLES },
  { path: '/admin', label: '管理后台', roles: [ROLES.ADMIN] }
]
// 注：「消息」对已登录的C端角色（普通用户/博主/导游/商家）显示为顶部导航项，
// 同时在右上角头像下拉里也保留一份入口；管理员与消息完全隔离，不显示该项。
// 「个人中心」不进顶部导航（导航条放不下），走右上角头像下拉。

/**
 * 路由访问权限表（守卫使用，键为路由完整路径）。
 * 未列出的路由（登录页/家长查看页）默认放行。
 */
export const ROUTE_ROLES = {
  // 首页/商城：商家也能进（其导航保留这两项）
  '/': SHOP_ROLES,
  '/mall': SHOP_ROLES,
  // 社区类内容：前台角色专属，商家已退出前台
  '/circles': FRONT_ROLES,
  '/circles/:id': FRONT_ROLES,
  '/forum': FRONT_ROLES,
  '/posts': FRONT_ROLES,
  '/posts/new': BUSINESS_ROLES,
  '/posts/:id': FRONT_ROLES,
  '/posts/:id/edit': BUSINESS_ROLES,
  // 结伴/安全/足迹/消费报表/路线规划
  '/trips': [ROLES.GUEST, ...BUSINESS_ROLES],
  '/trips/new': BUSINESS_ROLES,
  '/trips/my': BUSINESS_ROLES,
  // 队长团队管理页：属结伴业务能力（商家与管理员不参与）
  '/trips/:id/team': BUSINESS_ROLES,
  '/trips/:id': [ROLES.GUEST, ...BUSINESS_ROLES],
  // 半熟人撮合与契合度推荐（找搭子）：登录且为学生/博主/导游
  // 找搭子已并入/trips（?tab=match），权限随结伴出行页；旧/match路由只做重定向
  '/safety': BUSINESS_ROLES,
  '/map': FRONT_ROLES,
  // AI旅行搭子：游客可进页面看介绍，聊天时引导登录
  '/ai-chat': FRONT_ROLES,
  '/expenses': BUSINESS_ROLES,
  '/footprint': BUSINESS_ROLES,
  // 个人出行画像（个人中心、足迹、消费报表的聚合视图）
  '/portrait': BUSINESS_ROLES,
  // 创作者中心已并入数据洞察页的标签（/insight?tab=creator），仅在页面内按角色控制可见性；
  // 路由层不再单独设权限，旧/creator直接重定向到/insight
  // 匿名聚合洞察看板：公开脱敏内容，游客可访问，含热度榜与需求洞察标签；
  // 旧/hot、/demand路由只做重定向。需求洞察标签仅商家/导游可见
  '/insight': [...FRONT_ROLES, ROLES.MERCHANT],
  // 订单中心：买家与商家共用（买家看我的订单，商家看店铺订单）
  '/orders': LOGIN_ROLES,
  // 商家中心：纯商业合作方
  '/merchant': [ROLES.MERCHANT],
  // 需求洞察已并入数据洞察页（/insight?tab=demand），旧路由只做重定向
  '/guides': FRONT_ROLES,
  '/messages': LOGIN_ROLES,
  // 个人中心对游客开放（可查看他人主页 ?id=），但菜单只对登录用户展示
  '/profile': [ROLES.GUEST, ...LOGIN_ROLES],
  // 数据看板只作为管理后台内的一个Tab，仅管理员可直达
  '/stats': [ROLES.ADMIN],
  '/admin': [ROLES.ADMIN]
}

/** 取当前身份（未登录一律按游客处理） */
export function roleOf(auth) {
  if (!auth || !auth.isLoggedIn) return ROLES.GUEST
  return auth.user?.role || ROLES.USER
}

/** 判断某身份是否可访问指定角色列表 */
export function canAccess(roles, role) {
  if (!roles || !roles.length) return true
  return roles.includes(role)
}

/** 当前身份可用的顶部菜单 */
export function navFor(role) {
  return MENU
    .filter((m) => canAccess(m.roles, role))
    .map((m) => {
      // 商家在洞察页只看到「主题选品」这一个业务标签（供需缺口/出行预算对商家隐藏），
      // 导航叫「数据洞察」名不副实，按角色改称「选品分析」
      if (m.path === '/insight' && role === ROLES.MERCHANT) {
        return { ...m, label: '选品分析' }
      }
      return m
    })
}

/** 登录/越权后的落地页：管理员进后台，商家进商家中心，其余进首页 */
export function homeFor(role) {
  if (role === ROLES.ADMIN) return '/admin'
  if (role === ROLES.MERCHANT) return '/merchant'
  return '/'
}
