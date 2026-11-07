<template>
  <div class="page-wrap">
    <div class="flex-between mb-16" style="flex-wrap: wrap; gap: 8px">
      <h2 class="page-title" style="margin: 0">🏪 商家中心</h2>
      <span class="sub-text">纯商业合作方 · 开店经营 · 广告投放 · 交易管理 · 评价管理</span>
    </div>

    <!-- 未入驻 / 审核中 -->
    <div v-if="!merchant" class="x-card" v-loading="loading" style="max-width: 640px">
      <el-empty description="你还没有入驻学旅圈商城">
        <template #image>
          <div style="font-size: 56px">🏪</div>
        </template>
        <el-button type="primary" @click="applyVisible = true">申请商家入驻</el-button>
      </el-empty>
    </div>

    <div v-else-if="merchant.status !== 'APPROVED'" class="x-card" style="max-width: 640px">
      <el-result
        :icon="merchant.status === 'PENDING' ? 'warning' : 'error'"
        :title="merchant.status === 'PENDING' ? '入驻申请审核中' : '入驻申请未通过'"
        :sub-title="merchant.status === 'PENDING'
          ? '平台将在1-3个工作日内完成资质审核，请耐心等待'
          : merchant.reason || '资质材料不符合要求，可修改后重新提交'"
      >
        <template v-if="merchant.status === 'REJECTED'" #extra>
          <el-button type="primary" @click="openApplyAgain">重新提交申请</el-button>
        </template>
      </el-result>
    </div>

    <!-- 已入驻 -->
    <template v-else>
      <!-- 店铺信息 + 经营概览 -->
      <div class="x-card mb-16">
        <div class="flex-between" style="flex-wrap: wrap; gap: 10px">
          <div>
            <h3 style="margin: 0 0 6px">
              {{ merchant.shopName }}
              <el-tag type="success" size="small" class="ml-8">已认证商家</el-tag>
              <el-tag
                v-if="agencyLabel(merchant.agencyType)"
                size="small"
                type="warning"
                effect="plain"
                class="ml-8"
              >
                {{ agencyLabel(merchant.agencyType) }}
              </el-tag>
            </h3>
            <div class="sub-text">主营资质：{{ merchant.qualification }}</div>
            <div v-if="merchant.licenseNo" class="sub-text">
              旅行社资质编号：{{ merchant.licenseNo }}
            </div>
            <div class="sub-text">店铺介绍：{{ merchant.description }}</div>
          </div>
        </div>

        <div class="biz-kpi">
          <div v-for="k in bizKpis" :key="k.label" class="biz-kpi-item">
            <div class="biz-kpi-num">{{ k.value }}</div>
            <div class="sub-text">{{ k.label }}</div>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ① 开店经营：商品管理 -->
        <el-tab-pane label="商品管理" name="products">
          <!-- 选品建议：把「数据洞察」里的主题选品结论送到真正需要它的人面前。
               仅商家可见——导游不上架商品，「品类 / 在售商品数」这套口径对它没有意义 -->
          <div
            v-if="isMerchant && (pick.suggestions.length || pick.loading)"
            class="x-card mb-16"
            v-loading="pick.loading"
          >
            <div class="card-title">
              💡 选品建议
              <span class="sub-text" style="margin-left: auto; font-weight: normal">
                基于{{ pick.tripCount }}支结伴队伍的兴趣标签，按你店铺主营品类排序
                <router-link to="/insight?tab=demand" class="pick-more">查看完整分析 →</router-link>
              </span>
            </div>

            <div v-if="!pick.loading && !pick.suggestions.length" class="sub-text">
              暂无足够的需求样本，等平台上结伴队伍多起来后会给出建议
            </div>

            <template v-else>
              <div v-for="s in pick.suggestions" :key="s.category" class="pick-row">
                <div class="pick-head">
                  <el-tag size="small" type="primary" effect="plain">{{ s.category }}</el-tag>
                  <span v-if="s.mine" class="pick-own">你正在经营</span>
                  <span v-else class="pick-new">尚未上架</span>
                  <span class="sub-text" style="margin-left: auto">
                    {{ s.tags.map((t) => t.tag + ' ' + Math.round(t.ratio * 100) + '%').join(' · ') }}
                  </span>
                </div>
                <div class="pick-bar">
                  <div class="pick-bar-fill" :style="{ width: Math.min(100, s.ratio * 100) + '%' }" />
                </div>
                <div class="pick-foot sub-text">
                  相关队伍{{ s.count }}支（占全部队伍的{{ Math.round(s.ratio * 100) }}%）
                  <span v-if="s.productCount === 0" class="pick-gap">· 平台该品类暂无在售商品，建议优先补货</span>
                  <span v-else>· 平台已有{{ s.productCount }}件在售</span>
                  <el-button
                    v-if="s.productCount === 0"
                    link
                    type="primary"
                    size="small"
                    style="margin-left: 8px"
                    @click="openProduct(null, s.category)"
                  >
                    去上架
                  </el-button>
                </div>
              </div>
            </template>
          </div>

          <div class="x-card">
            <div class="card-title">
              我上架的商品
              <el-button
                size="small"
                type="primary"
                style="margin-left: auto"
                :icon="Plus"
                @click="openProduct()"
              >
                新增商品
              </el-button>
            </div>
            <el-table :data="products" v-loading="productLoading" size="small" empty-text="还没有上架商品">
              <el-table-column label="图片" width="80">
                <template #default="{ row }">
                  <el-image :src="row.image" style="width: 48px; height: 48px; border-radius: 6px" fit="cover" />
                </template>
              </el-table-column>
              <el-table-column label="商品名称" prop="name" min-width="160" show-overflow-tooltip />
              <el-table-column label="分类" prop="category" width="110" />
              <el-table-column label="价格" width="90">
                <template #default="{ row }">¥{{ row.price }}</template>
              </el-table-column>
              <el-table-column label="库存" prop="stock" width="80" />
              <el-table-column label="销量" width="80">
                <template #default="{ row }">{{ row.sales ?? row.sold ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openProduct(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 销售分析：交易量 + 季度高峰 + 品类结构 + 备货建议 -->
        <el-tab-pane label="销售分析" name="sales" :lazy="true">
          <MerchantSalesPanel />
        </el-tab-pane>

        <!-- ② 广告投放 -->
        <el-tab-pane label="投放中心" name="ads">
          <div class="x-card mb-12">
            <div class="card-title">
              📣 新建投放
              <span class="sub-text" style="margin-left: auto">提交后由平台审核，通过后按投放时段展示</span>
            </div>
            <el-form :model="adForm" label-width="90px" style="max-width: 560px">
              <el-form-item label="广告位" required>
                <el-select v-model="adForm.slot" style="width: 100%">
                  <el-option v-for="s in AD_SLOTS" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="投放标题" required>
                <el-input v-model="adForm.title" placeholder="如：秋冬户外装备5折起" />
              </el-form-item>
              <el-form-item label="图片 URL">
                <el-input v-model="adForm.image" placeholder="广告图片地址" />
              </el-form-item>
              <el-form-item label="跳转链接">
                <el-input v-model="adForm.link" placeholder="站内路径 /posts/1或外部 https://" />
              </el-form-item>
              <el-form-item label="定向城市">
                <el-input v-model="adForm.targetCity" placeholder="选填，如：长沙" />
              </el-form-item>
              <div class="flex gap-8">
                <el-form-item label="投放预算" style="flex: 1">
                  <el-input-number v-model="adForm.price" :min="0" :step="100" style="width: 100%" />
                </el-form-item>
                <el-form-item label="日期" style="flex: 1.4">
                  <el-date-picker
                    v-model="adRange"
                    type="daterange"
                    value-format="YYYY-MM-DD"
                    start-placeholder="开始"
                    end-placeholder="结束"
                    style="width: 100%"
                  />
                </el-form-item>
              </div>
              <el-form-item>
                <el-button type="primary" :loading="adSaving" @click="submitAd">提交投放申请</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div class="x-card">
            <div class="card-title">我的广告位</div>
            <el-table :data="ads" v-loading="adLoading" size="small" empty-text="还没有投放记录">
              <el-table-column label="广告位" width="110">
                <template #default="{ row }">{{ AD_SLOT_MAP[row.slot] || row.slot }}</template>
              </el-table-column>
              <el-table-column label="标题" prop="title" min-width="150" show-overflow-tooltip />
              <el-table-column label="定向城市" width="100">
                <template #default="{ row }">{{ row.targetCity || '不限' }}</template>
              </el-table-column>
              <el-table-column label="预算" width="100">
                <template #default="{ row }">¥{{ row.price }}</template>
              </el-table-column>
              <el-table-column label="曝光" prop="viewCount" width="80" />
              <el-table-column label="点击" prop="clickCount" width="80" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="AD_STATUS_TAG[row.status] || 'info'">
                    {{ AD_STATUS_MAP[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ③ 交易管理 -->
        <el-tab-pane label="交易订单" name="orders">
          <div class="x-card">
            <div class="card-title">
              📦 店铺订单
              <span class="sub-text" style="margin-left: auto">买家确认收货后进入平台结算</span>
            </div>
            <el-table :data="orders" v-loading="orderLoading" size="small" empty-text="暂无店铺订单">
              <el-table-column label="订单号" prop="id" width="90" />
              <el-table-column label="商品" min-width="180">
                <template #default="{ row }">
                  <div v-for="(it, i) in row.items || []" :key="i">
                    {{ it.name }} × {{ it.quantity }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="买家收货信息" prop="receiver" min-width="150" show-overflow-tooltip />
              <el-table-column label="金额" width="100">
                <template #default="{ row }">
                  <span style="color: #f5222d; font-weight: 700">¥{{ row.amount }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="ORDER_STATUS_TAG[row.status] || 'info'">
                    {{ ORDER_STATUS_MAP[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="结算" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.settled ? 'success' : 'info'" effect="plain">
                    {{ row.settled ? '已结算' : '待结算' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button
                    v-if="row.status === 'PAID'"
                    link
                    type="primary"
                    size="small"
                    @click="ship(row)"
                  >
                    发货
                  </el-button>
                  <span v-else class="sub-text">—</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ④ 评价管理 -->
        <el-tab-pane label="评价管理" name="reviews">
          <div class="x-card mb-12">
            <div class="biz-kpi">
              <div class="biz-kpi-item">
                <div class="biz-kpi-num">{{ reviewStat.total || 0 }}</div>
                <div class="sub-text">累计评价</div>
              </div>
              <div class="biz-kpi-item">
                <div class="biz-kpi-num">{{ reviewStat.avgRating || 0 }}</div>
                <div class="sub-text">平均星级</div>
              </div>
              <div class="biz-kpi-item">
                <div class="biz-kpi-num">{{ reviewStat.pendingReply || 0 }}</div>
                <div class="sub-text">待回复</div>
              </div>
            </div>
          </div>

          <div class="x-card">
            <div class="card-title">
              💬 买家评价
              <span class="sub-text" style="margin-left: auto">回复内容会展示给所有买家</span>
            </div>
            <el-empty v-if="!reviews.length && !reviewLoading" description="还没有收到评价" />
            <div v-loading="reviewLoading" class="section-stack">
              <div v-for="r in reviews" :key="r.id" class="review-item">
                <div class="flex-between" style="flex-wrap: wrap; gap: 8px">
                  <div class="flex-center gap-8">
                    <el-avatar :size="30" :src="r.avatar">{{ r.nickname?.charAt(0) }}</el-avatar>
                    <span style="font-weight: 600">{{ r.nickname || '匿名用户' }}</span>
                    <el-rate :model-value="r.rating || 0" disabled size="small" />
                  </div>
                  <span class="sub-text">{{ formatTime(r.createdAt) }}</span>
                </div>
                <div class="mt-8">商品：{{ r.productName || ('#' + r.productId) }}</div>
                <div class="mt-8">{{ r.content || '（买家未填写评价内容）' }}</div>
                <div v-if="r.reply" class="review-reply">
                  <b>商家回复：</b>{{ r.reply }}
                </div>
                <div v-else class="mt-8">
                  <el-input
                    v-model="replyDraft[r.id]"
                    size="small"
                    placeholder="输入回复内容"
                    style="max-width: 460px"
                  />
                  <el-button
                    size="small"
                    type="primary"
                    style="margin-left: 8px"
                    :loading="replyingId === r.id"
                    @click="submitReply(r)"
                  >
                    回复
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 入驻申请 -->
    <el-dialog v-model="applyVisible" title="商家入驻申请" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="店铺名称" prop="shopName">
          <el-input v-model="form.shopName" placeholder="如：山友户外装备社" />
        </el-form-item>
        <!-- 三层供给：商家类型，旅行社属于第 ③ 层 -->
        <el-form-item label="商家类型">
          <el-select v-model="form.agencyType" placeholder="选择商家类型" style="width: 100%">
            <el-option
              v-for="t in AGENCY_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.agencyType === 'TRAVEL_AGENCY'" label="旅行社资质">
          <el-input v-model="form.licenseNo" placeholder="旅行社业务经营许可证编号" />
        </el-form-item>
        <el-form-item label="资质材料" prop="qualification">
          <el-input
            v-model="form.qualification"
            type="textarea"
            :rows="3"
            placeholder="填写营业执照/经营范围/品牌授权等资质说明，或资质文件 URL"
          />
        </el-form-item>
        <el-form-item label="店铺介绍" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="主营商品与服务介绍" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 商品编辑 -->
    <el-dialog
      v-model="productVisible"
      :title="productForm.id ? '编辑商品' : '新增商品'"
      width="520px"
    >
      <el-form :model="productForm" label-width="84px">
        <el-form-item label="商品名称" required>
          <el-input v-model="productForm.name" placeholder="商品名称" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="productForm.category" style="width: 100%">
            <el-option v-for="c in PRODUCT_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <div class="flex gap-8">
          <el-form-item label="价格" style="flex: 1">
            <el-input-number v-model="productForm.price" :min="0" :precision="2" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item label="库存" style="flex: 1">
            <el-input-number v-model="productForm.stock" :min="0" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="图片 URL">
          <el-input v-model="productForm.image" placeholder="商品图片地址" />
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input v-model="productForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productVisible = false">取消</el-button>
        <el-button type="primary" :loading="productSaving" @click="submitProduct">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { adApi, demandApi, merchantApi, orderApi, productApi, productReviewApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import MerchantSalesPanel from '@/views/mall/MerchantSalesPanel.vue'
import {
  AD_SLOTS,
  AD_SLOT_MAP,
  AD_STATUS_MAP,
  AD_STATUS_TAG,
  ORDER_STATUS_MAP,
  ORDER_STATUS_TAG,
  PRODUCT_CATEGORIES,
  formatTime
} from '@/utils/format'

const route = useRoute()
const auth = useAuthStore()
/** 选品建议只对商家有意义：导游/管理员不进商品供给这条线 */
const isMerchant = computed(() => auth.isMerchant)

const loading = ref(false)
const saving = ref(false)
const merchant = ref(null)
const applyVisible = ref(false)
const formRef = ref()

const activeTab = ref('products')

const form = ref({ shopName: '', qualification: '', description: '', agencyType: 'OUTDOOR_SHOP', licenseNo: '' })

/** 商家类型，取值与后端MerchantController.AGENCY_TYPES白名单一致 */
const AGENCY_TYPES = [
  { value: 'TRAVEL_AGENCY', label: '旅行社（平台第 ③ 层供给）' },
  { value: 'OUTDOOR_SHOP', label: '户外装备店' },
  { value: 'HOSTEL', label: '民宿 / 客栈' },
  { value: 'OTHER', label: '其他' }
]

/** 商家类型展示名（不含OTHER，避免给普通店铺加无意义标签） */
function agencyLabel(code) {
  const hit = AGENCY_TYPES.find((t) => t.value === code)
  return hit && code !== 'OTHER' ? hit.label.replace('（平台第 ③ 层供给）', '') : ''
}
const rules = {
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  qualification: [{ required: true, message: '请填写资质材料', trigger: 'blur' }],
  description: [{ required: true, message: '请填写店铺介绍', trigger: 'blur' }]
}

/* ---------- 商品 ---------- */
const products = ref([])
const productLoading = ref(false)
const productVisible = ref(false)
const productSaving = ref(false)
const productForm = ref({})

/* ---------- 投放 ---------- */
const ads = ref([])
const adLoading = ref(false)
const adSaving = ref(false)
const adRange = ref([])
const adForm = ref({ slot: 'BANNER', title: '', image: '', link: '', targetCity: '', price: 0 })

/* ---------- 交易 ---------- */
const orders = ref([])
const orderLoading = ref(false)

/* ---------- 评价 ---------- */
const reviews = ref([])
const reviewStat = ref({})
const reviewLoading = ref(false)
const replyDraft = ref({})
const replyingId = ref(null)

const bizKpis = computed(() => [
  { label: '上架商品', value: products.value.length },
  { label: '待发货', value: orders.value.filter((o) => o.status === 'PAID').length },
  { label: '累计订单', value: orders.value.length },
  { label: '在投广告', value: ads.value.filter((a) => a.status === 'APPROVED').length }
])

/* ---------- 选品建议（复用/api/demand/insight的themeDemand） ---------- */

const pick = ref({ loading: false, tripCount: 0, suggestions: [] })

/**
 * 把后端的「兴趣标签 → 建议品类」聚合成「品类」维度的建议。
 * 同一品类可能对应多个标签（徒步/登山/露营 → 户外装备），先按品类合并队伍数，
 * 再按「是否与我的主营品类一致」优先、缺口次之、热度再次排序。
 */
async function loadPickSuggestions() {
  // 导游视角没有「商品品类/在售商品数」这套口径，不必发请求
  if (!isMerchant.value) return
  pick.value.loading = true
  try {
    const data = await demandApi.insight().catch(() => null)
    const themeDemand = data?.themeDemand || []
    // 分母：后端ratio = 该标签队伍数/总队伍数，用任意一条反推出总队伍数
    const first = themeDemand.find((t) => (t.count || 0) > 0 && (t.ratio || 0) > 0)
    const totalTrips = first ? Math.round(first.count / first.ratio) : 0

    const byCategory = new Map()
    for (const t of themeDemand) {
      const cat = t.categoryHint || '旅行日用'
      if (!byCategory.has(cat)) {
        byCategory.set(cat, { category: cat, count: 0, tags: [], productCount: t.productCount ?? 0 })
      }
      const item = byCategory.get(cat)
      item.tags.push({ tag: t.tag, count: t.count, ratio: t.ratio })
      // 一支队伍可能同时打「徒步」「登山」两个标签，都会被计入各自标签数，
      // 直接求和会重复计数，故取最大值作为该品类的队伍数下界
      item.count = Math.max(item.count, t.count || 0)
    }

    const myCategories = new Set(products.value.map((p) => p.category).filter(Boolean))
    const list = [...byCategory.values()].map((s) => ({
      ...s,
      tags: s.tags.sort((a, b) => b.count - a.count),
      ratio: totalTrips ? s.count / totalTrips : 0,
      mine: myCategories.has(s.category)
    }))

    list.sort((a, b) => {
      if (a.mine !== b.mine) return a.mine ? -1 : 1
      const gapA = a.productCount === 0 ? 1 : 0
      const gapB = b.productCount === 0 ? 1 : 0
      if (gapA !== gapB) return gapB - gapA
      return b.count - a.count
    })

    pick.value.tripCount = totalTrips
    pick.value.suggestions = list.slice(0, 4)
  } finally {
    pick.value.loading = false
  }
}

async function loadMerchant() {
  loading.value = true
  try {
    const data = await merchantApi.mine().catch(() => null)
    merchant.value = data && Object.keys(data).length ? data : null
    if (merchant.value?.status === 'APPROVED') {
      const want = route.query.tab
      if (want === 'ads' || want === 'reviews' || want === 'orders' || want === 'products' || want === 'sales') {
        activeTab.value = want
      }
      loadTab(activeTab.value)
    }
  } finally {
    loading.value = false
  }
}

function onTabChange(name) {
  loadTab(name)
}

function loadTab(name) {
  if (name === 'products') loadProducts()
  else if (name === 'ads') loadAds()
  else if (name === 'orders') loadOrders()
  else if (name === 'reviews') loadReviews()
}

async function loadProducts() {
  productLoading.value = true
  try {
    const data = await productApi.list({ merchantId: merchant.value.id }).catch(() => [])
    products.value = data?.list || data || []
  } finally {
    productLoading.value = false
  }
  // 选品建议要拿「我已上架的品类」做对照，故放在商品加载之后
  loadPickSuggestions()
}

async function loadAds() {
  adLoading.value = true
  try {
    ads.value = (await adApi.mine().catch(() => [])) || []
  } finally {
    adLoading.value = false
  }
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const data = await orderApi.shop().catch(() => [])
    // 后端返回 [{ order, items }]，拍平后模板可直接读订单字段
    const list = data?.list || data || []
    orders.value = list.map((x) => (x && x.order ? { ...x.order, items: x.items || [] } : x))
  } finally {
    orderLoading.value = false
  }
}

async function loadReviews() {
  reviewLoading.value = true
  try {
    const data = await productReviewApi.merchant().catch(() => null)
    reviews.value = data?.list || []
    reviewStat.value = data || {}
  } finally {
    reviewLoading.value = false
  }
}

function openApplyAgain() {
  form.value = {
    shopName: merchant.value.shopName || '',
    qualification: '',
    description: merchant.value.description || ''
  }
  applyVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    await merchantApi.apply({ ...form.value })
    ElMessage.success('申请已提交，请等待平台审核')
    applyVisible.value = false
    loadMerchant()
  } finally {
    saving.value = false
  }
}

function openProduct(row, presetCategory) {
  productForm.value = row
    ? { ...row }
    : {
        name: '',
        category: presetCategory || PRODUCT_CATEGORIES[0],
        price: 0,
        stock: 0,
        image: '',
        description: '',
        merchantId: merchant.value.id
      }
  productVisible.value = true
}

async function submitProduct() {
  if (!productForm.value.name) {
    ElMessage.warning('请填写商品名称')
    return
  }
  productSaving.value = true
  try {
    const payload = { ...productForm.value, merchantId: merchant.value.id }
    if (payload.id) {
      await productApi.update(payload.id, payload)
    } else {
      await productApi.create(payload)
    }
    ElMessage.success('商品已保存')
    productVisible.value = false
    loadProducts()
  } finally {
    productSaving.value = false
  }
}

async function submitAd() {
  if (!adForm.value.title) {
    ElMessage.warning('请填写投放标题')
    return
  }
  adSaving.value = true
  try {
    const [startAt, endAt] = adRange.value || []
    await adApi.create({ ...adForm.value, startAt, endAt })
    ElMessage.success('投放申请已提交，等待平台审核')
    adForm.value = { slot: 'BANNER', title: '', image: '', link: '', targetCity: '', price: 0 }
    adRange.value = []
    loadAds()
  } finally {
    adSaving.value = false
  }
}

async function ship(o) {
  await ElMessageBox.confirm('确认该订单已发货？', '发货确认', { type: 'warning' })
  await orderApi.ship(o.id)
  ElMessage.success('发货成功')
  loadOrders()
}

async function submitReply(r) {
  const text = (replyDraft.value[r.id] || '').trim()
  if (!text) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replyingId.value = r.id
  try {
    await productReviewApi.reply(r.id, text)
    ElMessage.success('回复已发布')
    replyDraft.value[r.id] = ''
    loadReviews()
  } finally {
    replyingId.value = null
  }
}

onMounted(loadMerchant)
</script>

<style scoped>
.ml-8 {
  margin-left: 8px;
}
.biz-kpi {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-top: 14px;
}
.biz-kpi-item {
  background: var(--xlvq-bg);
  border-radius: 10px;
  padding: 12px;
  text-align: center;
}
.biz-kpi-num {
  font-size: 20px;
  font-weight: 800;
  color: var(--xlvq-primary);
}
.review-item {
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  padding: 12px;
}
.review-reply {
  margin-top: 8px;
  padding: 8px 10px;
  background: var(--xlvq-bg);
  border-radius: 8px;
  color: var(--xlvq-text-sub);
}
/* 选品建议 */
.pick-row {
  padding: 10px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.pick-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.pick-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.pick-own {
  font-size: 12px;
  color: var(--xlvq-primary);
  background: var(--xlvq-primary-soft, rgba(64, 158, 255, 0.12));
  border-radius: 4px;
  padding: 1px 6px;
}
.pick-new {
  font-size: 12px;
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.12);
  border-radius: 4px;
  padding: 1px 6px;
}
.pick-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--xlvq-bg);
  overflow: hidden;
}
.pick-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--xlvq-primary), #79bbff);
  transition: width 0.4s ease;
}
.pick-foot {
  margin-top: 5px;
}
.pick-gap {
  color: #e6a23c;
}
.pick-more {
  margin-left: 10px;
  color: var(--xlvq-primary);
  text-decoration: none;
}
.pick-more:hover {
  text-decoration: underline;
}
</style>
