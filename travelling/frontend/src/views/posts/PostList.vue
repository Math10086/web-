<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <h2 class="page-title" style="margin: 0">📝 社区论坛 · 攻略 / 动态 / 问答</h2>
      <el-button type="primary" :icon="EditPen" @click="goPublish">发布帖子</el-button>
    </div>

    <div class="x-card mb-16">
      <div class="flex-between" style="flex-wrap: wrap; gap: 12px">
        <el-radio-group v-model="filters.type" @change="load">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="GUIDE">攻略</el-radio-button>
          <el-radio-button label="MOMENT">动态</el-radio-button>
          <el-radio-button label="QUESTION">问答</el-radio-button>
        </el-radio-group>

        <div class="flex gap-8" style="flex-wrap: wrap">
          <el-select
            v-model="filters.circleId"
            placeholder="全部圈层"
            clearable
            style="width: 160px"
            @change="load"
          >
            <el-option
              v-for="c in circles"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
          <el-input
            v-model="filters.keyword"
            placeholder="搜索标题/关键字"
            clearable
            style="width: 200px"
            :prefix-icon="Search"
            @keyup.enter="load"
            @clear="load"
          />
          <el-radio-group v-model="filters.sort" @change="load">
            <el-radio-button label="latest">最新</el-radio-button>
            <el-radio-button label="hot">热门</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && !posts.length" description="没有找到相关帖子" />
    <div v-loading="loading" class="post-grid">
      <PostCard v-for="p in posts" :key="p.id" :post="p" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { EditPen, Search } from '@element-plus/icons-vue'
import PostCard from '@/components/PostCard.vue'
import { circleApi, postApi } from '@/api'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const posts = ref([])
const circles = ref([])

const filters = reactive({
  type: '',
  circleId: '',
  keyword: route.query.keyword || '',
  sort: 'latest'
})

async function load() {
  loading.value = true
  try {
    const params = { sort: filters.sort }
    if (filters.type) params.type = filters.type
    if (filters.circleId) params.circleId = filters.circleId
    if (filters.keyword) params.keyword = filters.keyword
    const data = await postApi.list(params)
    posts.value = data?.list || data || []
  } finally {
    loading.value = false
  }
}

function goPublish() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/posts' } })
    return
  }
  router.push('/posts/new')
}

watch(
  () => route.query.keyword,
  (v) => {
    filters.keyword = v || ''
    load()
  }
)

onMounted(async () => {
  load()
  const data = await circleApi.list().catch(() => [])
  circles.value = data?.list || data || []
})
</script>
