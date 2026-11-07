<template>
  <div class="page-wrap" v-loading="loading">
    <!-- 我的档案状态 + 撮合偏好 -->
    <div class="x-card mb-16">
      <div class="flex-between" style="align-items: flex-start; flex-wrap: wrap; gap: 16px">
        <div style="flex: 1; min-width: 300px">
          <div class="card-title">我的出行标签档案</div>
          <el-alert
            v-if="!inPool"
            type="warning"
            :closable="false"
            show-icon
            class="mb-12"
            title="还差几个核心标签，填满后才能进入撮合推荐池"
            :description="`未填写：${missingText}`"
          />
          <el-alert
            v-else
            type="success"
            :closable="false"
            show-icon
            class="mb-12"
            title="档案已完整，你已进入撮合推荐池"
          />
          <div v-if="tagList.length">
            <el-tag
              v-for="t in tagList"
              :key="t.dimension + t.code"
              size="small"
              class="ml-8 mb-6"
              :type="coreDims.includes(t.dimension) ? 'primary' : 'info'"
              :effect="coreDims.includes(t.dimension) ? 'light' : 'plain'"
            >
              {{ t.label }}
            </el-tag>
          </div>
          <div v-else class="sub-text">还没有填写任何标签</div>
          <el-button class="mt-12" type="primary" plain size="small" @click="openTagEditor">
            编辑标签档案
          </el-button>
        </div>

        <div style="width: 280px">
          <div class="card-title">撮合偏好</div>
          <div class="pref-row">
            <span class="sub-text">愿意认识陌生人</span>
            <el-switch v-model="pref.openToStranger" @change="savePref" />
          </div>
          <div class="sub-text mb-12">
            {{ pref.openToStranger
              ? '半熟人度不足70的同学也会出现在推荐里'
              : '只推荐半熟人（同校/同院/同届/同圈子/曾同队）' }}
          </div>
          <div class="pref-row">
            <span class="sub-text">接受跨校推荐</span>
            <el-switch v-model="pref.crossSchool" @change="savePref" />
          </div>
        </div>
      </div>
    </div>

    <!-- 推荐列表 -->
    <div class="flex-between mb-12">
      <h3 style="margin: 0">为你推荐的搭子 · 契合度 Top {{ limit }}</h3>
      <el-select v-model="limit" style="width: 130px" @change="load">
        <el-option :value="5" label="看5个" />
        <el-option :value="10" label="看10个" />
        <el-option :value="20" label="看20个" />
      </el-select>
    </div>

    <el-empty
      v-if="!list.length"
      description="暂时没有匹配到合适的搭子：可能你的核心标签还没填满，或还没有半熟人用户完成认证"
    />

    <div class="match-grid">
      <div v-for="p in list" :key="p.targetId" class="x-card match-card" @click="openDetail(p.targetId)">
        <div class="flex gap-12" style="align-items: center">
          <el-avatar :size="52" :src="p.target.avatar">{{ p.target.nickname?.charAt(0) }}</el-avatar>
          <div style="flex: 1; min-width: 0">
            <div class="match-name">
              {{ p.target.nickname }}
              <el-tag v-if="p.target.verified" size="small" type="success" class="ml-8">已认证</el-tag>
            </div>
            <div class="sub-text ellipsis">{{ archiveText(p.target) }}</div>
          </div>
        </div>

        <div class="score-row">
          <div>
            <span class="score-num">{{ p.matchScore }}</span>
            <span class="score-unit">/100契合度</span>
          </div>
          <el-tag size="small" :type="matchTagType(p.matchScore)">推荐分{{ p.recommendScore }}</el-tag>
        </div>
        <el-progress
          :percentage="p.matchScore"
          :show-text="false"
          :stroke-width="8"
          :color="matchColor(p.matchScore)"
        />

        <div class="dim-mini">
          <span v-for="d in p.dims" :key="d.key" class="dim-chip" :title="d.label + '：' + d.note">
            {{ d.label }} {{ d.score }}
          </span>
        </div>

        <div class="reason">
          {{ topReasons(p.dims) }}
        </div>
        <div class="sub-text" style="text-align: right">查看契合度明细 →</div>
      </div>
    </div>

    <!-- 契合度明细 -->
    <el-dialog v-model="detailVisible" title="契合度明细 · 为什么推荐 TA" width="640px">
      <template v-if="detail">
        <div class="flex gap-12 mb-16" style="align-items: center">
          <el-avatar :size="56" :src="detail.target.avatar">{{ detail.target.nickname?.charAt(0) }}</el-avatar>
          <div>
            <div style="font-size: 17px; font-weight: 800">
              {{ detail.target.nickname }}
              <el-tag size="small" type="success" class="ml-8" v-if="detail.target.verified">已认证</el-tag>
            </div>
            <div class="sub-text">{{ archiveText(detail.target) }}</div>
            <div class="sub-text" v-if="detail.target.bio">{{ detail.target.bio }}</div>
          </div>
          <div style="margin-left: auto; text-align: right">
            <div class="score-num">{{ detail.matchScore }}</div>
            <div class="sub-text">契合度</div>
            <el-tag size="small" :type="creditTagType(detail.target.creditScore)">
              信誉{{ detail.target.creditScore }}
            </el-tag>
          </div>
        </div>

        <el-divider content-position="left">五维打分</el-divider>
        <div v-for="d in detail.dims" :key="d.key" class="dim-row">
          <div class="dim-head">
            <span><strong>{{ d.label }}</strong> <span class="sub-text">权重{{ d.weight }}%</span></span>
            <span>{{ d.score }}分</span>
          </div>
          <el-progress
            :percentage="d.score"
            :show-text="false"
            :stroke-width="8"
            :color="matchColor(d.score)"
          />
          <div class="sub-text dim-note">
            {{ d.note }}
            <template v-if="d.sharedTags && d.sharedTags.length">
              （{{ d.sharedTags.join(' / ') }}）
            </template>
          </div>
        </div>

        <el-divider content-position="left">一句话推荐理由</el-divider>
        <div class="reason-box">{{ topReasons(detail.dims, true) }}</div>
      </template>
    </el-dialog>

    <!-- 标签档案编辑 -->
    <el-drawer v-model="tagVisible" title="编辑我的出行标签档案" size="520px">
      <el-alert
        type="info"
        :closable="false"
        class="mb-16"
        title="带「核心」的4个维度填满后，才会进入撮合推荐池"
      />
      <div v-for="dim in userDims" :key="dim.dimension" class="tag-dim">
        <div class="tag-dim-head">
          <strong>{{ dimLabel(dim.dimension) }}</strong>
          <el-tag v-if="dim.core" size="small" type="danger" effect="plain" class="ml-8">核心</el-tag>
          <span class="sub-text ml-8">{{ dim.multi ? '可多选' : '单选' }}</span>
        </div>
        <el-radio-group v-if="!dim.multi" v-model="myTags[dim.dimension]">
          <el-radio v-for="o in dim.options" :key="o.code" :value="o.code" class="tag-radio">
            {{ o.label }}
          </el-radio>
        </el-radio-group>
        <el-checkbox-group v-else v-model="myTags[dim.dimension]">
          <el-checkbox v-for="o in dim.options" :key="o.code" :value="o.code" class="tag-radio">
            {{ o.label }}
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="tagVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTags">保存标签</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { matchApi, tagApi, userApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import { SCHOOL_MAP, creditTagType, matchTagType } from '@/utils/format'

const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const limit = ref(10)
const dict = ref({})
const inPool = ref(false)
const missingDims = ref([])
const tagList = ref([])
const pref = reactive({ openToStranger: false, crossSchool: true })

const detailVisible = ref(false)
const detail = ref(null)
const tagVisible = ref(false)
const myTags = reactive({})

/** 4个冷启动核心维度 */
const coreDims = ['pace', 'interest', 'budget', 'destination']

const dimLabelMap = {
  pace: '出行节奏',
  interest: '兴趣主题',
  budget: '预算档',
  destination: '目的地偏好',
  groupSize: '同行规模',
  lodging: '住宿偏好',
  transport: '交通偏好',
  social: '社交意愿'
}
const dimLabel = (d) => dimLabelMap[d] || d

const userDims = computed(() => {
  const user = dict.value?.USER || {}
  return Object.values(user)
})

const missingText = computed(() =>
  missingDims.value.map((d) => dimLabelMap[d] || d).join('、') || '—'
)

function archiveText(u) {
  const parts = []
  if (u.school) parts.push(SCHOOL_MAP[u.school] || u.school)
  if (u.college) parts.push(u.college)
  if (u.major) parts.push(u.major)
  if (u.grade) parts.push(u.grade + ' 级')
  return parts.join(' · ') || '未填写学院信息'
}

function matchColor(score) {
  const n = Number(score)
  if (n >= 80) return '#52C41A'
  if (n >= 60) return '#2F80ED'
  return '#FF8A65'
}

/** 从五维里挑出最亮的两条说明，拼成一句可读的推荐理由 */
function topReasons(dims, full = false) {
  if (!dims?.length) return ''
  const sorted = [...dims].sort((a, b) => b.score - a.score)
  const hit = sorted.filter((d) => d.score >= 80).slice(0, full ? 3 : 2)
  const pool = hit.length ? hit : sorted.slice(0, 1)
  return pool.map((d) => `${d.label}：${d.note}`).join('；')
}

async function load() {
  loading.value = true
  try {
    const [people, dictData, card, prefData] = await Promise.all([
      matchApi.people(limit.value),
      tagApi.dict().catch(() => ({})),
      auth.user?.id ? userApi.card(auth.user.id).catch(() => null) : Promise.resolve(null),
      matchApi.pref().catch(() => null)
    ])
    list.value = people.list || []
    inPool.value = !!people.poolStatus?.inPool
    missingDims.value = people.poolStatus?.missingDimensions || []
    dict.value = dictData || {}
    tagList.value = card?.tags || []
    if (prefData) {
      pref.openToStranger = !!prefData.openToStranger
      pref.crossSchool = prefData.crossSchool !== false
    }
  } finally {
    loading.value = false
  }
}

async function openDetail(targetId) {
  try {
    detail.value = await matchApi.detail(targetId)
    detailVisible.value = true
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function openTagEditor() {
  // 用已保存的标签回填
  for (const d of userDims.value) {
    const hit = tagList.value.filter((t) => t.dimension === d.dimension).map((t) => t.code)
    myTags[d.dimension] = d.multi ? hit : (hit[0] || '')
  }
  tagVisible.value = true
}

async function saveTags() {
  const tokens = []
  for (const d of userDims.value) {
    const v = myTags[d.dimension]
    if (Array.isArray(v)) {
      v.forEach((code) => tokens.push(`${d.dimension}:${code}`))
    } else if (v) {
      tokens.push(`${d.dimension}:${v}`)
    }
  }
  saving.value = true
  try {
    const u = await userApi.updateProfile({ profileTags: tokens.join(',') })
    auth.setUser(u || auth.user)
    ElMessage.success('标签档案已保存')
    tagVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function savePref() {
  try {
    await matchApi.savePref({
      openToStranger: pref.openToStranger,
      crossSchool: pref.crossSchool
    })
    ElMessage.success('偏好已更新')
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.mb-6 {
  margin-bottom: 6px;
}
.ml-8 {
  margin-left: 8px;
}
.match-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.match-card {
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.match-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
}
.match-name {
  font-size: 16px;
  font-weight: 800;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.score-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin: 12px 0 4px;
}
.score-num {
  font-size: 26px;
  font-weight: 800;
  color: var(--xlvq-primary, #2f80ed);
}
.score-unit {
  font-size: 12px;
  color: #999;
  margin-left: 4px;
}
.dim-mini {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0;
}
.dim-chip {
  font-size: 11.5px;
  color: #666;
  background: #f5f6f8;
  border-radius: 4px;
  padding: 2px 7px;
}
.reason {
  font-size: 12.5px;
  color: #555;
  line-height: 1.6;
  border-left: 3px solid var(--xlvq-primary, #2f80ed);
  background: #f7f9fc;
  padding: 6px 10px;
  border-radius: 4px;
}
.pref-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
}
.dim-row {
  margin-bottom: 14px;
}
.dim-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 4px;
}
.dim-note {
  margin-top: 4px;
}
.reason-box {
  font-size: 13.5px;
  line-height: 1.8;
  background: #f7f9fc;
  border-radius: 8px;
  padding: 12px 14px;
  color: #333;
}
.tag-dim {
  margin-bottom: 20px;
}
.tag-dim-head {
  margin-bottom: 8px;
}
.tag-radio {
  margin-right: 14px;
}
</style>
