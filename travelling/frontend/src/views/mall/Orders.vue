<template>
  <div class="page-wrap">
    <h2 class="page-title">📦 订单中心</h2>
    <el-tabs v-model="activeTab" @tab-change="load">
      <el-tab-pane v-if="!auth.isMerchant" label="我的订单" name="mine" />
      <el-tab-pane v-if="auth.hasRole('MERCHANT')" label="店铺订单" name="shop" />
    </el-tabs>

    <el-empty v-if="!loading && !orders.length" description="暂无订单" />

    <div v-loading="loading" class="section-stack">
      <div v-for="o in orders" :key="o.id" class="x-card">
        <div class="flex-between mb-12">
          <div class="sub-text">
            订单号：{{ o.id }} ｜ 下单时间：{{ formatTime(o.createdAt) }}
            <span v-if="o.merchantName">｜🏪 {{ o.merchantName }}</span>
          </div>
          <el-tag :type="ORDER_STATUS_TAG[o.status] || 'info'">
            {{ ORDER_STATUS_MAP[o.status] || o.status }}
          </el-tag>
        </div>

        <div
          v-for="(item, i) in o.items"
          :key="i"
          class="flex gap-12 mb-12"
          style="align-items: center"
        >
          <el-image
            :src="item.image"
            style="width: 56px; height: 56px; border-radius: 8px"
            fit="cover"
          />
          <div style="flex: 1">
            <div style="font-weight: 600">{{ item.name }}</div>
            <div class="sub-text">¥{{ item.price }} × {{ item.quantity }}</div>
            <!-- 买家评价入口：订单完成后可对每个商品评价一次 -->
            <div v-if="activeTab === 'mine' && o.status === 'DONE'" class="mt-8">
              <el-tag v-if="isReviewed(o.id, item.productId)" type="success" size="small" effect="plain">
                已评价
              </el-tag>
              <el-button
                v-else
                link
                type="primary"
                size="small"
                @click="openReview(o, item)"
              >
                评价商品
              </el-button>
            </div>
          </div>
        </div>

        <div class="flex-between" style="border-top: 1px dashed var(--xlvq-border); padding-top: 10px">
          <div class="sub-text">收货信息：{{ o.receiver }}</div>
          <div class="flex-center gap-12">
            <span style="color: #f5222d; font-weight: 800">合计 ¥{{ o.amount }}</span>
            <!-- 商家操作 -->
            <template v-if="activeTab === 'shop'">
              <el-button
                v-if="o.status === 'PAID' || o.status === 'PENDING'"
                type="primary"
                size="small"
                @click="ship(o)"
              >
                发货
              </el-button>
            </template>
            <!-- 用户操作 -->
            <template v-else>
              <el-button
                v-if="o.status === 'SHIPPED'"
                type="success"
                size="small"
                @click="confirm(o)"
              >
                确认收货
              </el-button>
              <el-tag v-if="o.status === 'DONE'" type="success" size="small">
                担保交易已完成
              </el-tag>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 商品评价 -->
    <el-dialog v-model="reviewVisible" title="评价商品" width="480px">
      <div style="font-weight: 600">{{ reviewTarget.name }}</div>
      <div class="sub-text">订单号：{{ reviewTarget.orderId }}</div>
      <div class="mt-12 flex-center gap-8">
        <span>评分</span>
        <el-rate v-model="reviewForm.rating" />
      </div>
      <el-input
        v-model="reviewForm.content"
        class="mt-12"
        type="textarea"
        :rows="4"
        placeholder="说说商品的质量、物流与使用体验，帮助其他同学参考"
      />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSaving" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi, productReviewApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import { formatTime, ORDER_STATUS_MAP, ORDER_STATUS_TAG } from '@/utils/format'

const auth = useAuthStore()
const activeTab = ref(auth.isMerchant ? 'shop' : 'mine')
const orders = ref([])
const loading = ref(false)

/* ---------- 商品评价 ---------- */
const myReviews = ref([])
const reviewVisible = ref(false)
const reviewSaving = ref(false)
const reviewTarget = ref({})
const reviewForm = ref({ rating: 5, content: '' })

function isReviewed(orderId, productId) {
  return myReviews.value.some(
    (r) => r.orderId === orderId && r.productId === productId
  )
}

function openReview(order, item) {
  reviewTarget.value = {
    orderId: order.id,
    productId: item.productId,
    name: item.name
  }
  reviewForm.value = { rating: 5, content: '' }
  reviewVisible.value = true
}

async function submitReview() {
  reviewSaving.value = true
  try {
    await productReviewApi.create({
      orderId: reviewTarget.value.orderId,
      productId: reviewTarget.value.productId,
      productName: reviewTarget.value.name,
      rating: reviewForm.value.rating,
      content: reviewForm.value.content
    })
    ElMessage.success('评价已提交，感谢你的反馈')
    reviewVisible.value = false
    await loadReviews()
  } finally {
    reviewSaving.value = false
  }
}

async function loadReviews() {
  myReviews.value = (await productReviewApi.mine().catch(() => [])) || []
}

async function load() {
  loading.value = true
  try {
    const data =
      activeTab.value === 'mine' ? await orderApi.mine() : await orderApi.shop()
    // 后端返回 [{ order, items }]，此处拍平成订单对象+items，便于模板直接取字段
    const list = data?.list || data || []
    orders.value = list.map((x) => (x && x.order ? { ...x.order, items: x.items || [] } : x))
    // 买家评价记录仅C端角色可查（商家调用会被后端403）
    if (activeTab.value === 'mine' && !auth.isMerchant) loadReviews()
  } finally {
    loading.value = false
  }
}

async function ship(o) {
  await ElMessageBox.confirm('确认该订单已发货？', '发货确认', { type: 'warning' })
  await orderApi.ship(o.id)
  ElMessage.success('发货成功')
  load()
}

async function confirm(o) {
  await ElMessageBox.confirm(
    '确认已收到货物？确认后平台将货款结算给商家（担保交易完成）',
    '确认收货',
    { type: 'success' }
  )
  await orderApi.confirm(o.id)
  ElMessage.success('交易完成，感谢使用学旅圈商城')
  load()
}

onMounted(load)
</script>
