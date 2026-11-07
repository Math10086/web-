<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <div>
        <h2 class="page-title" style="margin: 0">🧾 消费与费用管理</h2>
        <div class="sub-text" style="margin-top: 4px">
          个人消费报表与团队共享消费报表，消费公开透明，支持 AA 分摊与对账
        </div>
      </div>
      <el-button type="primary" :icon="Plus" :disabled="!auth.isLoggedIn" @click="openDialog">
        记一笔消费
      </el-button>
    </div>

    <el-tabs v-model="tab" @tab-change="onTab">
      <!-- 个人消费报表 -->
      <el-tab-pane label="👤 个人消费报表" name="personal">
        <div v-if="!auth.isLoggedIn" class="x-card">
          <el-empty description="登录后可查看个人消费报表" :image-size="90" />
        </div>
        <template v-else>
          <div class="kpi-row mb-16">
            <div class="kpi-card x-card">
              <div class="kpi-icon">💸</div>
              <div>
                <div class="kpi-value">¥{{ personal?.total ?? 0 }}</div>
                <div class="sub-text">累计支出</div>
              </div>
            </div>
            <div class="kpi-card x-card">
              <div class="kpi-icon">🧮</div>
              <div>
                <div class="kpi-value">¥{{ personal?.myShare ?? 0 }}</div>
                <div class="sub-text">我实际分摊</div>
              </div>
            </div>
            <div class="kpi-card x-card">
              <div class="kpi-icon">📝</div>
              <div>
                <div class="kpi-value">{{ personal?.count ?? 0 }}</div>
                <div class="sub-text">消费笔数</div>
              </div>
            </div>
          </div>

          <div class="two-col">
            <div class="x-card">
              <div class="card-title">🧱 消费类别构成</div>
              <div v-if="personal?.categories?.length">
                <div v-for="c in personal.categories" :key="c.category" class="cat-item">
                  <span class="cat-name">{{ c.name }}</span>
                  <div class="cat-bar-wrap">
                    <div class="cat-bar" :style="{ width: c.percent + '%' }"></div>
                  </div>
                  <span class="cat-amount">¥{{ c.amount }}（{{ c.percent }}%）</span>
                </div>
              </div>
              <el-empty v-else description="暂无消费记录" :image-size="80" />
            </div>
            <div class="x-card">
              <div class="card-title">🧳 按行程汇总</div>
              <div v-if="personal?.trips?.length">
                <div v-for="t in personal.trips" :key="t.tripId" class="trip-item">
                  <span>{{ t.title }}</span>
                  <span class="cat-amount">¥{{ t.amount }}</span>
                </div>
              </div>
              <el-empty v-else description="暂无关联行程的消费" :image-size="80" />
            </div>
          </div>

          <div class="x-card" style="margin-top: 16px">
            <div class="card-title">📋 消费明细</div>
            <el-table :data="personal?.list || []" size="small" empty-text="暂无记录">
              <el-table-column prop="createdAt" label="时间" width="180">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column prop="categoryName" label="类别" width="90" />
              <el-table-column prop="title" label="项目" min-width="180" />
              <el-table-column prop="amount" label="金额" width="100">
                <template #default="{ row }">¥{{ row.amount }}</template>
              </el-table-column>
              <el-table-column prop="perPerson" label="人均" width="100">
                <template #default="{ row }">¥{{ row.perPerson }}</template>
              </el-table-column>
              <el-table-column label="分摊" min-width="160">
                <template #default="{ row }">{{ (row.sharerNames || []).join('、') || '—' }}</template>
              </el-table-column>
              <el-table-column label="" width="70">
                <template #default="{ row }">
                  <el-button link type="danger" size="small" @click="removeExpense(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-tab-pane>

      <!-- 团队共享消费报表 -->
      <el-tab-pane label="👥 团队共享报表" name="team">
        <div class="x-card mb-16">
          <span class="sub-text" style="margin-right: 10px">选择结伴行程：</span>
          <el-select v-model="teamTripId" placeholder="选择一个我参与的结伴行程" style="width: 340px" @change="loadTeam">
            <el-option v-for="t in myTeams" :key="t.tripId" :label="`${t.title}（已登记 ¥${t.total}）`" :value="t.tripId" />
          </el-select>
        </div>

        <template v-if="team">
          <div class="kpi-row mb-16">
            <div class="kpi-card x-card">
              <div class="kpi-icon">💰</div>
              <div>
                <div class="kpi-value">¥{{ team.total }}</div>
                <div class="sub-text">团队合计</div>
              </div>
            </div>
            <div class="kpi-card x-card">
              <div class="kpi-icon">👥</div>
              <div>
                <div class="kpi-value">{{ team.memberCount }}</div>
                <div class="sub-text">参与成员</div>
              </div>
            </div>
            <div class="kpi-card x-card">
              <div class="kpi-icon">🧮</div>
              <div>
                <div class="kpi-value">¥{{ team.avgPerPerson }}</div>
                <div class="sub-text">人均分摊</div>
              </div>
            </div>
            <div class="kpi-card x-card">
              <div class="kpi-icon">🎯</div>
              <div>
                <div class="kpi-value">¥{{ team.budget }}</div>
                <div class="sub-text">队伍预算</div>
              </div>
            </div>
          </div>

          <el-alert
            type="info"
            :closable="false"
            class="mb-16"
            :title="team.sharedDoc"
            description="团队消费报表队长掌握、全员可见、共享编辑，应对队员各自消费不统一、便于 AA 分摊与对账。"
          />

          <div class="two-col">
            <div class="x-card">
              <div class="card-title">👥 成员消费与盈亏</div>
              <el-table :data="team.members" size="small">
                <el-table-column label="成员" min-width="140">
                  <template #default="{ row }">
                    {{ row.nickname }}
                    <el-tag v-if="row.isCaptain" size="small" type="warning" effect="plain">队长</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="已支付" width="100">
                  <template #default="{ row }">¥{{ row.paid }}</template>
                </el-table-column>
                <el-table-column label="应分摊" width="100">
                  <template #default="{ row }">¥{{ row.owed }}</template>
                </el-table-column>
                <el-table-column label="差额" width="110">
                  <template #default="{ row }">
                    <span :style="{ color: row.net >= 0 ? '#f56c6c' : '#67c23a', fontWeight: 600 }">
                      {{ row.net >= 0 ? '应收 ' : '应付 ' }}¥{{ Math.abs(row.net) }}
                    </span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div class="x-card">
              <div class="card-title">🤝 AA 结算建议</div>
              <div v-if="team.settlements?.length">
                <div v-for="(s, i) in team.settlements" :key="i" class="settle-item">
                  <b>{{ s.fromName }}</b> 转给 <b>{{ s.toName }}</b>
                  <span class="settle-amount">¥{{ s.amount }}</span>
                </div>
              </div>
              <el-empty v-else description="当前已结清，无需转账" :image-size="80" />
              <div class="card-title" style="margin-top: 16px">🧱 类别构成</div>
              <div v-for="c in team.categories" :key="c.category" class="cat-item">
                <span class="cat-name">{{ c.name }}</span>
                <div class="cat-bar-wrap">
                  <div class="cat-bar" :style="{ width: c.percent + '%' }"></div>
                </div>
                <span class="cat-amount">¥{{ c.amount }}</span>
              </div>
            </div>
          </div>

          <div class="x-card" style="margin-top: 16px">
            <div class="card-title">📋 团队消费明细</div>
            <el-table :data="team.list" size="small" empty-text="暂无记录">
              <el-table-column prop="createdAt" label="时间" width="180">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column prop="categoryName" label="类别" width="90" />
              <el-table-column prop="title" label="项目" min-width="200" />
              <el-table-column prop="payer" label="经手人" width="100" />
              <el-table-column prop="amount" label="金额" width="100">
                <template #default="{ row }">¥{{ row.amount }}</template>
              </el-table-column>
              <el-table-column prop="perPerson" label="人均" width="100">
                <template #default="{ row }">¥{{ row.perPerson }}</template>
              </el-table-column>
              <el-table-column label="分摊成员" min-width="160">
                <template #default="{ row }">{{ (row.sharerNames || []).join('、') }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
        <el-empty v-else description="选择行程后展示团队共享消费报表" :image-size="100" />
      </el-tab-pane>
    </el-tabs>

    <!-- 记一笔 -->
    <el-dialog v-model="dialogVisible" title="记一笔消费" width="480px">
      <el-form label-width="80px">
        <el-form-item label="归属">
          <el-radio-group v-model="form.scope">
            <el-radio-button value="PERSONAL">个人</el-radio-button>
            <el-radio-button value="TEAM">团队</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scope === 'TEAM'" label="结伴行程">
          <el-select v-model="form.tripId" placeholder="选择行程" style="width: 100%">
            <el-option v-for="t in myTeams" :key="t.tripId" :label="t.title" :value="t.tripId" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-input v-model="form.title" placeholder="如：景区门票 / 晚餐" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { expenseApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import { formatTime } from '@/utils/format'

const auth = useAuthStore()
const tab = ref('personal')
const personal = ref(null)
const myTeams = ref([])
const teamTripId = ref(null)
const team = ref(null)

const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({
  scope: 'PERSONAL',
  tripId: null,
  category: 'FOOD',
  title: '',
  amount: 0,
  remark: ''
})

const CATEGORIES = [
  { label: '交通', value: 'TRANSPORT' },
  { label: '住宿', value: 'HOTEL' },
  { label: '餐饮', value: 'FOOD' },
  { label: '门票', value: 'TICKET' },
  { label: '购物', value: 'SHOPPING' },
  { label: '其他', value: 'OTHER' }
]

async function loadPersonal() {
  if (!auth.isLoggedIn) return
  personal.value = await expenseApi.personal().catch(() => null)
}

async function loadTeams() {
  if (!auth.isLoggedIn) return
  myTeams.value = (await expenseApi.myTeams().catch(() => [])) || []
}

async function loadTeam() {
  if (!teamTripId.value) {
    team.value = null
    return
  }
  team.value = await expenseApi.team(teamTripId.value).catch(() => null)
}

function onTab(name) {
  if (name === 'personal') {
    loadPersonal()
  } else {
    loadTeams()
  }
}

function openDialog() {
  form.value = { scope: 'PERSONAL', tripId: null, category: 'FOOD', title: '', amount: 0, remark: '' }
  dialogVisible.value = true
}

async function submit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请填写支出项目')
    return
  }
  if (!form.value.amount || form.value.amount <= 0) {
    ElMessage.warning('请输入正确的金额')
    return
  }
  if (form.value.scope === 'TEAM' && !form.value.tripId) {
    ElMessage.warning('团队消费请选择结伴行程')
    return
  }
  saving.value = true
  try {
    await expenseApi.add({
      scope: form.value.scope,
      tripId: form.value.tripId,
      category: form.value.category,
      title: form.value.title.trim(),
      amount: form.value.amount,
      remark: form.value.remark
    })
    ElMessage.success('已记录')
    dialogVisible.value = false
    await loadPersonal()
    await loadTeams()
    if (form.value.scope === 'TEAM') {
      teamTripId.value = form.value.tripId
      await loadTeam()
    }
  } finally {
    saving.value = false
  }
}

async function removeExpense(id) {
  try {
    await ElMessageBox.confirm('确认删除这条消费记录？', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  await expenseApi.remove(id)
  ElMessage.success('已删除')
  await loadPersonal()
}

onMounted(() => {
  loadPersonal()
  loadTeams()
})
</script>

<style scoped>
.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
}
.kpi-card {
  display: flex;
  align-items: center;
  gap: 12px;
}
.kpi-icon {
  font-size: 26px;
}
.kpi-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--xlvq-primary);
}
.two-col {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 16px;
}
.cat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  font-size: 13px;
}
.cat-name {
  width: 50px;
  font-weight: 600;
}
.cat-bar-wrap {
  flex: 1;
  height: 10px;
  background: #f0f3f8;
  border-radius: 6px;
  overflow: hidden;
}
.cat-bar {
  height: 100%;
  background: linear-gradient(90deg, #69a6f8, var(--xlvq-primary));
  border-radius: 6px;
}
.cat-amount {
  color: var(--xlvq-text-sub);
  white-space: nowrap;
}
.trip-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed var(--xlvq-border);
  font-size: 13px;
}
.settle-item {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--xlvq-primary-light);
  margin-bottom: 8px;
  font-size: 13px;
}
.settle-amount {
  font-weight: 700;
  color: var(--xlvq-orange);
}
</style>
