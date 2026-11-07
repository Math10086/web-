<template>
  <div class="page-wrap" v-loading="loading">
    <!-- ==================== 信誉分总览卡 ==================== -->
    <div class="x-card mb-16">
      <div class="credit-head">
        <div class="score-box">
          <div class="score-num" :class="levelClass">{{ profile.score ?? 100 }}</div>
          <el-tag :type="levelTagType" effect="dark">{{ profile.levelName || '信用良好' }}</el-tag>
        </div>
        <div class="score-detail">
          <div class="sub-text mb-8">信誉分是结伴履约的信用资产：互评、账目结清会加分，被核实的投诉会扣分。</div>
          <div class="threshold-row">
            <div class="threshold" v-for="th in thresholds" :key="th.label">
              <span class="sub-text">{{ th.label }}</span>
              <el-tag
                size="small"
                :type="profile.score >= th.value ? 'success' : 'info'"
                effect="plain"
              >
                {{ th.value }}分{{ profile.score >= th.value ? '· 已达标' : '· 未达标' }}
              </el-tag>
            </div>
          </div>
          <el-progress
            class="mt-8"
            :percentage="Math.min(100, Math.round(((profile.score ?? 100) / 150) * 100))"
            :stroke-width="12"
            :color="levelColor"
          />
        </div>
      </div>

      <!-- 统计行 -->
      <div class="stat-row mt-16">
        <div class="stat-item">
          <div class="stat-num">{{ stats.finishedTrips ?? 0 }}</div>
          <div class="sub-text">完结行程</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ stats.finishedAsCaptain ?? 0 }}</div>
          <div class="sub-text">担任队长</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ stats.avgRate || '—' }}</div>
          <div class="sub-text">互评均分（1-5）</div>
        </div>
        <div class="stat-item">
          <div class="stat-num" :class="{ 'stat-warn': (stats.complaintsEstablished ?? 0) > 0 }">
            {{ stats.complaintsEstablished ?? 0 }}
          </div>
          <div class="sub-text">被核实投诉</div>
        </div>
      </div>
    </div>

    <!-- ==================== 徽章墙 ==================== -->
    <div class="x-card mb-16">
      <h3 class="block-title">徽章墙</h3>
      <div class="badge-grid">
        <div
          v-for="b in profile.badges || []"
          :key="b.name"
          class="badge"
          :class="{ earned: b.earned }"
        >
          <div class="badge-name">{{ b.name }}</div>
          <div class="badge-desc">{{ b.desc }}</div>
          <el-tag size="small" :type="b.earned ? 'success' : 'info'" effect="plain">
            {{ b.earned ? '已点亮' : '未达成' }}
          </el-tag>
        </div>
      </div>
    </div>

    <div class="two-col">
      <!-- ==================== 信誉变动历史 ==================== -->
      <div class="x-card">
        <h3 class="block-title">信誉变动历史（最近20条）</h3>
        <el-empty v-if="!(profile.logs || []).length" description="暂无变动记录" :image-size="60" />
        <div v-else class="log-list">
          <div v-for="l in profile.logs" :key="l.id" class="log-item">
            <el-tag size="small" :type="(l.delta || 0) > 0 ? 'success' : 'danger'" class="log-delta">
              {{ l.delta > 0 ? '+' : '' }}{{ l.delta }}
            </el-tag>
            <div class="log-body">
              <div>{{ l.reason }}</div>
              <div class="sub-text">{{ formatTime(l.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== 同行人投诉 ==================== -->
      <div class="x-card">
        <div class="flex-between mb-8">
          <h3 class="block-title" style="margin: 0">同行人投诉与申诉</h3>
          <el-button size="small" type="danger" plain @click="openCreate">发起投诉</el-button>
        </div>
        <el-tabs v-model="complaintTab">
          <el-tab-pane label="我发起的" name="filed">
            <el-empty v-if="!complaints.filed?.length" description="没有发起过投诉" :image-size="60" />
            <complaint-row
              v-for="c in complaints.filed"
              :key="c.id"
              :item="c"
              role="reporter"
              @appealed="loadAll"
            />
          </el-tab-pane>
          <el-tab-pane label="我被投诉的" name="received">
            <el-empty v-if="!complaints.received?.length" description="没有被投诉记录，继续保持" :image-size="60" />
            <complaint-row
              v-for="c in complaints.received"
              :key="c.id"
              :item="c"
              role="target"
              @appealed="loadAll"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- ==================== 发起投诉弹窗 ==================== -->
    <el-dialog v-model="createVisible" title="发起同行人投诉" width="520px">
      <el-form label-width="90px">
        <el-form-item label="选择行程">
          <el-select
            v-model="form.tripId"
            placeholder="选择已结束或进行中的行程"
            style="width: 100%"
            @change="loadTripMembers"
          >
            <el-option
              v-for="t in finishedTrips"
              :key="t.id"
              :value="t.id"
              :label="`${t.title}（${t.destination}）`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="被投诉人">
          <el-select v-model="form.targetId" placeholder="选择同队成员" style="width: 100%">
            <el-option
              v-for="m in tripMembers"
              :key="m.userId"
              :value="m.userId"
              :label="m.nickname"
              :disabled="m.userId === myId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="投诉类型">
          <el-select v-model="form.type" placeholder="选择类型" style="width: 100%">
            <el-option label="爽约（未按时集合且失联）" value="NO_SHOW" />
            <el-option label="行程中失联" value="LOST_CONTACT" />
            <el-option label="言行不当" value="MISBEHAVE" />
            <el-option label="费用不清（AA 账目拒不结清）" value="EXPENSE_DISPUTE" />
          </el-select>
        </el-form-item>
        <el-form-item label="情况说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请客观描述发生了什么（将提交给管理员核实）"
          />
        </el-form-item>
        <el-form-item label="证据补充">
          <el-input
            v-model="form.evidence"
            type="textarea"
            :rows="2"
            placeholder="聊天记录、转账凭证等线索描述（选填）"
          />
        </el-form-item>
      </el-form>
      <div class="sub-text mb-8">
        投诉将提交管理员核实，核实属实会按类型扣除对方信誉分（爽约 -15，其余 -10）；
        恶意投诉可能影响你自己的信誉，请如实填写。
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitCreate">提交投诉</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 申诉弹窗 ==================== -->
    <el-dialog v-model="appealVisible" title="对核实结果发起申诉" width="480px">
      <div class="sub-text mb-8">
        申诉仅针对「已核实属实」的投诉。管理员复核后：申诉成立将回滚扣除的信誉分；维持原判则终审生效。
      </div>
      <el-input
        v-model="appealReason"
        type="textarea"
        :rows="4"
        placeholder="请说明申诉理由（例如：当时有特殊情况已提前告知队友，可附时间线）"
      />
      <template #footer>
        <el-button @click="appealVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAppeal">提交申诉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue'
import { ElMessage, ElTag } from 'element-plus'
import { creditApi, tripApi } from '@/api'
import { formatTime } from '@/utils/format'

const loading = ref(true)
const submitting = ref(false)
const profile = ref({})
const complaints = ref({ filed: [], received: [] })
const myTrips = ref([])
const createVisible = ref(false)
const appealVisible = ref(false)
const appealReason = ref('')
const appealTarget = ref(null)
const form = ref({ tripId: null, targetId: null, type: null, description: '', evidence: '' })
const tripMembers = ref([])
const complaintTab = ref('filed')

const me = JSON.parse(localStorage.getItem('xlvq_user') || '{}')
const myId = me.id

const stats = computed(() => profile.value.stats || {})
const thresholds = computed(() => {
  const th = profile.value.thresholds || {}
  return [
    { label: '发布 / 报名结伴', value: th.trip ?? 60 },
    { label: '发布导游计划', value: th.guidePlan ?? 80 },
    { label: '信誉标杆徽章', value: 110 }
  ]
})

const levelTagType = computed(() => {
  switch (profile.value.level) {
    case 'EXCELLENT': return 'success'
    case 'GOOD': return 'primary'
    case 'AVERAGE': return 'warning'
    default: return 'danger'
  }
})

const levelColor = computed(() => {
  switch (profile.value.level) {
    case 'EXCELLENT': return '#3b6d11'
    case 'GOOD': return '#1d9e75'
    case 'AVERAGE': return '#ba7517'
    default: return '#a32d2d'
  }
})

const levelClass = computed(() => profile.value.level || 'GOOD')

const finishedTrips = computed(() =>
  (myTrips.value || []).filter((t) => t.status === 'FINISHED' || t.status === 'ONGOING')
)

async function loadAll() {
  loading.value = true
  try {
    const [p, c, t] = await Promise.all([
      creditApi.profile(),
      creditApi.myComplaints(),
      tripApi.my().catch(() => ({ data: [] }))
    ])
    profile.value = p.data || {}
    complaints.value = c.data || { filed: [], received: [] }
    myTrips.value = t.data || []
  } catch (e) {
    // api层已统一弹错误提示
  } finally {
    loading.value = false
  }
}

async function loadTripMembers(tripId) {
  form.value.targetId = null
  tripMembers.value = []
  if (!tripId) return
  try {
    const res = await tripApi.team(tripId)
    const data = res.data || {}
    const list = data.members || data.team || []
    tripMembers.value = list
      .map((m) => ({
        userId: m.userId ?? m.id,
        nickname: m.nickname || m.name || `用户${m.userId ?? m.id}`
      }))
      .filter((m) => m.userId && m.userId !== myId)
  } catch (e) {
    tripMembers.value = []
  }
}

function openCreate() {
  form.value = { tripId: null, targetId: null, type: null, description: '', evidence: '' }
  tripMembers.value = []
  createVisible.value = true
}

async function submitCreate() {
  if (!form.value.tripId || !form.value.targetId || !form.value.type) {
    ElMessage.warning('请选择行程、被投诉人与投诉类型')
    return
  }
  if (!form.value.description || !form.value.description.trim()) {
    ElMessage.warning('请填写情况说明')
    return
  }
  submitting.value = true
  try {
    await creditApi.createComplaint(form.value.tripId, {
      targetId: form.value.targetId,
      type: form.value.type,
      description: form.value.description,
      evidence: form.value.evidence
    })
    ElMessage.success('投诉已提交，待管理员核实')
    createVisible.value = false
    complaintTab.value = 'filed'
    await loadAll()
  } finally {
    submitting.value = false
  }
}

async function submitAppeal() {
  if (!appealReason.value.trim()) {
    ElMessage.warning('请填写申诉理由')
    return
  }
  submitting.value = true
  try {
    await creditApi.appeal(appealTarget.value.id, appealReason.value)
    ElMessage.success('申诉已提交，等待管理员复核')
    appealVisible.value = false
    appealReason.value = ''
    await loadAll()
  } finally {
    submitting.value = false
  }
}

/** 单条投诉展示行（内部小组件） */
const ComplaintRow = defineComponent({
  name: 'ComplaintRow',
  components: { ElTag },
  props: {
    item: { type: Object, required: true },
    role: { type: String, default: 'reporter' }
  },
  emits: ['appealed'],
  setup(props, { emit }) {
    const STATUS = {
      PENDING: { text: '待核实', type: 'warning' },
      RESOLVED: { text: '已核实属实', type: 'danger' },
      REJECTED: { text: '不属实', type: 'info' },
      APPEALED: { text: '申诉中', type: 'primary' },
      FINAL: { text: '终审', type: 'success' }
    }
    const st = STATUS[props.item.status] || { text: props.item.status, type: 'info' }
    const other = props.role === 'reporter' ? props.item.targetName : props.item.reporterName
    const otherLabel = props.role === 'reporter' ? '被投诉人' : '投诉人'
    return () =>
      h('div', { class: 'complaint-item' }, [
        h('div', { class: 'complaint-head' }, [
          h(ElTag, { size: 'small', type: st.type, effect: 'plain' }, () => st.text),
          h('strong', { class: 'complaint-type' }, props.item.typeName),
          h('span', { class: 'sub-text' }, `${otherLabel}：${other || '—'}`)
        ]),
        h('div', { class: 'complaint-desc' }, props.item.description || '（无说明）'),
        h('div', { class: 'sub-text' }, [
          `行程「${props.item.tripTitle || props.item.tripId}」 · `,
          String(props.item.createdAt || '').replace('T', ' ').slice(0, 16),
          props.item.creditDelta
            ? ` · 信誉分${props.item.creditDelta > 0 ? '+' : ''}${props.item.creditDelta}`
            : '',
          props.item.finalAction === 'OVERTURNED' ? ' · 申诉成立已回滚' : '',
          props.item.finalAction === 'UPHELD' ? ' · 申诉后维持原判' : ''
        ]),
        props.item.result
          ? h('div', { class: 'complaint-result' }, `处理意见：${props.item.result}`)
          : null,
        props.role === 'target' && props.item.status === 'RESOLVED'
          ? h(
              'div',
              { style: 'margin-top: 8px; text-align: right' },
              h(
                'button',
                {
                  class: 'appeal-btn',
                  onClick: () => {
                    appealTarget.value = props.item
                    appealReason.value = ''
                    appealVisible.value = true
                  }
                },
                '对结果申诉'
              )
            )
          : null
      ])
  }
})

onMounted(loadAll)
</script>

<style scoped>
.credit-head {
  display: flex;
  gap: 24px;
  align-items: center;
  flex-wrap: wrap;
}
.score-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 120px;
}
.score-num {
  font-size: 52px;
  font-weight: 800;
  line-height: 1.1;
}
.score-num.EXCELLENT { color: #3b6d11; }
.score-num.GOOD { color: #1d9e75; }
.score-num.AVERAGE { color: #ba7517; }
.score-num.RISKY,
.score-num.BLOCKED { color: #a32d2d; }
.score-detail {
  flex: 1;
  min-width: 260px;
}
.threshold-row {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.threshold {
  display: flex;
  gap: 6px;
  align-items: center;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 12px;
  border-top: 1px dashed #e4e4e4;
  padding-top: 14px;
}
.stat-item {
  text-align: center;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
}
.stat-warn {
  color: #a32d2d;
}
.block-title {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 12px;
}
.badge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}
.badge {
  border: 1px solid #e4e4e4;
  border-radius: 10px;
  padding: 12px;
  text-align: center;
  opacity: 0.55;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}
.badge.earned {
  opacity: 1;
  border-color: #c0dd97;
  background: #f4faf0;
}
.badge-name {
  font-weight: 700;
  font-size: 14px;
}
.badge-desc {
  font-size: 12px;
  color: #7a7a7a;
  line-height: 1.5;
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 900px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
.log-list {
  max-height: 380px;
  overflow: auto;
}
.log-item {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed #eee;
  align-items: flex-start;
}
.log-delta {
  flex-shrink: 0;
}
.log-body {
  font-size: 13px;
  line-height: 1.6;
}
.complaint-item {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
  font-size: 13px;
}
.complaint-head {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.complaint-type {
  font-size: 13px;
}
.complaint-desc {
  color: #4a4a4a;
  margin-bottom: 4px;
  line-height: 1.6;
}
.complaint-result {
  margin-top: 4px;
  color: #7a7a7a;
  background: #f7f7f5;
  border-radius: 6px;
  padding: 6px 8px;
}
.appeal-btn {
  border: 1px solid #d0d0d0;
  background: #fff;
  border-radius: 6px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
}
.appeal-btn:hover {
  border-color: #185fa5;
  color: #185fa5;
}
</style>
