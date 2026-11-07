<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <div class="logo-emoji">🏔️</div>
        <h2>学旅圈</h2>
        <div class="sub-text">高校学生旅游社区与结伴出行平台</div>
      </div>

      <el-tabs v-model="activeTab" stretch>
        <!-- 登录 -->
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            label-position="top"
            @keyup.enter="handleLogin"
          >
            <el-form-item label="账号" prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入账号"
                :prefix-icon="User"
                clearable
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-button
              type="primary"
              style="width: 100%"
              size="large"
              :loading="loading"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <!-- 注册 -->
        <el-tab-pane label="注册" name="register">
          <el-form
            ref="regFormRef"
            :model="regForm"
            :rules="regRules"
            label-position="top"
          >
            <el-form-item label="账号" prop="username">
              <el-input v-model="regForm.username" placeholder="设置登录账号" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="regForm.password"
                type="password"
                placeholder="设置登录密码（不少于6位）"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="regForm.nickname" placeholder="社区中展示的昵称" :prefix-icon="Avatar" />
            </el-form-item>
            <el-form-item label="所在学校" prop="school">
              <el-select v-model="regForm.school" placeholder="请选择学校" style="width: 100%">
                <el-option label="中南大学（CSU）" value="CSU" />
                <el-option label="湖南师范大学（HUNNU）" value="HUNNU" />
                <el-option label="湖南大学（HNU）" value="HNU" />
              </el-select>
            </el-form-item>
            <el-alert
              type="info"
              :closable="false"
              style="margin-bottom: 12px"
              title="注册后请在个人中心完成三校学籍认证（真实姓名+学号），认证后才可报名结伴、发布与交易。"
            />
            <el-button
              type="primary"
              style="width: 100%"
              size="large"
              :loading="loading"
              @click="handleRegister"
            >
              注册并登录
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="demo-accounts">
        <div class="da-title">🧪 演示账号（点击一键填充）：</div>
        <el-space wrap :size="8">
          <el-tag
            v-for="d in demos"
            :key="d.username"
            class="demo-tag"
            style="cursor: pointer"
            @click="fillDemo(d)"
          >
            {{ d.label }}：{{ d.username }}/{{ d.password }}
          </el-tag>
        </el-space>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const activeTab = ref('login')
const loading = ref(false)
const loginFormRef = ref()
const regFormRef = ref()

const demos = [
  { label: '管理员', username: 'admin', password: 'admin123' },
  { label: '小鹿（博主）', username: 'xiaolu', password: '123456' },
  { label: '大山（队长）', username: 'dashan', password: '123456' },
  { label: '商家', username: 'shang', password: '123456' },
  { label: '导游', username: 'dao', password: '123456' }
]

const loginForm = ref({ username: '', password: '' })
const regForm = ref({ username: '', password: '', nickname: '', school: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const regRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 20, message: '账号长度3-20位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码不少于6位', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  school: [{ required: true, message: '请选择学校', trigger: 'change' }]
}

function fillDemo(d) {
  activeTab.value = 'login'
  loginForm.value = { username: d.username, password: d.password }
}

function afterLogin() {
  const redirect = route.query.redirect
  ElMessage.success(`欢迎回来，${auth.user?.nickname || ''}！`)
  if (redirect && typeof redirect === 'string' && redirect.startsWith('/')) {
    router.replace(redirect)
  } else {
    // 按身份落地：管理员直接进管理后台，其余进首页
    router.replace(auth.homePath)
  }
}

async function handleLogin() {
  await loginFormRef.value.validate()
  loading.value = true
  try {
    await auth.login(loginForm.value.username, loginForm.value.password)
    afterLogin()
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  await regFormRef.value.validate()
  loading.value = true
  try {
    await auth.register({ ...regForm.value })
    ElMessage.success('注册成功，已自动登录')
    afterLogin()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.demo-tag:active {
  opacity: 0.8;
}
</style>
