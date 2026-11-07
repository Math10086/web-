<template>
  <div class="page-wrap" v-loading="loading">
    <!-- 用户卡 -->
    <div class="x-card mb-16" v-if="profile.id">
      <div class="flex gap-16" style="align-items: center; flex-wrap: wrap">
        <el-avatar :size="84" :src="profile.avatar">
          {{ profile.nickname?.charAt(0) }}
        </el-avatar>
        <div style="flex: 1; min-width: 240px">
          <div style="font-size: 22px; font-weight: 800">
            {{ profile.nickname }}
            <el-tag v-if="profile.verified" type="success" class="ml-8">
              ✓ {{ SCHOOL_MAP[profile.school] || profile.school }}学籍认证
            </el-tag>
            <el-tag v-else-if="isSelf" type="info" effect="plain" class="ml-8">
              未完成学籍认证
            </el-tag>
            <el-tag type="warning" class="ml-8">{{ ROLE_MAP[profile.role] || '普通用户' }}</el-tag>
          </div>
          <div class="sub-text mt-12">@{{ profile.username }} ｜ 加入于{{ formatDate(profile.createdAt) }}</div>
          <div class="sub-text mt-12" v-if="archiveText">
            🎓 {{ archiveText }}
          </div>
          <div class="mt-12" v-if="interestList.length">
            <el-tag
              v-for="t in interestList"
              :key="t"
              size="small"
              class="ml-8"
              type="info"
              effect="plain"
            >
              {{ t }}
            </el-tag>
          </div>
          <div class="sub-text mt-12" v-if="profile.bio">{{ profile.bio }}</div>
        </div>

        <div style="width: 230px">
          <!-- 商家为纯商业合作方：不展示结伴信誉分与博主等级，改为经营入口 -->
          <template v-if="auth.isMerchant">
            <div class="flex-between mb-12">
              <span class="sub-text">平台身份</span>
              <strong>认证商家</strong>
            </div>
            <div class="sub-text" style="line-height: 1.9">
              纯商业合作方：可开店经营、投放广告位、处理店铺订单与回复商品评价。
            </div>
            <el-button
              class="mt-12"
              size="small"
              type="primary"
              style="width: 100%"
              @click="$router.push('/merchant')"
            >
              进入商家中心
            </el-button>
          </template>
          <template v-else>
            <div class="flex-between mb-12">
              <span class="sub-text">信誉分（结伴履约）</span>
              <strong>{{ profile.creditScore ?? 100 }}</strong>
            </div>
            <el-tag size="small" :type="creditTagType(profile.creditScore)" class="mb-12">
              🏅 {{ creditLabel }}
            </el-tag>
            <el-progress
              :percentage="creditPercent"
              :color="creditColor"
              :stroke-width="10"
            />
            <el-button
              v-if="isSelf"
              class="mt-12"
              size="small"
              style="width: 100%"
              @click="$router.push('/credit')"
            >
              查看信誉主页 · 徽章与申诉
            </el-button>
            <el-button
              v-if="isSelf"
              type="warning"
              plain
              size="small"
              style="width: 100%"
              @click="$router.push('/portrait')"
            >
              我的出行画像 · 标签与消费
            </el-button>
            <div class="flex-between mt-12">
              <span class="sub-text">博主等级 / 博主分</span>
              <strong>Lv.{{ profile.bloggerLevel ?? 0 }} · {{ profile.bloggerScore ?? 0 }}</strong>
            </div>
          </template>
          <div v-if="!isSelf" class="mt-12" style="text-align: right">
            <div class="sub-text mb-12">
              公开发帖{{ otherStats.postCount }}篇 · 累计浏览{{ otherStats.totalViews }}
            </div>
            <el-button
              size="small"
              type="primary"
              @click="$router.push(`/messages?userId=${profile.id}`)"
            >
              私信
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 本人：编辑与设置 -->
    <el-tabs v-if="isSelf" v-model="tab">
      <el-tab-pane label="资料编辑" name="edit">
        <div class="x-card" style="max-width: 680px">
          <el-form :model="editForm" label-width="96px">
            <el-form-item label="头像">
              <el-avatar :size="64" :src="editForm.avatar">{{ editForm.nickname?.charAt(0) }}</el-avatar>
              <el-input v-model="editForm.avatar" placeholder="头像图片 URL" class="ml-8" style="flex: 1" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="editForm.nickname" maxlength="16" />
            </el-form-item>
            <el-form-item label="兴趣标签">
              <el-input v-model="editForm.interests" placeholder="如：美食、徒步、摄影，逗号分隔" />
            </el-form-item>
            <!-- 头像信息卡展示字段 -->
            <el-form-item label="学院">
              <el-input v-model="editForm.college" placeholder="如：计算机学院" />
            </el-form-item>
            <el-form-item label="专业">
              <el-input v-model="editForm.major" placeholder="如：软件工程" />
            </el-form-item>
            <el-form-item label="届别">
              <el-input v-model="editForm.grade" placeholder="入学年份，如2023" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input v-model="editForm.bio" type="textarea" :rows="3" maxlength="200" show-word-limit />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
              <!-- 出行标签档案（节奏、兴趣、预算、目的地等）在「找搭子」页维护 -->
              <el-button plain @click="$router.push('/trips?tab=match')">编辑出行标签档案 →</el-button>
            </el-form-item>
          </el-form>

          <el-divider>三校学籍认证</el-divider>

          <template v-if="profile.verified">
            <el-result icon="success" title="已完成三校学籍认证" :sub-title="`${SCHOOL_MAP[profile.school]} · 学号${profile.studentNo || ''}`" />
          </template>
          <template v-else>
            <el-alert
              type="warning"
              :closable="false"
              class="mb-12"
              title="完成三校学籍认证后，才可报名结伴、发布内容与参与平台交易"
              show-icon
            />
            <el-form :model="verifyForm" label-width="96px" style="max-width: 520px">
              <el-form-item label="真实姓名" required>
                <el-input v-model="verifyForm.realName" placeholder="与学生证一致的真实姓名" />
              </el-form-item>
              <el-form-item label="所学学校" required>
                <el-select v-model="verifyForm.school" style="width: 100%">
                  <el-option label="中南大学" value="CSU" />
                  <el-option label="湖南师范大学" value="HUNNU" />
                  <el-option label="湖南大学" value="HNU" />
                </el-select>
              </el-form-item>
              <el-form-item label="学号" required>
                <el-input v-model="verifyForm.studentNo" :placeholder="STUDENT_NO_HINT[verifyForm.school]" />
              </el-form-item>
              <el-form-item label="学院">
                <el-input v-model="verifyForm.college" placeholder="如：计算机学院" />
              </el-form-item>
              <el-form-item label="专业">
                <el-input v-model="verifyForm.major" placeholder="如：软件工程" />
              </el-form-item>
              <el-form-item label="届别">
                <el-input v-model="verifyForm.grade" placeholder="入学年份，如2023" />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" :loading="saving" @click="doVerify">提交学籍认证</el-button>
                <span class="sub-text ml-8">学号格式校验通过即自动完成认证，无需等待人工审核</span>
              </el-form-item>
            </el-form>
          </template>

          <template v-if="auth.isBusiness">
            <el-divider>博主申请</el-divider>
            <div class="flex-between" style="max-width: 520px">
              <span class="sub-text">
                攻略产出优质、被引用与点赞收藏较多的同学可申请成为攻略博主（Lv 评级与精品推荐）
              </span>
              <el-button
                type="primary"
                plain
                :disabled="profile.role === 'BLOGGER' || profile.role === 'GUIDE'"
                :loading="saving"
                @click="applyBlogger"
              >
                {{ profile.role === 'BLOGGER' ? '已是博主' : '申请成为博主' }}
              </el-button>
            </div>
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="!auth.isMerchant" label="我的攻略" name="posts">
        <div class="flex-between mb-12">
          <h3 style="margin: 0">我发布的帖子</h3>
          <el-button type="primary" @click="$router.push('/posts/new')">去发帖</el-button>
        </div>
        <el-empty v-if="!myPosts.length" description="还没有发布过帖子" />
        <div class="post-grid">
          <PostCard v-for="p in myPosts" :key="p.id" :post="p" />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="auth.isBusiness" label="我的足迹" name="footprints">
        <div class="flex-between mb-12">
          <h3 style="margin: 0">我的旅行足迹</h3>
          <el-button type="primary" @click="$router.push('/footprint')">打开足迹地图</el-button>
        </div>
        <el-table :data="myFootprints" size="small" empty-text="还没有足迹">
          <el-table-column label="照片" width="76">
            <template #default="{ row }">
              <el-image v-if="row.photo" :src="row.photo" style="width: 52px; height: 52px; border-radius: 8px" fit="cover" />
              <span v-else style="font-size: 22px">📍</span>
            </template>
          </el-table-column>
          <el-table-column label="地点" prop="name" min-width="140" />
          <el-table-column label="城市" prop="city" width="100" />
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ formatDate(row.visitedAt) }}</template>
          </el-table-column>
          <el-table-column label="备注" prop="note" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="auth.isBusiness" label="紧急联系人与保险" name="safety">
        <div class="section-stack" style="max-width: 720px">
          <div class="x-card">
            <div class="card-title">🛡️ 紧急联系人（家校知情）</div>
            <div v-for="c in contacts" :key="c.id" class="safety-row">
              <div>
                <strong>{{ c.name }}</strong>
                <el-tag size="small" class="ml-8" :type="c.relation === '辅导员' ? 'warning' : 'primary'">
                  {{ c.relation }}
                </el-tag>
                <el-tag size="small" class="ml-8" :type="c.authorized ? 'success' : 'info'">
                  {{ c.authorized ? '已授权位置' : '未授权' }}
                </el-tag>
                <div class="sub-text">📞 {{ c.phone }}</div>
              </div>
              <el-switch :model-value="!!c.authorized" @change="(v) => toggleAuth(c, v)" />
            </div>
            <el-empty v-if="!contacts.length" description="未设置紧急联系人，建议在结伴行程中添加" :image-size="60" />
          </div>

          <div class="x-card">
            <div class="card-title">🧾 我的保险</div>
            <el-table :data="insurances" size="small" empty-text="还没有购买保险">
              <el-table-column label="险种" prop="planName" min-width="140" />
              <el-table-column label="关联行程" min-width="120">
                <template #default="{ row }">{{ row.tripTitle || row.tripId || '—' }}</template>
              </el-table-column>
              <el-table-column label="投保时间" width="170">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="消息与订单" name="links">
        <div class="x-card quick-grid">
          <div class="quick-item" @click="$router.push('/messages')">💬<span>我的消息</span></div>
          <div class="quick-item" @click="$router.push('/orders')">📦<span>我的订单</span></div>
          <div v-if="auth.isBusiness" class="quick-item" @click="$router.push('/trips/my')">
            🧳<span>我的结伴</span>
          </div>
          <div v-if="auth.isBusiness" class="quick-item" @click="$router.push('/footprint')">
            📷<span>我的足迹</span>
          </div>
          <div v-if="auth.isBusiness" class="quick-item" @click="$router.push('/expenses')">
            💰<span>消费报表</span>
          </div>
          <!-- 导游接单仅认证导游账号可见；普通用户/博主看自己「预约过的导游订单」 -->
          <div v-if="auth.isGuide" class="quick-item" @click="$router.push('/guide-bookings?tab=guide')">
            🎫<span>导游接单</span>
          </div>
          <div
            v-else-if="auth.isBusiness"
            class="quick-item"
            @click="$router.push('/guide-bookings?tab=traveler')"
          >
            🧭<span>导游预约</span>
          </div>
          <div v-if="auth.isMerchant" class="quick-item" @click="$router.push('/merchant')">
            🏪<span>商家中心</span>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 他人主页 -->
    <template v-else>
      <div class="section-stack">
        <div class="x-card">
          <h3 style="margin: 0 0 12px">TA 的攻略</h3>
          <el-empty v-if="!myPosts.length" description="TA 还没有公开发帖" />
          <div class="post-grid">
            <PostCard v-for="p in myPosts" :key="p.id" :post="p" />
          </div>
        </div>
        <div class="x-card">
          <h3 style="margin: 0 0 12px">TA 的足迹</h3>
          <el-table :data="myFootprints" size="small" empty-text="足迹未公开或暂无记录">
            <el-table-column label="照片" width="76">
              <template #default="{ row }">
                <el-image v-if="row.photo" :src="row.photo" style="width: 52px; height: 52px; border-radius: 8px" fit="cover" />
                <span v-else style="font-size: 22px">📍</span>
              </template>
            </el-table-column>
            <el-table-column label="地点" prop="name" min-width="140" />
            <el-table-column label="城市" prop="city" width="100" />
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatDate(row.visitedAt) }}</template>
            </el-table-column>
            <el-table-column label="备注" prop="note" min-width="180" show-overflow-tooltip />
          </el-table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PostCard from '@/components/PostCard.vue'
import {
  emergencyApi,
  footprintApi,
  insuranceApi,
  postApi,
  userApi
} from '@/api'
import { useAuthStore } from '@/store/auth'
import {
  creditTagType,
  formatDate,
  formatTime,
  ROLE_MAP,
  SCHOOL_MAP
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const profile = ref({})
const tab = ref('edit')
const myPosts = ref([])
const myFootprints = ref([])
const contacts = ref([])
const insurances = ref([])
/** 他人主页统计：发帖数/累计浏览（来自/users/{id} 的外层统计字段） */
const otherStats = ref({ postCount: 0, totalViews: 0 })

const editForm = ref({ nickname: '', avatar: '', bio: '', interests: '', college: '', major: '', grade: '' })
const verifyForm = ref({
  realName: '',
  studentNo: '',
  school: 'CSU',
  college: '',
  major: '',
  grade: ''
})

/** 头像信息卡上的展示字段：学院、专业、届别 */
const archiveText = computed(() => {
  const p = profile.value
  const parts = []
  if (p.college) parts.push(p.college)
  if (p.major) parts.push(p.major)
  if (p.grade) parts.push(`${p.grade} 级`)
  return parts.join(' · ')
})

/** 信誉标签，分档与后端CreditBadge保持一致 */
const creditLabel = computed(() => {
  const s = Number(profile.value.creditScore ?? 100)
  if (s >= 120) return '金牌守信'
  if (s >= 100) return '诚信用户'
  if (s >= 80) return '信誉良好'
  if (s >= 60) return '信用一般'
  return '信誉警示'
})

/** 各校学号格式提示（与后端xlvq.verify配置一致） */
const STUDENT_NO_HINT = {
  CSU: '中南大学：8位数字，如20230116',
  HUNNU: '湖南师范大学：10位数字，如2022030517',
  HNU: '湖南大学：8位数字，如20220823'
}

const targetId = computed(() =>
  route.query.id ? Number(route.query.id) : auth.user?.id
)
const isSelf = computed(
  () => !route.query.id || Number(route.query.id) === auth.user?.id
)
const interestList = computed(() =>
  String(profile.value.interests || '')
    .split(/[,，、]/)
    .map((s) => s.trim())
    .filter(Boolean)
)
const creditPercent = computed(() =>
  Math.min(100, Math.max(0, Number(profile.value.creditScore ?? 100)))
)
const creditColor = computed(() => {
  const s = creditPercent.value
  if (s >= 80) return '#52C41A'
  if (s >= 60) return '#2F80ED'
  return '#FF8A65'
})

async function load() {
  const id = targetId.value
  if (!id) {
    router.replace({ path: '/login', query: { redirect: '/profile' } })
    return
  }
  loading.value = true
  try {
    if (isSelf.value && auth.user) {
      profile.value = auth.user
    } else {
      // 后端/users/{id} 返回 {user, postCount, totalViews, fans}，内层user才是资料本体
      const res = (await userApi.get(id).catch(() => null)) || {}
      profile.value = res.user || res || {}
      otherStats.value = {
        postCount: res.postCount || 0,
        totalViews: res.totalViews || 0
      }
    }
    if (isSelf.value) {
      editForm.value = {
        nickname: profile.value.nickname || '',
        avatar: profile.value.avatar || '',
        bio: profile.value.bio || '',
        interests: profile.value.interests || '',
        college: profile.value.college || '',
        major: profile.value.major || '',
        grade: profile.value.grade || ''
      }
      verifyForm.value = {
        realName: profile.value.realName || '',
        studentNo: profile.value.studentNo || '',
        school: profile.value.school || 'CSU',
        college: profile.value.college || '',
        major: profile.value.major || '',
        grade: profile.value.grade || ''
      }
      loadSafety()
    }
    // 足迹属C端前台能力，商家为纯商业合作方，不请求该接口
    const fpPromise = auth.isBusiness
      ? isSelf.value
        ? footprintApi.mine()
        : footprintApi.user(id)
      : Promise.resolve([])
    const [postData, fpData] = await Promise.allSettled([
      // 商家已退出社区（后端403），不请求其发帖记录
      auth.isMerchant ? Promise.resolve([]) : postApi.list({ authorId: id, sort: 'latest' }).catch(() => []),
      fpPromise
    ])
    myPosts.value =
      postData.status === 'fulfilled'
        ? postData.value?.list || postData.value || []
        : []
    myFootprints.value =
      fpData.status === 'fulfilled' ? fpData.value?.list || fpData.value || [] : []
  } finally {
    loading.value = false
  }
}

async function loadSafety() {
  // 紧急联系人与保险属家校安全能力（学生用户/博主/导游），商家与管理员不涉及
  if (!auth.isBusiness) {
    contacts.value = []
    insurances.value = []
    return
  }
  const [c, i] = await Promise.allSettled([
    emergencyApi.list(),
    insuranceApi.mine()
  ])
  contacts.value = c.status === 'fulfilled' ? c.value?.list || c.value || [] : []
  insurances.value = i.status === 'fulfilled' ? i.value?.list || i.value || [] : []
}

async function saveProfile() {
  if (!editForm.value.nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    const u = await userApi.updateProfile({ ...editForm.value })
    auth.setUser(u || { ...auth.user, ...editForm.value })
    ElMessage.success('资料已保存')
    load()
  } finally {
    saving.value = false
  }
}

async function doVerify() {
  const f = verifyForm.value
  if (!f.realName || !f.studentNo || !f.school) {
    ElMessage.warning('请填写完整认证信息')
    return
  }
  saving.value = true
  try {
    const u = await userApi.verify({ ...f })
    auth.setUser(u || { ...auth.user, ...f, verified: true })
    ElMessage.success('学籍认证已通过（学号格式校验自动完成）')
    load()
  } finally {
    saving.value = false
  }
}

async function applyBlogger() {
  saving.value = true
  try {
    const u = await userApi.applyBlogger()
    // 后端返回的是角色已变更的完整用户对象：同步回store与localStorage，
    // 否则导航、个人资料、创作者中心入口都还按旧角色渲染，要刷新页面才生效
    if (u) auth.setUser(u)
    ElMessage.success('已开通博主身份，可在「数据洞察 → 创作者中心」查看帖子表现')
  } finally {
    saving.value = false
  }
}

async function toggleAuth(c, v) {
  await emergencyApi.update(c.id, { authorized: v })
  c.authorized = v
  ElMessage.success(v ? '已授权紧急联系人查看实时位置' : '已取消授权')
}

watch(() => route.query.id, load)
onMounted(load)
</script>

<style scoped>
.ml-8 {
  margin-left: 8px;
}
.safety-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.quick-item {
  text-align: center;
  padding: 26px 10px;
  border-radius: 12px;
  background: var(--xlvq-primary-light);
  cursor: pointer;
  font-size: 30px;
  transition: transform 0.15s;
}
.quick-item:hover {
  transform: translateY(-3px);
}
.quick-item span {
  display: block;
  font-size: 13px;
  margin-top: 6px;
  color: var(--xlvq-text);
}
@media (max-width: 700px) {
  .quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
