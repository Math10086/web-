<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <div>
        <h2 class="page-title" style="margin: 0">🏘️ 本地论坛 · 本地人的靠谱攻略</h2>
        <div class="sub-text" style="margin-top: 4px">
          由目的地城市的本地居民与资深旅行者分享餐饮、交通、避坑、时段、消费等实用信息
        </div>
      </div>
      <el-button type="primary" :icon="QuestionFilled" @click="openAsk">
        我要提问
      </el-button>
    </div>

    <div class="forum-layout">
      <!-- 城市板块 -->
      <div class="x-card city-menu">
        <div
          v-for="c in cities"
          :key="c"
          class="city-row"
          :class="{ active: activeCity === c }"
          @click="activeCity = c"
        >
          <span>{{ c === '全部' ? '🌏' : '📍' }} {{ c }}</span>
        </div>
      </div>

      <div class="section-stack" v-loading="loading">
        <!-- 问答求助 -->
        <div class="x-card">
          <div class="card-title">❓ 问答求助</div>
          <el-empty
            v-if="!questions.length"
            description="该城市板块暂无提问"
            :image-size="56"
          />
          <div
            v-for="q in questions"
            :key="q.id"
            class="forum-row"
            @click="$router.push(`/posts/${q.id}`)"
          >
            <el-tag type="warning" size="small">问</el-tag>
            <span class="fr-title">{{ q.title }}</span>
            <span class="sub-text">{{ q.author?.nickname }}</span>
            <span class="fr-stat">💬 {{ q.commentCount ?? 0 }}</span>
          </div>
        </div>

        <!-- 本地攻略流 -->
        <div class="x-card">
          <div class="card-title">
            📌 本地攻略 / 经验帖
            <span class="more-link">{{ activeCity === '全部' ? '全部城市' : activeCity }}</span>
          </div>
          <el-empty
            v-if="!localPosts.length"
            description="该城市还没有本地攻略，等你来分享"
          />
          <div
            v-for="p in localPosts"
            :key="p.id"
            class="forum-row"
            @click="$router.push(`/posts/${p.id}`)"
          >
            <el-tag type="danger" size="small">本地</el-tag>
            <el-tag v-if="p.trustedBadge" type="success" size="small">本地认证</el-tag>
            <span class="fr-title">{{ p.title }}</span>
            <span class="sub-text">{{ p.author?.nickname }}</span>
            <span class="fr-stat">👍 {{ p.likeCount ?? 0 }}</span>
            <span class="fr-stat">💬 {{ p.commentCount ?? 0 }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 提问弹窗 -->
    <el-dialog v-model="askVisible" title="向本地同学提问" width="540px">
      <el-form :model="askForm" :rules="askRules" ref="askFormRef" label-width="84px">
        <el-form-item label="目标城市" prop="city">
          <el-select v-model="askForm.city" placeholder="选择想提问的城市" style="width: 100%">
            <el-option
              v-for="c in cities.filter((x) => x !== '全部')"
              :key="c"
              :label="c"
              :value="c"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="问题标题" prop="title">
          <el-input
            v-model="askForm.title"
            placeholder="如：国庆去张家界，三天怎么安排不踩坑？"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="问题详情" prop="content">
          <el-input
            v-model="askForm.content"
            type="textarea"
            :rows="4"
            placeholder="说清你的出行时间、人数、预算与偏好，本地同学更好回答"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="askVisible = false">取消</el-button>
        <el-button type="primary" :loading="asking" @click="submitAsk">提交问题</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { postApi } from '@/api'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const cities = ['全部', '长沙', '张家界', '重庆', '成都', '西安', '厦门']
const activeCity = ref(route.query.city || '全部')

const loading = ref(false)
const localPosts = ref([])
const questions = ref([])

const askVisible = ref(false)
const asking = ref(false)
const askFormRef = ref()
const askForm = ref({
  city: activeCity.value !== '全部' ? activeCity.value : '',
  title: '',
  content: ''
})
const askRules = {
  city: [{ required: true, message: '请选择目标城市', trigger: 'change' }],
  title: [{ required: true, message: '请输入问题标题', trigger: 'blur' }],
  content: [{ required: true, message: '请描述问题详情', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const params = activeCity.value !== '全部' ? { city: activeCity.value } : {}
    const [local, qa] = await Promise.allSettled([
      postApi.list({ type: 'LOCAL', sort: 'latest', ...params }),
      postApi.list({ type: 'QUESTION', sort: 'latest', ...params })
    ])
    localPosts.value =
      local.status === 'fulfilled' ? local.value?.list || local.value || [] : []
    questions.value =
      qa.status === 'fulfilled' ? qa.value?.list || qa.value || [] : []
  } finally {
    loading.value = false
  }
}

function openAsk() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  askForm.value = {
    city: activeCity.value !== '全部' ? activeCity.value : '',
    title: '',
    content: ''
  }
  askVisible.value = true
}

async function submitAsk() {
  await askFormRef.value.validate()
  asking.value = true
  try {
    await postApi.create({
      type: 'QUESTION',
      title: askForm.value.title,
      content: askForm.value.content,
      city: askForm.value.city,
      circleId: null,
      destination: '',
      modeTag: '',
      images: ''
    })
    ElMessage.success('问题已发布，本地同学会尽快解答')
    askVisible.value = false
    activeCity.value = askForm.value.city
    load()
  } finally {
    asking.value = false
  }
}

watch(activeCity, load)
onMounted(load)
</script>

<style scoped>
.city-row {
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  margin-bottom: 4px;
  transition: all 0.15s;
}
.city-row:hover {
  background: var(--xlvq-primary-light);
}
.city-row.active {
  background: var(--xlvq-primary);
  color: #fff;
  font-weight: 600;
}
.forum-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 6px;
  border-bottom: 1px dashed var(--xlvq-border);
  cursor: pointer;
}
.forum-row:hover .fr-title {
  color: var(--xlvq-primary);
}
.fr-title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fr-stat {
  color: var(--xlvq-text-sub);
  font-size: 12px;
  white-space: nowrap;
}
</style>
