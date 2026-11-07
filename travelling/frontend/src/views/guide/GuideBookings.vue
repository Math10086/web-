<template>
  <div :class="embedded ? '' : 'page-wrap'">
    <h2 v-if="!embedded" class="page-title">{{ isGuide ? '🎫 导游预约订单' : '我的导游预约' }}</h2>
    <!-- 接单视角为导游账号专属；其他账号只保留「我预约的（游客）」列表 -->
    <el-tabs v-if="isGuide" v-model="role" @tab-change="load">
      <el-tab-pane label="我发出的预约" name="traveler" />
      <el-tab-pane label="我收到的预约" name="guide" />
    </el-tabs>
    <div v-else class="sub-text mb-12">这里是你预约导游服务的记录，接单由导游在自己的账号下处理</div>

    <el-empty v-if="!loading && !bookings.length" description="暂无预约订单" />

    <div v-loading="loading" class="section-stack">
      <div v-for="b in bookings" :key="b.id" class="x-card">
        <div class="flex-between mb-12" style="flex-wrap: wrap; gap: 8px">
          <div>
            <strong>{{ b.serviceTitle || b.title || '导游服务' }}</strong>
            <el-tag :type="BOOKING_STATUS_TAG[b.status] || 'info'" size="small" class="ml-8">
              {{ BOOKING_STATUS_MAP[b.status] || b.status }}
            </el-tag>
          </div>
          <div class="sub-text">出行日期：{{ formatDate(b.tripDate) }}</div>
        </div>

        <div class="sub-text" style="line-height: 2">
          <div v-if="role === 'traveler'">导游：{{ b.guideName || '待分配' }}</div>
          <div v-else>游客：{{ b.travelerName || b.nickname || '同学' }}</div>
          <div v-if="b.remark">需求备注：{{ b.remark }}</div>
        </div>

        <!-- 分佣明细 -->
        <el-alert
          v-if="showSettlement(b)"
          type="success"
          :closable="false"
          class="mt-12"
          :title="settlementText(b)"
        />

        <!-- 已评价 -->
        <div v-if="b.reviewContent || b.rating" class="mt-12">
          <el-rate :model-value="Number(b.rating) || 0" disabled size="small" />
          <span class="sub-text" style="margin-left: 8px">{{ b.reviewContent }}</span>
        </div>

        <div class="mt-12" style="text-align: right">
          <!-- 游客操作 -->
          <template v-if="role === 'traveler'">
            <el-button
              v-if="['PENDING', 'PAID', 'CONFIRMED'].includes(b.status)"
              type="danger"
              plain
              size="small"
              @click="cancel(b)"
            >
              取消预约
            </el-button>
            <el-button
              v-if="b.status === 'CONFIRMED'"
              type="success"
              size="small"
              @click="complete(b)"
            >
              确认服务完成
            </el-button>
            <el-button
              v-if="canReview(b)"
              type="primary"
              size="small"
              @click="openReview(b)"
            >
              评价导游
            </el-button>
          </template>
          <!-- 导游操作 -->
          <template v-else>
            <el-button
              v-if="['PENDING', 'PAID'].includes(b.status)"
              type="primary"
              size="small"
              @click="confirm(b)"
            >
              确认接单
            </el-button>
            <el-button
              v-if="b.status === 'CONFIRMED'"
              type="success"
              size="small"
              @click="complete(b)"
            >
              完成服务
            </el-button>
          </template>
        </div>
      </div>
    </div>

    <!-- 评价弹窗 -->
    <el-dialog v-model="reviewVisible" title="评价导游服务" width="460px">
      <div class="mb-12 sub-text">
        从讲解水平、行程向导、准时度等方面评价，会计入导游信誉与推荐权重
      </div>
      <el-rate v-model="reviewForm.rating" :texts="['差评', '一般', '还行', '满意', '超赞']" show-text />
      <el-input
        v-model="reviewForm.content"
        type="textarea"
        :rows="3"
        class="mt-12"
        placeholder="说说这次服务的体验"
      />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { guideApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import {
  BOOKING_STATUS_MAP,
  BOOKING_STATUS_TAG,
  formatDate
} from '@/utils/format'

const route = useRoute()
const auth = useAuthStore()

/** embedded：被「找导游」页以标签页形式嵌入时隐藏标题与外边距 */
defineProps({ embedded: { type: Boolean, default: false } })

/** 接单（导游视角）仅认证导游账号可用 */
const isGuide = computed(() => auth.isGuide)

/** 导游从菜单/个人中心进来默认看「我接到的」；游客下单后跳转默认看「我预约的」 */
function initialTab() {
  // 合并后外层用 ?inner= 传递内部页签（?tab= 已被外层「预约视图」占用）
  const q = String(route.query.inner || route.query.tab || '')
  if (isGuide.value && q === 'traveler') return 'traveler'
  return isGuide.value ? 'guide' : 'traveler'
}

const role = ref(initialTab())
const bookings = ref([])
const loading = ref(false)
const acting = ref(false)

const reviewVisible = ref(false)
const reviewTarget = ref(null)
const reviewForm = ref({ rating: 5, content: '' })

function showSettlement(b) {
  return ['SETTLED', 'COMPLETED', 'REVIEWED'].includes(b.status) && (b.amount || b.fee)
}
/** 结算明细：服务费=订单金额，平台手续费=金额×10%，导游到账=金额×90%（后端已有值则直接用） */
function settlementText(b) {
  const amount = Number(b.amount || 0)
  const fee = b.fee != null && b.fee > 0 ? Number(b.fee) : amount * 0.1
  const income = b.guideIncome != null && b.guideIncome > 0 ? Number(b.guideIncome) : amount * 0.9
  return `平台担保结算：服务费 ¥${amount.toFixed(2)}，平台手续费10% = ¥${fee.toFixed(2)}，导游到账90% = ¥${income.toFixed(2)}`
}
function canReview(b) {
  return ['COMPLETED', 'SETTLED'].includes(b.status) && !b.reviewContent
}

async function load() {
  // 非导游账号一律按游客视角取数，不请求接单数据
  if (!isGuide.value) role.value = 'traveler'
  loading.value = true
  try {
    const data = await guideApi.bookings(role.value)
    bookings.value = data?.list || data || []
  } finally {
    loading.value = false
  }
}

async function confirm(b) {
  await ElMessageBox.confirm('确认接受该预约？确认后游客将收到通知', '确认接单', {
    type: 'warning'
  })
  await guideApi.confirmBooking(b.id)
  ElMessage.success('已确认接单')
  load()
}

async function complete(b) {
  await ElMessageBox.confirm(
    '确认服务已完成？完成后平台进入结算：手续费10%，导游到账90%',
    '完成服务',
    { type: 'success' }
  )
  await guideApi.completeBooking(b.id)
  ElMessage.success('服务已完成，平台将完成结算')
  load()
}

async function cancel(b) {
  await ElMessageBox.confirm('确定取消该预约吗？', '取消预约', { type: 'warning' })
  await guideApi.cancelBooking(b.id)
  ElMessage.success('预约已取消')
  load()
}

function openReview(b) {
  reviewTarget.value = b
  reviewForm.value = { rating: 5, content: '' }
  reviewVisible.value = true
}

async function submitReview() {
  acting.value = true
  try {
    await guideApi.reviewBooking(reviewTarget.value.id, {
      rating: reviewForm.value.rating,
      content: reviewForm.value.content
    })
    ElMessage.success('评价已提交')
    reviewVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ml-8 {
  margin-left: 8px;
}
</style>
