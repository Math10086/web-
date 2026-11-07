<template>
  <el-container class="layout-root">
    <el-header class="x-header" height="62px">
      <div class="header-inner">
        <div class="x-logo" @click="router.push('/')">
          <span>🏔️</span>
          <span>学旅圈</span>
        </div>

        <nav class="x-nav">
          <!-- 菜单按当前身份渲染：管理员只保留「管理后台」，
               结伴出行 / 地图路线 / 足迹 / 消费报表等属游客与学生用户（含博主 / 导游）的能力 -->
          <router-link v-for="m in navItems" :key="m.path" :to="m.path">
            {{ m.label }}
          </router-link>
        </nav>

        <div class="header-tools">
          <!-- 管理员只负责后台；商家已退出社区，攻略/动态搜索一并隐藏 -->
          <el-input
            v-if="!auth.isAdmin && !auth.isMerchant"
            v-model="keyword"
            class="header-search"
            placeholder="搜索攻略/动态"
            :prefix-icon="Search"
            clearable
            @keyup.enter="doSearch"
            @clear="doSearch"
          />

          <el-popover
            v-if="!auth.isAdmin"
            placement="bottom-end"
            :width="360"
            trigger="click"
            popper-class="notify-popover"
            @show="onOpenNotify"
          >
            <template #reference>
              <el-badge
                :value="unreadCount"
                :hidden="unreadCount === 0"
                :max="99"
                class="bell-badge"
              >
                <el-button circle :icon="Bell" />
              </el-badge>
            </template>
            <div style="max-height: 420px; overflow-y: auto">
              <div class="flex-between mb-12">
                <strong>消息通知</strong>
                <el-button link type="primary" size="small" @click="markRead">
                  全部已读
                </el-button>
              </div>
              <el-empty
                v-if="!notifications.length"
                description="暂无通知"
                :image-size="70"
              />
              <div
                v-for="n in notifications"
                :key="n.id"
                class="notify-item"
                :class="{ unread: !isRead(n) }"
              >
                <div class="nt-title">
                  {{ n.title || '系统通知' }}
                  <el-tag v-if="n.type" size="small" style="margin-left: 6px">
                    {{ n.type }}
                  </el-tag>
                </div>
                <div class="nt-content">{{ n.content }}</div>
                <div class="nt-time">{{ formatTime(n.createdAt) }}</div>
              </div>
              <div style="text-align: center; margin-top: 8px">
                <el-button link type="primary" size="small" @click="router.push('/messages')">
                  查看全部消息
                </el-button>
              </div>
            </div>
          </el-popover>

          <template v-if="auth.isLoggedIn">
            <el-dropdown trigger="click" @command="onUserCommand">
              <span class="flex-center gap-8" style="cursor: pointer">
                <el-avatar :size="32" :src="auth.user?.avatar">
                  {{ auth.user?.nickname?.charAt(0) || '游' }}
                </el-avatar>
                <span style="font-size: 13px">{{ auth.user?.nickname }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <!-- 管理员与 C 端功能完全隔离，下拉只保留后台入口 -->
                  <template v-if="auth.isAdmin">
                    <el-dropdown-item command="admin">管理后台</el-dropdown-item>
                  </template>
                  <template v-else>
                    <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                    <el-dropdown-item command="messages">消息</el-dropdown-item>
                    <el-dropdown-item v-if="auth.isBusiness" command="myTrips">我的结伴</el-dropdown-item>
                    <el-dropdown-item v-if="auth.isBusiness" command="safety">安全中心</el-dropdown-item>
                    <el-dropdown-item command="orders">
                      {{ auth.isMerchant ? '店铺订单' : '我的订单' }}
                    </el-dropdown-item>
                    <!-- 接单是导游账号专属能力，其他角色不出现该入口 -->
                    <el-dropdown-item v-if="auth.isGuide" command="guideBookings">
                      导游接单
                    </el-dropdown-item>
                    <el-dropdown-item v-if="auth.isMerchant" command="merchant">
                      商家中心
                    </el-dropdown-item>
                  </template>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else type="primary" round @click="router.push('/login')">
            登录 / 注册
          </el-button>
        </div>
      </div>
    </el-header>

    <el-main style="padding: 0; background: var(--xlvq-bg)">
      <router-view />
    </el-main>

    <el-footer height="auto" style="background: #fff; border-top: 1px solid var(--xlvq-border)">
      <div style="text-align: center; padding: 18px 12px; color: var(--xlvq-text-sub); font-size: 12px; line-height: 2">
        <div>🏔️ 学旅圈 · 高校学生旅游社区与结伴出行平台</div>
        <div>中南大学 · 湖南师范大学 · 湖南大学 三校学生专属 | 同城结伴 · 家校知情 · 信誉护航 · 安全出行</div>
      </div>
    </el-footer>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Bell, ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { notificationApi } from '@/api'
import { ROLE_MAP, formatTime } from '@/utils/format'
import { navFor } from '@/utils/permission'

const router = useRouter()
const auth = useAuthStore()

/** 按当前登录身份裁剪顶部菜单 */
const navItems = computed(() => navFor(auth.role))

const keyword = ref(router.currentRoute.value.query.keyword || '')
const notifications = ref([])
let timer = null

const unreadCount = ref(0)

function isRead(n) {
  // 后端实体字段是readFlag，Jackson默认序列化后也是readFlag
  return n.read === true || n.isRead === true || n.readFlag === true
}

async function loadNotifications() {
  if (!auth.isLoggedIn) return
  try {
    const data = await notificationApi.list()
    notifications.value = Array.isArray(data) ? data : data?.list || []
    unreadCount.value = notifications.value.filter((n) => !isRead(n)).length
  } catch (e) {
    // 静默处理轮询失败
  }
}

async function onOpenNotify() {
  await loadNotifications()
  if (unreadCount.value > 0) {
    await markRead()
  }
}

async function markRead() {
  try {
    await notificationApi.read()
    notifications.value.forEach((n) => {
      n.readFlag = true
      n.read = true
    })
    unreadCount.value = 0
  } catch (e) {
    // ignore
  }
}

function doSearch() {
  router.push({ path: '/posts', query: keyword.value ? { keyword: keyword.value } : {} })
}

async function onUserCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'messages') router.push('/messages')
  else if (cmd === 'myTrips') router.push('/trips/my')
  else if (cmd === 'safety') router.push('/safety')
  else if (cmd === 'orders') router.push('/orders')
  else if (cmd === 'guideBookings') router.push({ path: '/guide-bookings', query: { tab: 'guide' } })
  else if (cmd === 'merchant') router.push('/merchant')
  else if (cmd === 'admin') router.push('/admin')
  else if (cmd === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    auth.logout()
    ElMessage.success('已退出登录')
    router.push('/')
  }
}

onMounted(async () => {
  if (auth.isLoggedIn) {
    auth.fetchMe().catch(() => {})
  }
  if (auth.isLoggedIn && !auth.isAdmin) {
    loadNotifications()
    timer = setInterval(loadNotifications, 5000)
  }
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.layout-root {
  min-height: 100vh;
}
:deep(.el-main) {
  flex: 1;
}
</style>
