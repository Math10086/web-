import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { BUSINESS_ROLES, ROLES, homeFor, roleOf } from '@/utils/permission'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('xlvq_token') || '')
  const user = ref(
    JSON.parse(localStorage.getItem('xlvq_user') || 'null')
  )

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isVerified = computed(() => !!user.value?.verified)

  /** 当前身份（未登录=GUEST） */
  const role = computed(() => roleOf({ isLoggedIn: isLoggedIn.value, user: user.value }))
  const isGuest = computed(() => role.value === ROLES.GUEST)
  const isMerchant = computed(() => role.value === ROLES.MERCHANT)
  const isGuide = computed(() => role.value === ROLES.GUIDE)
  const isBlogger = computed(() => role.value === ROLES.BLOGGER)
  /** C端业务角色：普通用户/博主/导游（可结伴、规划路线、记录足迹、查看消费报表） */
  const isBusiness = computed(() => BUSINESS_ROLES.includes(role.value))
  /** 当前身份的落地页（管理员进后台） */
  const homePath = computed(() => homeFor(role.value))

  function hasRole(role) {
    if (!role) return true
    return user.value?.role === role
  }

  function setSession(data) {
    token.value = data.token
    user.value = data.user
    localStorage.setItem('xlvq_token', data.token)
    localStorage.setItem('xlvq_user', JSON.stringify(data.user))
  }

  function setUser(u) {
    user.value = u
    localStorage.setItem('xlvq_user', JSON.stringify(u))
  }

  async function login(username, password) {
    const data = await authApi.login({ username, password })
    setSession(data)
    return data
  }

  async function register(payload) {
    const data = await authApi.register(payload)
    setSession(data)
    return data
  }

  async function fetchMe() {
    if (!token.value) return null
    const u = await authApi.me()
    setUser(u)
    return u
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('xlvq_token')
    localStorage.removeItem('xlvq_user')
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    isVerified,
    role,
    isGuest,
    isMerchant,
    isGuide,
    isBlogger,
    isBusiness,
    homePath,
    hasRole,
    setSession,
    setUser,
    login,
    register,
    fetchMe,
    logout
  }
})
