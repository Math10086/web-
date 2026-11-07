<template>
  <div class="page-wrap" v-loading="loading">
    <div class="home-layout" v-if="post.id">
      <div class="home-main">
        <div class="x-card">
          <div class="flex gap-8 mb-12">
            <el-tag :type="POST_TYPE_TAG_TYPE[post.type] || 'info'">
              {{ POST_TYPE_MAP[post.type] || '帖子' }}
            </el-tag>
            <el-tag v-if="post.trustedBadge" type="success">本地认证</el-tag>
            <el-tag v-if="post.circleName" type="info" effect="plain">
              {{ post.circleName }}
            </el-tag>
            <el-tag v-if="post.city" type="warning" effect="plain">
              📍 {{ post.city }}{{ post.destination ? ' · ' + post.destination : '' }}
            </el-tag>
            <el-tag v-if="post.modeTag" effect="plain">{{ post.modeTag }}</el-tag>
          </div>

          <!-- 预算向结构化攻略：人均预算、建议天数、避坑主题、适合人群 -->
          <div v-if="hasStructured" class="b2-panel mb-12">
            <div v-if="post.costPerPerson != null" class="b2-cell">
              <span class="b2-k">人均预算</span>
              <span class="b2-v b2-price">¥{{ Math.round(Number(post.costPerPerson)) }}</span>
            </div>
            <div v-if="post.days" class="b2-cell">
              <span class="b2-k">建议天数</span>
              <span class="b2-v">{{ post.days }}天</span>
            </div>
            <div v-if="avoidLabels.length" class="b2-cell">
              <span class="b2-k">避坑主题</span>
              <span class="b2-v">
                <el-tag
                  v-for="t in avoidLabels"
                  :key="'av-' + t"
                  size="small"
                  type="warning"
                  effect="plain"
                  class="mr-6"
                >
                  {{ t }}
                </el-tag>
              </span>
            </div>
            <div v-if="suitableLabels.length" class="b2-cell">
              <span class="b2-k">适合人群</span>
              <span class="b2-v">
                <el-tag
                  v-for="t in suitableLabels"
                  :key="'su-' + t"
                  size="small"
                  type="info"
                  effect="plain"
                  class="mr-6"
                >
                  {{ t }}
                </el-tag>
              </span>
            </div>
          </div>

          <h1 style="font-size: 24px; margin: 0 0 14px; line-height: 1.4">
            {{ post.title }}
          </h1>

          <div class="author-card mb-12">
            <el-avatar
              :size="44"
              :src="post.author?.avatar"
              style="cursor: pointer"
              @click="$router.push(`/profile?id=${post.authorId}`)"
            >
              {{ post.author?.nickname?.charAt(0) || '游' }}
            </el-avatar>
            <div style="flex: 1">
              <div style="font-weight: 700">
                {{ post.author?.nickname }}
                <el-tag v-if="post.author?.verified" type="success" size="small" class="ml-8">
                  {{ SCHOOL_MAP[post.author?.school] || '三校认证' }}
                </el-tag>
                <el-tag v-if="post.author?.bloggerLevel" size="small" class="ml-8">
                  Lv.{{ post.author.bloggerLevel }}博主
                </el-tag>
              </div>
              <div class="sub-text">{{ formatTime(post.createdAt) }}</div>
            </div>
            <el-button
              v-if="canEdit"
              size="small"
              @click="$router.push(`/posts/${post.id}/edit`)"
            >
              编辑
            </el-button>
            <el-button v-if="canEdit" size="small" type="danger" plain @click="remove">
              删除
            </el-button>
            <el-button size="small" :icon="Warning" @click="reportVisible = true">
              举报
            </el-button>
          </div>

          <el-image
            v-for="(img, i) in images"
            :key="i"
            :src="img"
            :preview-src-list="images"
            :initial-index="i"
            fit="cover"
            style="width: 100%; max-height: 420px; border-radius: 10px; margin-bottom: 10px"
          />

          <div class="detail-content">{{ post.content }}</div>

          <div class="flex-center gap-16 mt-16" style="border-top: 1px solid var(--xlvq-border); padding-top: 16px">
            <el-button
              round :type="post.liked ? 'primary' : 'default'"
              :icon="Pointer" @click="doLike"
            >
              点赞{{ post.likeCount ?? 0 }}
            </el-button>
            <el-button
              round :type="post.favorited ? 'warning' : 'default'"
              :icon="Star" @click="doFavorite"
            >
              收藏{{ post.favoriteCount ?? 0 }}
            </el-button>
            <el-button round :icon="ChatDotRound">
              评论{{ post.commentCount ?? comments.length }}
            </el-button>
            <el-button round :icon="Connection">
              被引用{{ post.citeCount ?? 0 }}
            </el-button>
            <el-button round :icon="View">
              浏览{{ post.viewCount ?? 0 }}
            </el-button>
          </div>
        </div>

        <!-- 评论区 -->
        <div class="x-card">
          <div class="card-title">💬 评论（{{ comments.length }}）</div>
          <div class="flex gap-8 mb-16">
            <el-avatar :size="36" :src="auth.user?.avatar">
              {{ auth.user?.nickname?.charAt(0) || '游' }}
            </el-avatar>
            <el-input
              v-model="commentText"
              type="textarea"
              :rows="2"
              :placeholder="auth.isLoggedIn ? '友善发言，分享你的看法…' : '登录后参与评论'"
              @keyup.enter.ctrl="sendComment"
            />
            <el-button type="primary" :disabled="!auth.isLoggedIn" @click="sendComment">
              发表
            </el-button>
          </div>

          <el-empty v-if="!comments.length" description="还没有评论，来抢沙发" :image-size="70" />
          <div v-for="(c, i) in comments" :key="c.id || i" class="comment-item">
            <el-avatar :size="36" :src="c.avatar">{{ c.nickname?.charAt(0) }}</el-avatar>
            <div style="flex: 1">
              <div style="font-weight: 600; font-size: 13px">{{ c.nickname }}</div>
              <div style="line-height: 1.7; margin: 2px 0">{{ c.content }}</div>
              <div class="sub-text">{{ formatTime(c.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 侧边：相关本地攻略 -->
      <div class="home-side">
        <div class="x-card">
          <div class="card-title">🧭 相关本地攻略</div>
          <el-empty v-if="!related.length" description="暂无相关攻略" :image-size="60" />
          <div
            v-for="r in related"
            :key="r.id"
            class="related-item"
            @click="goRelated(r.id)"
          >
            <el-tag v-if="r.trustedBadge" type="success" size="small">本地认证</el-tag>
            <div class="r-title">{{ r.title }}</div>
            <div class="sub-text">
              {{ r.author?.nickname }} · 👍 {{ r.likeCount ?? 0 }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 举报弹窗 -->
    <el-dialog v-model="reportVisible" title="举报帖子" width="480px">
      <el-form :model="reportForm" label-width="92px">
        <el-form-item label="举报原因" required>
          <el-select v-model="reportForm.reason" placeholder="选择原因" style="width: 100%">
            <el-option label="广告 / 引流营销" value="广告营销" />
            <el-option label="虚假不实信息" value="虚假信息" />
            <el-option label="不文明 / 辱骂内容" value="不文明内容" />
            <el-option label="违法违规 / 危险引导" value="违法违规" />
            <el-option label="抄袭侵权" value="抄袭侵权" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="证据说明">
          <el-input
            v-model="reportForm.evidence"
            type="textarea"
            :rows="4"
            placeholder="请描述具体问题，可粘贴截图链接等证据"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="danger" :loading="reporting" @click="submitReport">提交举报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElMessage,
  ElMessageBox
} from 'element-plus'
import {
  Pointer,
  Star,
  ChatDotRound,
  Connection,
  View,
  Warning
} from '@element-plus/icons-vue'
import { postApi, reportApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import {
  formatTime,
  POST_TYPE_MAP,
  POST_TYPE_TAG_TYPE,
  SCHOOL_MAP,
  splitImages
} from '@/utils/format'
import { loadDict, labelsOf } from '@/utils/tagDict'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const post = ref({})
const comments = ref([])
const related = ref([])
const commentText = ref('')

const reportVisible = ref(false)
const reporting = ref(false)
const reportForm = ref({ reason: '', evidence: '' })

const images = computed(() => splitImages(post.value.images))

/** ---------- 结构化攻略字段 ---------- */
const hasStructured = computed(
  () =>
    post.value.costPerPerson != null ||
    !!post.value.days ||
    !!post.value.avoidTags ||
    !!post.value.suitableFor
)
const avoidLabels = computed(() => labelsOf('POST', 'avoid', post.value.avoidTags))
const suitableLabels = computed(() => labelsOf('POST', 'suitable', post.value.suitableFor))

const canEdit = computed(
  () =>
    auth.isLoggedIn &&
    (post.value.authorId === auth.user?.id || auth.user?.role === 'ADMIN')
)

async function load() {
  const id = route.params.id
  loading.value = true
  try {
    const [detail, commentData] = await Promise.allSettled([
      postApi.detail(id),
      postApi.comments(id)
    ])
    if (detail.status === 'fulfilled' && detail.value) {
      post.value = detail.value
      comments.value = detail.value.comments || []
      if (post.value.city) loadRelated(post.value.city)
    }
    if (commentData.status === 'fulfilled') {
      comments.value = commentData.value?.list || commentData.value || []
    }
  } finally {
    loading.value = false
  }
}

async function loadRelated(city) {
  const data = await postApi
    .list({ type: 'GUIDE', city, sort: 'hot' })
    .catch(() => [])
  related.value = (data?.list || data || [])
    .filter((p) => p.id !== post.value.id)
    .slice(0, 6)
}

function requireLogin() {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

async function doLike() {
  if (!requireLogin()) return
  const data = await postApi.like(post.value.id)
  post.value.liked = !post.value.liked
  post.value.likeCount = Math.max(
    0,
    Number(post.value.likeCount ?? 0) + (post.value.liked ? 1 : -1)
  )
  if (data?.liked !== undefined) post.value.liked = data.liked
  if (data?.likeCount !== undefined) post.value.likeCount = data.likeCount
}

async function doFavorite() {
  if (!requireLogin()) return
  const data = await postApi.favorite(post.value.id)
  post.value.favorited = !post.value.favorited
  post.value.favoriteCount = Math.max(
    0,
    Number(post.value.favoriteCount ?? 0) + (post.value.favorited ? 1 : -1)
  )
  if (data?.favorited !== undefined) post.value.favorited = data.favorited
  ElMessage.success(post.value.favorited ? '已收藏' : '已取消收藏')
}

async function sendComment() {
  if (!requireLogin() || !commentText.value.trim()) return
  await postApi.addComment(post.value.id, commentText.value.trim())
  ElMessage.success('评论成功')
  commentText.value = ''
  const data = await postApi.comments(post.value.id)
  comments.value = data?.list || data || []
}

async function submitReport() {
  if (!reportForm.value.reason) {
    ElMessage.warning('请选择举报原因')
    return
  }
  reporting.value = true
  try {
    await reportApi.create({
      targetType: 'POST',
      targetId: post.value.id,
      reason: reportForm.value.reason,
      evidence: reportForm.value.evidence
    })
    ElMessage.success('举报已提交，管理员会尽快处理')
    reportVisible.value = false
    reportForm.value = { reason: '', evidence: '' }
  } finally {
    reporting.value = false
  }
}

async function remove() {
  await ElMessageBox.confirm('确定删除该帖子吗？删除后不可恢复', '危险操作', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await postApi.remove(post.value.id)
  ElMessage.success('已删除')
  router.push('/posts')
}

function goRelated(id) {
  router.push(`/posts/${id}`)
}

watch(() => route.params.id, load)
onMounted(() => {
  loadDict('POST')
  load()
})
</script>

<style scoped>
.mr-6 {
  margin-right: 6px;
}
.b2-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 26px;
  padding: 12px 16px;
  border: 1px solid var(--xlvq-border, #ebeef5);
  border-radius: 10px;
  background: #fafafa;
}
.b2-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.b2-k {
  font-size: 13px;
  color: var(--xlvq-text-sub, #909399);
}
.b2-v {
  font-size: 14px;
  font-weight: 600;
}
.b2-price {
  color: #f5222d;
}
.ml-8 {
  margin-left: 8px;
}
.comment-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.related-item {
  padding: 10px 6px;
  border-bottom: 1px dashed var(--xlvq-border);
  cursor: pointer;
}
.related-item:hover .r-title {
  color: var(--xlvq-primary);
}
.related-item .r-title {
  font-weight: 600;
  font-size: 13px;
  margin: 4px 0 2px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
