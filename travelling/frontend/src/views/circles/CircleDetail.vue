<template>
  <div class="page-wrap" v-loading="loading">
    <div class="x-card mb-16" v-if="circle.id">
      <div class="flex gap-16" style="align-items: flex-start">
        <div
          class="circle-icon"
          style="width: 76px; height: 76px; font-size: 38px; margin: 0; flex: none"
        >
          {{ circle.icon || '🏕️' }}
        </div>
        <div style="flex: 1">
          <div class="flex-between">
            <h2 style="margin: 0 0 6px">
              {{ circle.name }}
              <el-tag size="small" type="info" effect="plain" class="ml-8">
                {{ circle.theme || '兴趣圈层' }}
              </el-tag>
            </h2>
            <el-button
              v-if="auth.isLoggedIn"
              :type="circle.currentUserJoined ? 'default' : 'primary'"
              round
              :loading="joining"
              @click="toggleJoin"
            >
              {{ circle.currentUserJoined ? '已加入 · 退出圈层' : '+ 加入圈层' }}
            </el-button>
            <el-button v-else type="primary" round @click="goLogin">登录后加入</el-button>
          </div>
          <div class="sub-text" style="font-size: 13px; line-height: 1.8">
            {{ circle.description }}
          </div>
          <div class="flex gap-16 mt-12 sub-text">
            <span>👥 成员{{ circle.memberCount ?? 0 }}</span>
            <span>📝 帖子{{ circle.postCount ?? 0 }}</span>
            <el-button link type="primary" size="small" @click="showMembers = true">
              查看成员
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="flex-between mb-12">
      <h3 class="page-title" style="margin: 0">圈内帖子</h3>
      <el-button type="primary" :icon="EditPen" @click="goPublish">
        在此圈层发帖
      </el-button>
    </div>

    <el-empty
      v-if="!loading && !posts.length"
      description="圈内还没有帖子，来发第一帖吧"
    />
    <div class="post-grid">
      <PostCard v-for="p in posts" :key="p.id" :post="p" />
    </div>

    <!-- 成员列表 -->
    <el-dialog v-model="showMembers" title="圈层成员" width="460px">
      <div v-for="m in members" :key="m.id" class="author-card mb-12">
        <el-avatar :size="40" :src="m.avatar">{{ m.nickname?.charAt(0) }}</el-avatar>
        <div style="flex: 1">
          <div style="font-weight: 600">
            {{ m.nickname }}
            <el-tag v-if="m.verified" type="success" size="small" class="ml-8">已认证</el-tag>
          </div>
          <div class="sub-text">{{ SCHOOL_MAP[m.school] || m.school }}</div>
        </div>
        <el-button
          v-if="auth.isLoggedIn && m.id !== auth.user?.id"
          size="small"
          @click="$router.push(`/messages?userId=${m.id}`)"
        >
          私信
        </el-button>
      </div>
      <el-empty v-if="!members.length" description="暂无成员" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { EditPen } from '@element-plus/icons-vue'
import PostCard from '@/components/PostCard.vue'
import { circleApi, postApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import { SCHOOL_MAP } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const circle = ref({})
const posts = ref([])
const members = ref([])
const loading = ref(false)
const joining = ref(false)
const showMembers = ref(false)

async function load() {
  const id = route.params.id
  loading.value = true
  try {
    const [detail, postData] = await Promise.allSettled([
      circleApi.detail(id),
      postApi.list({ circleId: id, sort: 'latest' })
    ])
    if (detail.status === 'fulfilled') circle.value = detail.value || {}
    if (postData.status === 'fulfilled') {
      posts.value = postData.value?.list || postData.value || []
    }
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  const data = await circleApi.members(route.params.id).catch(() => [])
  members.value = data?.list || data || []
}

async function toggleJoin() {
  joining.value = true
  try {
    if (circle.value.currentUserJoined) {
      await circleApi.leave(circle.value.id)
      circle.value.currentUserJoined = false
      circle.value.memberCount = Math.max(0, (circle.value.memberCount || 1) - 1)
      ElMessage.success('已退出圈层')
    } else {
      await circleApi.join(circle.value.id)
      circle.value.currentUserJoined = true
      circle.value.memberCount = (circle.value.memberCount || 0) + 1
      ElMessage.success('加入成功，一起玩！')
    }
  } finally {
    joining.value = false
  }
}

function goLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

function goPublish() {
  if (!auth.isLoggedIn) {
    goLogin()
    return
  }
  router.push({ path: '/posts/new', query: { circleId: route.params.id } })
}

watch(showMembers, (v) => {
  if (v && !members.value.length) loadMembers()
})
watch(() => route.params.id, load)
onMounted(load)
</script>

<style scoped>
.ml-8 {
  margin-left: 8px;
}
</style>
