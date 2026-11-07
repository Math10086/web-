import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { ROLES, ROLE_NAME, ROUTE_ROLES, canAccess, homeFor, roleOf } from '@/utils/permission'

const MainLayout = () => import('@/layouts/MainLayout.vue')

/** 路由meta：roles即该页面允许访问的身份（含GUEST表示无需登录） */
const withRoles = (path) => ({ roles: ROUTE_ROLES[path] })

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { plain: true }
  },
  {
    // 家长/辅导员查看页：凭学生分享的授权链接访问，无需登录，独立于主布局
    path: '/track/:token',
    name: 'track-view',
    component: () => import('@/views/safety/TrackView.vue')
  },
  {
    path: '/',
    component: MainLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        meta: withRoles('/')
      },
      {
        path: 'circles',
        name: 'circles',
        component: () => import('@/views/circles/Circles.vue'),
        meta: withRoles('/circles')
      },
      {
        path: 'circles/:id',
        name: 'circle-detail',
        component: () => import('@/views/circles/CircleDetail.vue'),
        meta: withRoles('/circles/:id')
      },
      {
        path: 'forum',
        name: 'local-forum',
        component: () => import('@/views/posts/LocalForum.vue'),
        meta: withRoles('/forum')
      },
      {
        path: 'posts',
        name: 'post-list',
        component: () => import('@/views/posts/PostList.vue'),
        meta: withRoles('/posts')
      },
      {
        path: 'posts/new',
        name: 'post-create',
        component: () => import('@/views/posts/PostEdit.vue'),
        meta: withRoles('/posts/new')
      },
      {
        path: 'posts/:id',
        name: 'post-detail',
        component: () => import('@/views/posts/PostDetail.vue'),
        meta: withRoles('/posts/:id')
      },
      {
        path: 'posts/:id/edit',
        name: 'post-edit',
        component: () => import('@/views/posts/PostEdit.vue'),
        meta: withRoles('/posts/:id/edit')
      },
      {
        path: 'trips',
        name: 'trip-list',
        component: () => import('@/views/trips/TripList.vue'),
        meta: withRoles('/trips')
      },
      {
        path: 'trips/new',
        name: 'trip-create',
        component: () => import('@/views/trips/TripEdit.vue'),
        meta: withRoles('/trips/new')
      },
      {
        path: 'trips/my',
        name: 'my-trips',
        component: () => import('@/views/trips/MyTrips.vue'),
        meta: withRoles('/trips/my')
      },
      {
        // 队长团队管理页：一屏看全队（队员+足迹+消费），队长与队员可进
        path: 'trips/:id/team',
        name: 'trip-team',
        component: () => import('@/views/trips/TeamManage.vue'),
        meta: withRoles('/trips/:id/team')
      },
      {
        path: 'trips/:id',
        name: 'trip-detail',
        component: () => import('@/views/trips/TripDetail.vue'),
        meta: withRoles('/trips/:id')
      },
      {
        path: 'safety',
        name: 'safety-center',
        component: () => import('@/views/safety/SafetyCenter.vue'),
        meta: withRoles('/safety')
      },
      {
        // 原「找搭子」已并入/trips（找搭子标签）；旧地址保留，自动跳转
        path: 'match',
        name: 'people-match-legacy',
        redirect: { path: '/trips', query: { tab: 'match' } }
      },
      {
        // 「地图路线」是规划/行程/信息一体化的统一入口
        path: 'map',
        name: 'map',
        component: () => import('@/views/map/MapView.vue'),
        meta: withRoles('/map')
      },
      {
        // 原「AI行程」已合并进/map（辅助决策标签）；旧地址保留，自动跳转
        path: 'ai-plan',
        name: 'ai-plan-legacy',
        redirect: { path: '/map', query: { tab: 'assist' } }
      },
      {
        // AI旅行搭子：个性化聊天（画像注入+会话记忆，本地Ollama优先）
        path: 'ai-chat',
        name: 'ai-chat',
        component: () => import('@/views/ai/AiChat.vue'),
        meta: withRoles('/ai-chat')
      },
      {
        path: 'expenses',
        name: 'expense-report',
        component: () => import('@/views/expense/ExpenseReport.vue'),
        meta: withRoles('/expenses')
      },
      {
        // 数据看板已归入管理后台（仅管理员可直达，顶部导航不再暴露）
        path: 'stats',
        name: 'stats-board',
        component: () => import('@/views/stats/StatsBoard.vue'),
        meta: withRoles('/stats')
      },
      {
        // 原「热度榜」已并入/insight（热度榜标签）；旧地址保留，自动跳转
        path: 'hot',
        name: 'hot-center-legacy',
        redirect: { path: '/insight', query: { tab: 'hot' } }
      },
      {
        // 匿名聚合洞察看板：对外公开，游客可见，数据脱敏聚合
        path: 'insight',
        name: 'insight-board',
        component: () => import('@/views/insight/InsightBoard.vue'),
        meta: withRoles('/insight')
      },
      {
        // 原「信息浏览」已合并进/map（出行信息标签）；旧地址保留，自动跳转
        path: 'info',
        name: 'info-browse-legacy',
        redirect: { path: '/map', query: { tab: 'transport' } }
      },
      {
        path: 'footprint',
        name: 'footprint',
        component: () => import('@/views/footprint/Footprint.vue'),
        meta: withRoles('/footprint')
      },
      {
        path: 'mall',
        name: 'mall',
        component: () => import('@/views/mall/Mall.vue'),
        meta: withRoles('/mall')
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/mall/Orders.vue'),
        meta: withRoles('/orders')
      },
      {
        path: 'merchant',
        name: 'merchant-center',
        component: () => import('@/views/mall/MerchantCenter.vue'),
        meta: withRoles('/merchant')
      },
      {
        // 需求洞察已并入数据洞察页（/insight?tab=demand），旧地址保留重定向
        path: 'demand',
        redirect: { path: '/insight', query: { tab: 'demand' } }
      },
      {
        path: 'guides',
        name: 'guides',
        component: () => import('@/views/guide/GuideList.vue'),
        meta: withRoles('/guides')
      },
      {
        // 导游接单已并入找导游页（/guides?tab=bookings），旧地址保留重定向
        path: 'guide-bookings',
        redirect: (to) => ({
          path: '/guides',
          query: { tab: 'bookings', ...(to.query.tab ? { inner: to.query.tab } : {}) }
        })
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/message/Messages.vue'),
        meta: withRoles('/messages')
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/user/Profile.vue'),
        meta: withRoles('/profile')
      },
      {
        // 信誉主页：分数、等级、徽章墙、变动历史与同行人投诉申诉
        path: 'credit',
        name: 'credit-profile',
        component: () => import('@/views/user/CreditProfile.vue'),
        meta: withRoles('/credit')
      },
      {
        // 个人出行画像：标签、节奏、消费、目的地、同伴，以及数据价值洞察
        path: 'portrait',
        name: 'travel-portrait',
        component: () => import('@/views/user/TravelPortrait.vue'),
        meta: withRoles('/portrait')
      },
      {
        // 创作者中心已并入数据洞察页（/insight?tab=creator），旧地址保留重定向。
        // 进入前由页面内activeTab兜底判断角色（非写内容角色回落到公开看板）
        path: 'creator',
        redirect: { path: '/insight', query: { tab: 'creator' } }
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/admin/Admin.vue'),
        meta: withRoles('/admin')
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  // base跟随vite配置（/travelling/），保证页面挂在子路径下时路由能正确解析
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

/**
 * 统一权限守卫（按角色权限矩阵控制访问）：
 *  1. 未登录且该页面对游客不开放 → 引导登录（保留redirect）；
 *  2. 已登录但角色不匹配 → 管理员回后台、商家回商家中心、其余回首页并提示；
 *  3. 管理员只保留后台运营与治理能力，商家只保留商业合作能力（开店/投放/交易/评价）。
 */
router.beforeEach((to) => {
  const auth = useAuthStore()
  const role = roleOf(auth)
  if (canAccess(to.meta?.roles, role)) {
    return true
  }

  if (role === ROLES.GUEST) {
    ElMessage.warning('该页面需要登录后访问')
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (role === ROLES.ADMIN) {
    // 管理员不走C端前台功能（社区、结伴、地图路线、足迹、消费报表等），统一回管理后台
    ElMessage.info('管理员账号仅可访问管理后台')
    return { path: homeFor(role) }
  }

  if (role === ROLES.MERCHANT) {
    // 商家为纯商业合作方，前台使用功能（社区、结伴、地图路线、足迹等）一律不可用
    ElMessage.info('商家账号仅可进行开店、投放、交易与评价管理')
    return { path: homeFor(role) }
  }

  ElMessage.error(`当前身份（${ROLE_NAME[role] || role}）无权访问该页面`)
  return { path: homeFor(role) }
})

export default router
