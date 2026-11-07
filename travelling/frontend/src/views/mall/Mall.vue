<template>
  <div class="page-wrap">
    <div class="gradient-banner gradient-orange mb-16" style="padding: 24px 30px">
      <h1 style="font-size: 24px">🎒 旅行用品商城 · 平台担保交易</h1>
      <p>背包、帐篷、防潮垫、摄影装备、户外用品一站购齐，自营＋商家入驻，售后有保障</p>
    </div>

    <div class="x-card mb-16">
      <div class="flex gap-8" style="flex-wrap: wrap">
        <el-input
          v-model="keyword"
          placeholder="搜索商品"
          clearable
          style="width: 240px"
          :prefix-icon="Search"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select
          v-model="category"
          placeholder="全部分类"
          clearable
          style="width: 180px"
          @change="load"
        >
          <el-option v-for="c in PRODUCT_CATEGORIES" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button type="primary" @click="load">搜索</el-button>
        <el-button @click="$router.push('/orders')">我的订单</el-button>
        <el-button type="warning" plain @click="$router.push('/merchant')">
          商家中心
        </el-button>
      </div>
    </div>

    <el-empty v-if="!loading && !products.length" description="没有找到相关商品" />
    <div v-loading="loading" class="product-grid">
      <div v-for="p in products" :key="p.id" class="x-card product-card hoverable" @click="openDetail(p)">
        <img v-if="p.image" :src="p.image" :alt="p.name" class="p-img" />
        <div v-else class="p-img flex-center" style="font-size: 40px">🎒</div>
        <div class="p-name">{{ p.name }}</div>
        <div class="flex-between">
          <span class="p-price">¥{{ p.price }}</span>
          <span class="sub-text">已售{{ p.sales ?? p.sold ?? 0 }}</span>
        </div>
        <div class="sub-text mt-12">🏪 {{ p.merchantName || p.shopName || '学旅自营' }}</div>
      </div>
    </div>

    <!-- 商品详情 -->
    <el-dialog v-model="detailVisible" :title="current?.name" width="560px">
      <template v-if="current">
        <el-image
          v-if="current.image"
          :src="current.image"
          fit="cover"
          style="width: 100%; max-height: 300px; border-radius: 10px"
        />
        <div v-else class="flex-center" style="height: 220px; background: #f0f5fc; border-radius: 10px; font-size: 60px">
          🎒
        </div>
        <div class="mt-12 flex-between">
          <span class="p-price" style="font-size: 24px">¥{{ current.price }}</span>
          <el-tag>库存{{ current.stock }}</el-tag>
        </div>
        <el-tag size="small" type="info" class="mt-12">{{ current.category }}</el-tag>
        <div class="sub-text mt-12" style="line-height: 1.8; white-space: pre-wrap">
          {{ current.description }}
        </div>

        <!-- 平替 / 升级替代：同品类横向比较，帮买家少花冤枉钱（商家视角无意义，隐藏） -->
        <div v-if="!auth.isMerchant" v-loading="altLoading" class="alt-wrap">
          <div class="alt-title">🔀 同品类对比 · 也许更适合你</div>
          <template v-if="alt">
            <div v-if="alt.cheaper?.length" class="alt-group">
              <div class="alt-sub">💰 更省钱的平替</div>
              <div
                v-for="a in alt.cheaper"
                :key="a.id"
                class="alt-item"
                @click="switchProduct(a.id)"
              >
                <div class="alt-line">
                  <span class="alt-name">{{ a.name }}</span>
                  <span class="alt-price">¥{{ a.price }}</span>
                </div>
                <div class="sub-text alt-reason">{{ a.reason }}</div>
                <div class="sub-text alt-reason">🏪 {{ a.shopName }}</div>
              </div>
            </div>
            <div v-if="alt.premium?.length" class="alt-group">
              <div class="alt-sub">⬆️ 想要更高配</div>
              <div
                v-for="a in alt.premium"
                :key="a.id"
                class="alt-item"
                @click="switchProduct(a.id)"
              >
                <div class="alt-line">
                  <span class="alt-name">{{ a.name }}</span>
                  <span class="alt-price">¥{{ a.price }}</span>
                </div>
                <div class="sub-text alt-reason">{{ a.reason }}</div>
                <div class="sub-text alt-reason">🏪 {{ a.shopName }}</div>
              </div>
            </div>
            <div v-if="!alt.cheaper?.length && !alt.premium?.length && alt.others?.length" class="alt-group">
              <div class="alt-sub">🧭 同品类其他热销（用途不同，仅供参考）</div>
              <div
                v-for="a in alt.others"
                :key="a.id"
                class="alt-item alt-other"
                @click="switchProduct(a.id)"
              >
                <div class="alt-line">
                  <span class="alt-name">{{ a.name }}</span>
                  <span class="alt-price">¥{{ a.price }}</span>
                </div>
                <div class="sub-text alt-reason">已售{{ a.sales }}件 · 🏪 {{ a.shopName }}</div>
              </div>
            </div>
            <div v-if="!alt.cheaper?.length && !alt.premium?.length && !alt.others?.length" class="sub-text alt-reason">
              {{ alt.note }}
            </div>
          </template>
        </div>
        <!-- 商家是经营方而非买家（后端禁止商家下单），隐藏购买流程 -->
        <div v-if="!auth.isMerchant" class="flex-center gap-8 mt-16">
          <span>数量：</span>
          <el-input-number v-model="quantity" :min="1" :max="current.stock || 99" />
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">再逛逛</el-button>
        <el-button v-if="!auth.isMerchant" type="primary" :loading="ordering" @click="openCheckout">
          立即购买（合计 ¥{{ ((current?.price || 0) * quantity).toFixed(2) }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- 收货信息 -->
    <el-dialog v-model="checkoutVisible" title="确认收货信息" width="460px">
      <el-alert
        type="success"
        :closable="false"
        class="mb-12"
        title="平台担保交易：确认收货后款项才会结算给商家，支持售后与纠纷处理"
      />
      <el-form label-width="84px">
        <el-form-item label="收货信息" required>
          <el-input
            v-model="receiver"
            type="textarea"
            :rows="3"
            placeholder="姓名、手机号、收货地址（校园可填宿舍/快递站）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkoutVisible = false">取消</el-button>
        <el-button type="primary" :loading="ordering" @click="submitOrder">
          提交并模拟支付
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { orderApi, productApi } from '@/api'
import { PRODUCT_CATEGORIES } from '@/utils/format'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const products = ref([])
const keyword = ref('')
const category = ref('')

const detailVisible = ref(false)
const checkoutVisible = ref(false)
const current = ref(null)
const quantity = ref(1)
const receiver = ref('')
const ordering = ref(false)

async function load() {
  loading.value = true
  try {
    const params = {}
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (category.value) params.category = category.value
    const data = await productApi.list(params)
    products.value = data?.list || data || []
  } finally {
    loading.value = false
  }
}

function openDetail(p) {
  current.value = p
  quantity.value = 1
  detailVisible.value = true
  loadAlternatives(p.id)
}

/* ---------- 平替/升级替代 ---------- */
const alt = ref(null)
const altLoading = ref(false)

async function loadAlternatives(id) {
  alt.value = null
  altLoading.value = true
  try {
    alt.value = await productApi.alternatives(id)
  } catch (e) {
    alt.value = null
  } finally {
    altLoading.value = false
  }
}

/** 点替代品直接切到那件商品（详情弹窗保持打开，避免来回复关闭） */
async function switchProduct(id) {
  try {
    const p = await productApi.detail(id)
    if (p) openDetail(p)
  } catch (e) {
    ElMessage.warning('该商品已下架')
  }
}

function openCheckout() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/mall' } })
    return
  }
  // 商家账号不可下单（后端requireBusiness会403）
  if (auth.isMerchant) {
    ElMessage.warning('商家账号仅可经营店铺，不支持购买商品')
    return
  }
  if (quantity.value > (current.value.stock || 0)) {
    ElMessage.warning('库存不足')
    return
  }
  detailVisible.value = false
  checkoutVisible.value = true
}

async function submitOrder() {
  if (!receiver.value.trim()) {
    ElMessage.warning('请填写收货信息')
    return
  }
  ordering.value = true
  try {
    await orderApi.create({
      items: [{ productId: current.value.id, quantity: quantity.value }],
      receiver: receiver.value.trim()
    })
    ElMessage.success('下单成功！平台担保交易，已模拟支付')
    checkoutVisible.value = false
    router.push('/orders')
  } finally {
    ordering.value = false
  }
}

load()
</script>

<style scoped>
.alt-wrap {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--xlvq-border);
}
.alt-title {
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 8px;
}
.alt-group {
  margin-bottom: 10px;
}
.alt-sub {
  font-size: 12px;
  color: var(--xlvq-text-sub);
  margin-bottom: 6px;
}
.alt-item {
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.alt-item:hover {
  border-color: var(--xlvq-primary);
  background: #f7faff;
}
.alt-other {
  opacity: 0.9;
  background: #fafbfd;
}
.alt-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.alt-name {
  font-weight: 600;
  font-size: 13px;
}
.alt-price {
  color: #f5222d;
  font-weight: 800;
}
.alt-reason {
  font-size: 12px;
  line-height: 1.6;
  margin-top: 2px;
}
</style>
