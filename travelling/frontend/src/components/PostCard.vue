<template>
  <div class="post-card" @click="goDetail">
    <div v-if="cover" class="cover-wrap">
      <img class="cover" :src="cover" :alt="post.title" />
      <el-tag
        class="pc-type"
        size="small"
        :type="POST_TYPE_TAG_TYPE[post.type] || 'info'"
        effect="dark"
        style="position: absolute; top: 8px; left: 8px"
      >
        {{ POST_TYPE_MAP[post.type] || '帖子' }}
      </el-tag>
      <span v-if="hot" class="hot-flame feed-ad-tag" style="left: auto; right: 8px">🔥 热门</span>
    </div>
    <div v-else class="cover-fallback">
      <el-tag
        size="small"
        :type="POST_TYPE_TAG_TYPE[post.type] || 'info'"
        effect="dark"
      >
        {{ POST_TYPE_MAP[post.type] || '帖子' }}
      </el-tag>
    </div>
    <div class="pc-body">
      <div class="pc-title">{{ post.title }}</div>
      <!-- 预算向结构化攻略：人均预算、建议天数、避坑提示、适合人群 -->
      <div v-if="hasStructured" class="pc-b2">
        <span v-if="post.costPerPerson != null" class="b2-item b2-cost">
          人均 ¥{{ Math.round(Number(post.costPerPerson)) }}
        </span>
        <span v-if="post.days" class="b2-item">建议{{ post.days }}天</span>
        <template v-if="avoidLabels.length">
          <el-tag
            v-for="t in avoidLabels.slice(0, 2)"
            :key="'av-' + t"
            size="small"
            type="warning"
            effect="plain"
          >
            🕳️ {{ t }}
          </el-tag>
        </template>
        <template v-if="suitableLabels.length">
          <el-tag
            v-for="t in suitableLabels.slice(0, 2)"
            :key="'su-' + t"
            size="small"
            type="info"
            effect="plain"
          >
            👥 {{ t }}
          </el-tag>
        </template>
      </div>
      <div class="pc-meta">
        <el-avatar :size="22" :src="post.author?.avatar">
          {{ post.author?.nickname?.charAt(0) || '游' }}
        </el-avatar>
        <span>{{ post.author?.nickname || '匿名同学' }}</span>
        <el-icon v-if="post.city"><Location /></el-icon>
        <span v-if="post.city">{{ post.city }}</span>
      </div>
      <div class="pc-stats">
        <span><el-icon><View /></el-icon> {{ num(post.viewCount ?? post.views) }}</span>
        <span><el-icon><Pointer /></el-icon> {{ num(post.likeCount ?? post.likes) }}</span>
        <span><el-icon><Star /></el-icon> {{ num(post.favoriteCount ?? post.favorites) }}</span>
        <span v-if="post.trustedBadge" style="margin-left: auto">
          <el-tag type="success" size="small">本地认证</el-tag>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { POST_TYPE_MAP, POST_TYPE_TAG_TYPE, splitImages } from '@/utils/format'
import { loadDict, labelsOf } from '@/utils/tagDict'

const props = defineProps({
  post: { type: Object, required: true }
})

const router = useRouter()

const cover = computed(() => splitImages(props.post.images)[0] || '')
// 高赞或按热度排序时显示小火苗
const hot = computed(
  () =>
    Number(props.post.likeCount ?? props.post.likes ?? 0) >= 20 ||
    props.post.hot === true
)

/* ---------- 结构化攻略字段（文案取自POST作用域的字典） ---------- */
const hasStructured = computed(
  () =>
    props.post.costPerPerson != null ||
    !!props.post.days ||
    !!props.post.avoidTags ||
    !!props.post.suitableFor
)
const avoidLabels = computed(() => labelsOf('POST', 'avoid', props.post.avoidTags))
const suitableLabels = computed(() => labelsOf('POST', 'suitable', props.post.suitableFor))

onMounted(() => loadDict('POST'))

function num(v) {
  const n = Number(v) || 0
  return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : n
}

function goDetail() {
  router.push(`/posts/${props.post.id}`)
}
</script>

<style scoped>
.cover-wrap {
  position: relative;
}
.pc-stats .el-icon {
  vertical-align: -2px;
  margin-right: 2px;
}
.pc-b2 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 6px 0 4px;
}
.pc-b2 .b2-item {
  font-size: 12px;
  color: var(--xlvq-text-sub, #909399);
  background: #f4f4f5;
  border-radius: 4px;
  padding: 1px 6px;
  line-height: 18px;
}
.pc-b2 .b2-cost {
  color: #f5222d;
  font-weight: 700;
  background: #fff1f0;
}
</style>
