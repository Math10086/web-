<template>
  <div class="page-wrap" v-loading="loading">
    <template v-if="trip.id">
      <!-- 行程信息卡 -->
      <div class="x-card mb-16">
        <div class="flex-between" style="flex-wrap: wrap; gap: 10px">
          <h2 style="margin: 0">
            {{ trip.title }}
            <el-tag :type="TRIP_STATUS_TAG[trip.status]" class="ml-8">
              {{ TRIP_STATUS_MAP[trip.status] }}
            </el-tag>
            <el-tag :type="RISK_TAG[trip.riskLevel] || 'success'" effect="plain" class="ml-8">
              {{ RISK_MAP[trip.riskLevel] || '低风险' }}
            </el-tag>
          </h2>
          <div class="flex gap-8">
            <!-- 队长团队管理页入口：一屏看全队足迹与全员消费状态（队长与队员都能进） -->
            <el-button
              v-if="isCaptain || trip.myMemberStatus"
              type="primary"
              plain
              @click="$router.push(`/trips/${trip.id}/team`)"
            >
              🧭 团队管理
            </el-button>
            <!-- 队长操作 -->
            <template v-if="isCaptain">
              <el-button
                v-if="isRecruiting"
                type="primary"
                :icon="VideoPlay"
                @click="changeStatus('ONGOING')"
              >
                开始行程
              </el-button>
              <el-button
                v-if="trip.status === 'ONGOING'"
                type="success"
                :icon="CircleCheck"
                @click="changeStatus('FINISHED')"
              >
                结束行程
              </el-button>
              <el-button
                v-if="isRecruiting"
                type="danger"
                plain
                @click="changeStatus('CANCELLED')"
              >
                取消行程
              </el-button>
              <el-button type="warning" plain :icon="Wallet" @click="expenseVisible = true">
                AA 记账
              </el-button>
            </template>

            <!-- 成员操作 -->
            <template v-else-if="auth.isLoggedIn">
              <el-button
                v-if="isRecruiting && !trip.myMemberStatus"
                type="primary"
                :disabled="isFull"
                @click="openApply"
              >
                {{ isFull ? '名额已满' : '我要报名' }}
              </el-button>
              <el-tag v-if="trip.myMemberStatus === 'PENDING'" type="warning">
                报名审核中，请等待队长确认
              </el-tag>
              <el-tag v-if="trip.myMemberStatus === 'APPROVED'" type="success">
                已加入本行程
              </el-tag>
              <el-tag v-if="trip.myMemberStatus === 'REJECTED'" type="danger">
                报名未通过
              </el-tag>
              <el-button
                v-if="trip.myMemberStatus === 'APPROVED' && trip.status === 'ONGOING'"
                type="primary"
                :icon="LocationInformation"
                :loading="locating"
                @click="shareMyLocation"
              >
                上报实时位置
              </el-button>
            </template>
            <el-button v-else type="primary" @click="goLogin">登录后报名</el-button>
          </div>
        </div>

        <div class="mt-12" style="line-height: 2.2">
          <div class="flex gap-16" style="flex-wrap: wrap">
            <span>📍 目的地：<strong>{{ trip.destination }}</strong></span>
            <span>🕒 出发时间：{{ formatTime(trip.departAt) }}</span>
            <span>🚄 出行方式：{{ TRANSPORT_MODE_MAP[trip.transport] || trip.transport }}</span>
          </div>
          <div class="flex gap-16" style="flex-wrap: wrap">
            <span>👥 人数：{{ approvedMembers.length }}/{{ trip.maxMembers }}</span>
            <span>💰 人均预算：¥{{ trip.budget }}</span>
            <span>📌 集合点：{{ trip.meetPoint }}</span>
          </div>
          <div class="tag-line mt-12">
            <span class="sub-text">结伴偏好：</span>
            <el-tag size="small" effect="plain" type="primary">
              {{ PACE_MAP[trip.paceType] || '节奏适中' }}
            </el-tag>
            <el-tag v-if="trip.prefDays" size="small" effect="plain">{{ trip.prefDays }}天</el-tag>
            <el-tag
              size="small"
              effect="plain"
              :type="trip.schoolScope === 'SAME' ? 'warning' : 'info'"
            >
              {{ SCHOOL_SCOPE_MAP[trip.schoolScope] || '三校跨校' }}
            </el-tag>
            <el-tag v-for="tag in splitTags(trip.interestTags)" :key="tag" size="small" effect="plain">
              {{ tag }}
            </el-tag>
          </div>
          <div class="sub-text" style="white-space: pre-wrap; margin-top: 8px">
            {{ trip.description }}
          </div>
        </div>

        <!-- 我的偏好匹配度 -->
        <div v-if="trip.myMatch" class="match-box mt-16">
          <div class="flex-between">
            <div style="font-weight: 700">
              你与本次结伴的偏好匹配度
              <el-tag :type="matchTagType(trip.myMatch.matchScore)" class="ml-8">
                {{ trip.myMatch.matchScore }}分
              </el-tag>
            </div>
            <el-button size="small" text type="primary" @click="prefVisible = true">
              调整我的偏好
            </el-button>
          </div>
          <div class="match-dims">
            <div v-for="d in trip.myMatch.dims" :key="d.key" class="match-dim">
              <div class="flex-between">
                <span>{{ d.label }} <span class="sub-text">(权重{{ d.weight }}%)</span></span>
                <strong :style="{ color: dimColor(d.score) }">{{ d.score }}</strong>
              </div>
              <el-progress
                :percentage="d.score"
                :stroke-width="6"
                :show-text="false"
                :color="dimColor(d.score)"
              />
              <div class="sub-text">{{ d.note }}</div>
            </div>
          </div>
          <div class="sub-text" style="margin-top: 8px">
            提示：匹配度由天数、节奏、目的地、兴趣、预算、出行方式六个维度加权计算，供参考
          </div>
        </div>

        <div class="author-card mt-16" style="border-top: 1px solid var(--xlvq-border); padding-top: 14px">
          <el-avatar
            :size="44"
            :src="trip.captain?.avatar"
            class="clickable-avatar"
            title="查看队长资料"
            @click="goUser(trip.captainId)"
          >
            {{ trip.captain?.nickname?.charAt(0) }}
          </el-avatar>
          <div style="flex: 1">
            <div style="font-weight: 700; cursor: pointer" @click="goUser(trip.captainId)">
              队长：{{ trip.captain?.nickname }}
              <el-tag v-if="trip.captain?.verified" type="success" size="small" class="ml-8">三校认证</el-tag>
            </div>
            <div class="sub-text">
              {{ SCHOOL_MAP[trip.captain?.school] || trip.captain?.school }} · 信誉分
              {{ trip.captain?.creditScore ?? 100 }}
            </div>
          </div>
          <el-button
            v-if="canSeeHistory && !isCaptain"
            size="small"
            @click="openHistory()"
          >
            组队经历
          </el-button>
          <el-button
            v-if="auth.isLoggedIn && !isCaptain"
            size="small"
            @click="$router.push(`/messages?userId=${trip.captainId}`)"
          >
            私信队长
          </el-button>
        </div>
      </div>

      <div class="two-col">
        <!-- 成员列表 -->
        <div class="x-card">
          <div class="card-title">
            👥 队员列表（{{ approvedMembers.length }}/{{ trip.maxMembers }}）
            <el-tag v-if="pendingMembers.length" type="warning" size="small" style="margin-left: 8px">
              {{ pendingMembers.length }}人待审核
            </el-tag>
          </div>
          <div class="member-grid">
            <div v-for="m in trip.members || []" :key="m.id" class="member-card">
              <el-avatar
                :size="46"
                :src="m.avatar"
                class="clickable-avatar"
                title="查看队员资料"
                @click="goUser(m.userId)"
              >
                {{ m.nickname?.charAt(0) }}
              </el-avatar>
              <div
                style="font-weight: 600; margin-top: 6px; cursor: pointer"
                @click="goUser(m.userId)"
              >
                {{ m.nickname }}
              </div>
              <div class="sub-text">信誉{{ m.creditSnapshot ?? m.score ?? 100 }}</div>
              <div v-if="matchOf(m)" class="sub-text">
                <el-popover trigger="hover" width="260" placement="top">
                  <template #reference>
                    <span class="match-inline">
                      偏好匹配度
                      <el-tag size="small" :type="matchTagType(matchOf(m).matchScore)">
                        {{ matchOf(m).matchScore }}
                      </el-tag>
                    </span>
                  </template>
                  <div v-for="d in matchOf(m).dims" :key="d.key" style="line-height: 1.9; font-size: 12px">
                    <strong>{{ d.label }}</strong> {{ d.score }}分 · {{ d.note }}
                  </div>
                </el-popover>
              </div>
              <el-tag :type="MEMBER_STATUS_TAG[m.status] || 'info'" size="small" class="mt-12">
                {{ MEMBER_STATUS_MAP[m.status] || m.status }}
              </el-tag>
              <div v-if="canSeeHistory && m.status === 'APPROVED'" class="mt-12">
                <el-button size="small" text type="primary" @click="openHistory()">
                  组队经历
                </el-button>
              </div>
              <div v-if="m.status === 'APPROVED' && trip.status === 'FINISHED' && !isCaptainSelf(m)" class="mt-12">
                <el-button size="small" text type="primary" @click="openRate(m)">互评</el-button>
              </div>
              <div v-if="isCaptain && m.status === 'PENDING'" class="flex gap-8 mt-12" style="justify-content: center">
                <el-button size="small" type="success" @click="review(m, 'approve')">通过</el-button>
                <el-button size="small" type="danger" plain @click="review(m, 'reject')">拒绝</el-button>
              </div>
              <div v-if="m.lng && m.lat" class="loc-info">
                📡 {{ Number(m.lng).toFixed(4) }}, {{ Number(m.lat).toFixed(4) }}
                <br />更新于{{ formatTime(m.locationAt) }}
              </div>
            </div>
          </div>
        </div>

        <!-- 安全区 -->
        <div class="section-stack">
          <div class="x-card">
            <div class="card-title">🛡️ 安全中心 · 家校知情</div>
            <el-alert
              type="success"
              :closable="false"
              class="mb-12"
              title="家校联动：请将紧急联系人设为家长或辅导员，结伴出行前完成行程报备；授权后联系人可查看你的实时位置。"
              show-icon
            />
            <div v-for="c in contacts" :key="c.id" class="contact-row">
              <div>
                <strong>{{ c.name }}</strong>
                <el-tag size="small" class="ml-8" :type="RELATION_TAG[c.relation] || 'info'">
                  {{ RELATION_MAP[c.relation] || c.relation }}
                </el-tag>
                <div class="sub-text">📞 {{ c.phone }}</div>
              </div>
              <div class="flex-center gap-8">
                <span class="sub-text">授权查看位置</span>
                <el-switch
                  :model-value="!!c.authorized"
                  @change="(v) => toggleAuth(c, v)"
                />
              </div>
            </div>
            <el-empty v-if="!contacts.length" description="尚未设置紧急联系人" :image-size="56" />
            <el-button class="mt-12" type="primary" plain :icon="Plus" @click="contactVisible = true">
              添加紧急联系人
            </el-button>

            <!-- 家校知情：出行前通知与位置共享入口 -->
            <div class="sub-divider"></div>
            <div class="flex-between" style="gap: 10px; flex-wrap: wrap">
              <div>
                <div style="font-weight: 700">家校知情 · 位置共享</div>
                <div class="sub-text">
                  <template v-if="noticeCount">已向家长 / 辅导员发送{{ noticeCount }}条行前通知</template>
                  <template v-else>出行前把行程告知家长与辅导员，家长可凭链接查看你的实时位置</template>
                </div>
              </div>
              <div class="flex gap-8">
                <el-button
                  v-if="isCaptain"
                  type="primary"
                  plain
                  :icon="Bell"
                  :loading="notifying"
                  @click="notifyDeparture"
                >
                  一键通知
                </el-button>
                <el-button :icon="Share" @click="$router.push('/safety')">位置共享给家人</el-button>
              </div>
            </div>

            <el-divider />
            <div class="flex-between">
              <div>
                <div style="font-weight: 700">旅游意外险</div>
                <div class="sub-text">承保行程全程意外风险，¥30/人，即时生效</div>
              </div>
              <el-button type="warning" :icon="Present" @click="buyInsurance">购买保险 ¥30</el-button>
            </div>
            <div v-if="myInsurances.length" class="mt-12">
              <el-tag v-for="ins in myInsurances" :key="ins.id" type="success" class="ml-8">
                {{ ins.planName }} · {{ formatDate(ins.createdAt) }}
              </el-tag>
            </div>
          </div>

          <!-- AA 账单 -->
          <div class="x-card">
            <div class="card-title">
              💰 AA 账单
              <el-button
                v-if="isCaptain"
                size="small"
                type="primary"
                text
                style="margin-left: auto"
                @click="expenseVisible = true"
              >
                记一笔
              </el-button>
            </div>
            <el-empty v-if="!trip.expenses?.length" description="暂无账单记录" :image-size="56" />
            <div v-for="e in trip.expenses" :key="e.id" class="expense-row">
              <div>
                <strong>{{ e.title }}</strong>
                <div class="sub-text">
                  合计 ¥{{ e.amount }} · {{ e.sharerCount }}人均摊 每人
                  <span style="color: #f5222d; font-weight: 700">¥{{ e.perPerson }}</span>
                  <span v-if="e.payer"> · {{ e.payer }}垫付</span>
                </div>
              </div>
            </div>
            <div v-if="trip.expenses?.length" class="sub-text" style="margin-top: 8px">
              共{{ trip.expenses.length }}笔 · 与「团队管理」的团队总支出同源
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 报名弹窗 -->
    <el-dialog v-model="applyVisible" title="申请加入行程" width="560px">
      <el-alert
        type="info"
        :closable="false"
        class="mb-12"
        title="报名需完成三校学籍认证；队长会参考你的信誉分与偏好匹配度进行审核，请按时守约。"
      />
      <el-input
        v-model="applyReason"
        type="textarea"
        :rows="4"
        placeholder="介绍一下你自己：学校年级、户外经验、为什么想参加…"
      />

      <el-divider content-position="left">我的结伴偏好（用于与队伍匹配打分）</el-divider>
      <el-form :model="applyPref" label-width="96px" size="small">
        <el-form-item label="游玩节奏">
          <el-radio-group v-model="applyPref.paceType">
            <el-radio-button
              v-for="p in PACE_OPTIONS"
              :key="p.value"
              :label="p.value"
            >
              {{ PACE_SHORT_MAP[p.value] }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预期天数">
          <el-input-number v-model="applyPref.expectDays" :min="1" :max="15" />
          <span class="sub-text" style="margin-left: 8px">天</span>
        </el-form-item>
        <el-form-item label="兴趣标签">
          <el-checkbox-group v-model="applyTagList">
            <el-checkbox v-for="t in TRIP_INTEREST_TAGS" :key="t" :label="t">{{ t }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="预算上限">
          <el-input-number v-model="applyPref.budget" :min="0" :step="100" :precision="0" />
          <span class="sub-text" style="margin-left: 8px">元/人</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitApply">提交报名</el-button>
      </template>
    </el-dialog>

    <!-- 我的结伴偏好 -->
    <TripPrefDialog v-model="prefVisible" @saved="load" />

    <!-- AA 记账弹窗 -->
    <el-dialog v-model="expenseVisible" title="记一笔 AA 费用" width="520px">
      <el-form label-width="90px">
        <el-form-item label="费用项目">
          <el-input v-model="expenseForm.title" placeholder="如：民宿两晚 / 门票 / 拼车油费" />
        </el-form-item>
        <el-form-item label="总金额">
          <el-input-number v-model="expenseForm.amount" :min="0" :precision="2" :step="10" />
          <span class="sub-text" style="margin-left: 8px">元</span>
        </el-form-item>
        <el-form-item label="垫付人">
          <el-checkbox-group v-model="expenseForm.payerIds">
            <el-checkbox
              v-for="m in approvedMembers"
              :key="m.userId || m.id"
              :label="m.userId || m.id"
            >
              {{ m.nickname }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="sub-text">勾选实际垫付的同学，保存后系统按参与人数计算每人应摊金额</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="expenseVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitExpense">保存账单</el-button>
      </template>
    </el-dialog>

    <!-- 紧急联系人弹窗 -->
    <el-dialog v-model="contactVisible" title="添加紧急联系人" width="460px">
      <el-form :model="contactForm" label-width="84px">
        <el-form-item label="姓名" required>
          <el-input v-model="contactForm.name" placeholder="联系人真实姓名" />
        </el-form-item>
        <el-form-item label="关系" required>
          <el-radio-group v-model="contactForm.relation">
            <el-radio label="家长">家长</el-radio>
            <el-radio label="辅导员">辅导员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="contactForm.phone" placeholder="11位手机号" maxlength="11" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitContact">保存</el-button>
      </template>
    </el-dialog>

    <!-- 互评弹窗 -->
    <el-dialog v-model="rateVisible" title="行程结束 · 队员互评" width="440px">
      <div v-if="rateTarget" class="mb-12">
        评价队员：<strong>{{ rateTarget.nickname }}</strong>
        <div class="sub-text">从履约、准时、内容真实度等方面评价，计入队员信誉分</div>
      </div>
      <el-rate v-model="rateForm.score" :texts="['差评', '一般', '还行', '满意', '超赞']" show-text />
      <el-input
        v-model="rateForm.comment"
        type="textarea"
        :rows="3"
        class="mt-12"
        placeholder="留下你的评价（选填）"
      />
      <template #footer>
        <el-button @click="rateVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitRate">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- 组队经历弹窗：队长与队员互相可见历史结伴记录 -->
    <el-dialog v-model="historyVisible" title="组队经历 · 信任参考" width="620px">
      <el-alert
        type="info"
        :closable="false"
        class="mb-12"
        title="展示本队成员（含队长）的历史结伴记录与信誉分；报名前可先考察，游客不可见。"
      />
      <div v-loading="historyLoading">
        <div v-for="h in histories" :key="h.userId" class="history-block">
          <div class="flex gap-8" style="align-items: center">
            <el-avatar :size="34" :src="h.avatar">{{ h.nickname?.charAt(0) }}</el-avatar>
            <strong>{{ h.nickname }}</strong>
            <el-tag v-if="h.isCaptain" type="warning" size="small">队长</el-tag>
            <span class="sub-text">
              {{ SCHOOL_MAP[h.school] || h.school || '' }} · 信誉分
              {{ h.creditScore ?? 100 }}
            </span>
            <span style="margin-left: auto" class="sub-text">
              带队{{ h.asCaptain }}次 · 参团{{ h.asMember }}次
            </span>
          </div>
          <el-empty
            v-if="!h.list?.length"
            description="暂无历史结伴记录"
            :image-size="48"
          />
          <div v-else class="history-list">
            <div v-for="item in h.list" :key="item.tripId" class="history-row">
              <div>
                <strong>{{ item.title }}</strong>
                <div class="sub-text">
                  📍 {{ item.destination }} · {{ item.departAt || '时间待定' }}
                </div>
              </div>
              <div class="flex gap-8" style="align-items: center">
                <el-tag size="small" effect="plain">{{ item.role }}</el-tag>
                <el-tag
                  size="small"
                  :type="item.statusText === '已完成' ? 'success' : 'info'"
                >
                  {{ item.statusText }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-if="!histories.length && !historyLoading" description="暂无数据" />
      </div>
      <template #footer>
        <el-button @click="historyVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  VideoPlay,
  CircleCheck,
  Wallet,
  LocationInformation,
  Plus,
  Present,
  Bell,
  Share
} from '@element-plus/icons-vue'
import {
  emergencyApi,
  insuranceApi,
  safetyApi,
  tripApi
} from '@/api'
import { useAuthStore } from '@/store/auth'
import TripPrefDialog from '@/views/trips/TripPrefDialog.vue'
import {
  formatDate,
  formatTime,
  matchTagType,
  MEMBER_STATUS_MAP,
  MEMBER_STATUS_TAG,
  PACE_MAP,
  PACE_OPTIONS,
  PACE_SHORT_MAP,
  RELATION_MAP,
  RELATION_TAG,
  RISK_MAP,
  RISK_TAG,
  SCHOOL_MAP,
  SCHOOL_SCOPE_MAP,
  splitTags,
  TRANSPORT_MODE_MAP,
  TRIP_INTEREST_TAGS,
  TRIP_STATUS_MAP,
  TRIP_STATUS_TAG
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const acting = ref(false)
const locating = ref(false)
const notifying = ref(false)
const noticeCount = ref(0)
const trip = ref({})
const contacts = ref([])
const myInsurances = ref([])

const applyVisible = ref(false)
const expenseVisible = ref(false)
const contactVisible = ref(false)
const rateVisible = ref(false)
const prefVisible = ref(false)
const applyReason = ref('')
const applyTagList = ref([])
const applyPref = ref({
  paceType: 'BALANCED',
  expectDays: null,
  budget: null,
  interestTags: '',
  openToCrossSchool: true
})
const expenseForm = ref({ title: '', amount: 0, payerIds: [] })
const contactForm = ref({ name: '', relation: '家长', phone: '' })
const rateTarget = ref(null)
const rateForm = ref({ score: 5, comment: '' })

// ===== 组队经历 =====
const historyVisible = ref(false)
const historyLoading = ref(false)
const histories = ref([])
const historyLoaded = ref(false)
/** 可查看组队经历：所有登录的前台用户（报名前考察队长与队员，商家/管理员无结伴入口） */
const canSeeHistory = computed(
  () => auth.isLoggedIn && !auth.isMerchant && !auth.isAdmin
)

async function openHistory() {
  historyVisible.value = true
  if (historyLoaded.value) return
  historyLoading.value = true
  try {
    histories.value = await tripApi.histories(route.params.id)
    historyLoaded.value = true
  } finally {
    historyLoading.value = false
  }
}

/** 点队长/队员头像与昵称，进入其个人资料页（全站统一用/profile?id= 看他人资料） */
function goUser(userId) {
  if (!userId) return
  router.push(`/profile?id=${userId}`)
}

const isCaptain = computed(
  () => auth.isLoggedIn && trip.value.captainId === auth.user?.id
)
const isRecruiting = computed(
  () => ['OPEN', 'FULL', 'PLANNING'].includes(trip.value.status)
)
const approvedMembers = computed(
  () => (trip.value.members || []).filter((m) => m.status === 'APPROVED')
)
const pendingMembers = computed(
  () => (trip.value.members || []).filter((m) => m.status === 'PENDING')
)
const isFull = computed(
  () => approvedMembers.value.length >= Number(trip.value.maxMembers)
)

/** 队长视角：取某位报名者的匹配度快照 */
function matchOf(m) {
  const map = trip.value.matchMap || {}
  return map[m.userId] || map[String(m.userId)] || null
}

function dimColor(score) {
  if (score >= 80) return '#52c41a'
  if (score >= 60) return '#fa8c16'
  return '#f5222d'
}

/** 打开报名弹窗：预填我的结伴偏好，可当场调整后随报名一起提交 */
async function openApply() {
  const p = await tripApi.getPref().catch(() => null)
  if (p) {
    applyPref.value = {
      paceType: p.paceType || 'BALANCED',
      expectDays: p.expectDays ?? null,
      budget: p.budget ?? null,
      openToCrossSchool: p.openToCrossSchool !== false
    }
    applyTagList.value = splitTags(p.interestTags)
  }
  applyVisible.value = true
}

function isCaptainSelf(m) {
  return m.userId === auth.user?.id
}

function goLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

async function load() {
  loading.value = true
  try {
    trip.value = await tripApi.detail(route.params.id)
  } finally {
    loading.value = false
  }
}

async function loadSafety() {
  if (!auth.isLoggedIn) return
  const [c, i, n] = await Promise.allSettled([
    emergencyApi.list(),
    insuranceApi.mine(),
    safetyApi.notices(route.params.id)
  ])
  contacts.value = c.status === 'fulfilled' ? c.value?.list || c.value || [] : []
  const list = i.status === 'fulfilled' ? i.value?.list || i.value || [] : []
  myInsurances.value = list.filter(
    (x) => !x.tripId || String(x.tripId) === String(route.params.id)
  )
  const notices = n.status === 'fulfilled' ? n.value?.list || n.value || [] : []
  noticeCount.value = notices.length
}

/** 队长在出行前通知全队成员的家长与辅导员 */
async function notifyDeparture() {
  notifying.value = true
  try {
    const res = await safetyApi.notifyDeparture(trip.value.id)
    const skipped = res?.skipped || []
    noticeCount.value = (noticeCount.value || 0) + (res?.noticeCount || 0)
    if (!res?.noticeCount) {
      ElMessage.warning('没有可通知的联系人：队员需先在「安全中心」添加并授权家长 / 辅导员')
    } else {
      ElMessage.success(
        `已向${res.noticeCount} 位家长 / 辅导员发送行前知情通知` +
          (skipped.length ? `，${skipped.length} 位成员未设置已授权联系人` : '')
      )
    }
    if (skipped.length) {
      ElMessageBox.alert(
        skipped.map((s) => `${s.memberName}：${s.reason}`).join('\n'),
        '以下成员未被通知',
        { confirmButtonText: '知道了' }
      )
    }
  } finally {
    notifying.value = false
  }
}

async function changeStatus(status) {
  const map = {
    ONGOING: ['确认开始行程？开始后成员可上报实时位置', '行程已开始，注意安全'],
    FINISHED: ['确认结束本次行程？结束后开放队员互评', '行程已结束，记得互评哦'],
    CANCELLED: ['确定取消该行程？取消后成员将无法继续操作', '行程已取消']
  }
  await ElMessageBox.confirm(map[status][0], '操作确认', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '再想想'
  })
  await tripApi.updateStatus(trip.value.id, status)
  ElMessage.success(map[status][1])
  load()
}

async function submitApply() {
  if (!applyReason.value.trim()) {
    ElMessage.warning('请填写报名理由')
    return
  }
  acting.value = true
  try {
    const pref = { ...applyPref.value, interestTags: applyTagList.value.join(',') }
    await tripApi.apply(trip.value.id, applyReason.value.trim(), pref)
    ElMessage.success('报名成功，等待队长审核')
    applyVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

async function review(member, action) {
  await tripApi.reviewMember(trip.value.id, member.id, action)
  ElMessage.success(action === 'approve' ? '已通过报名' : '已拒绝报名')
  load()
}

// 上报位置：优先浏览器定位，不支持则使用长沙坐标随机微调
function shareMyLocation() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve(randomChangsha())
      return
    }
    locating.value = true
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        resolve({ lng: pos.coords.longitude, lat: pos.coords.latitude })
      },
      () => resolve(randomChangsha()),
      { enableHighAccuracy: true, timeout: 8000 }
    )
  })
    .then(async ({ lng, lat }) => {
      await tripApi.shareLocation(trip.value.id, lng, lat)
      ElMessage.success('位置已上报，授权联系人可查看（家校知情）')
      load()
    })
    .finally(() => {
      locating.value = false
    })
}

function randomChangsha() {
  ElMessage.info('浏览器定位不可用，已使用长沙城区模拟坐标')
  return {
    lng: 112.97 + (Math.random() - 0.5) * 0.08,
    lat: 28.2 + (Math.random() - 0.5) * 0.06
  }
}

async function submitExpense() {
  if (!expenseForm.value.title.trim() || !expenseForm.value.amount) {
    ElMessage.warning('请填写项目与金额')
    return
  }
  if (!expenseForm.value.payerIds.length) {
    ElMessage.warning('请勾选垫付人')
    return
  }
  acting.value = true
  try {
    await tripApi.addExpense(trip.value.id, { ...expenseForm.value })
    ElMessage.success('账单已记录')
    expenseVisible.value = false
    expenseForm.value = { title: '', amount: 0, payerIds: [] }
    load()
  } finally {
    acting.value = false
  }
}

async function submitContact() {
  const f = contactForm.value
  if (!f.name || !f.phone) {
    ElMessage.warning('请填写姓名与手机号')
    return
  }
  acting.value = true
  try {
    await emergencyApi.create({ ...f })
    ElMessage.success('紧急联系人已添加，请提醒家长/辅导员关注行程')
    contactVisible.value = false
    contactForm.value = { name: '', relation: 'PARENT', phone: '' }
    loadSafety()
  } finally {
    acting.value = false
  }
}

async function toggleAuth(c, v) {
  await emergencyApi.update(c.id, { authorized: v })
  c.authorized = v
  ElMessage.success(v ? '已授权查看实时位置' : '已取消授权')
}

async function buyInsurance() {
  await ElMessageBox.confirm(
    '为该行程购买「旅游意外险」，保费 ¥30/人，由平台模拟支付并即时生效。确认购买？',
    '购买旅游意外险',
    { confirmButtonText: '确认购买 ¥30', cancelButtonText: '取消', type: 'warning' }
  )
  await insuranceApi.purchase({ tripId: trip.value.id, planName: '旅游意外险' })
  ElMessage.success('投保成功，出行风险有兜底')
  loadSafety()
}

function openRate(member) {
  rateTarget.value = member
  rateForm.value = { score: 5, comment: '' }
  rateVisible.value = true
}

async function submitRate() {
  acting.value = true
  try {
    await tripApi.rate(trip.value.id, {
      userId: rateTarget.value.userId || rateTarget.value.id,
      score: rateForm.value.score,
      comment: rateForm.value.comment
    })
    ElMessage.success('评价已提交，感谢反馈')
    rateVisible.value = false
  } finally {
    acting.value = false
  }
}

onMounted(() => {
  load().then(loadSafety)
})
</script>

<style scoped>
.ml-8 {
  margin-left: 8px;
}
.clickable-avatar {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.clickable-avatar:hover {
  transform: scale(1.06);
  box-shadow: 0 0 0 3px var(--xlvq-primary-soft, rgba(64, 158, 255, 0.25));
}
.tag-line {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}
.match-box {
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--xlvq-card-2, #fafafa);
}
.match-dims {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px 18px;
  margin-top: 10px;
}
.match-dim {
  font-size: 13px;
}
.match-inline {
  cursor: help;
}
@media (max-width: 900px) {
  .match-dims {
    grid-template-columns: repeat(2, 1fr);
  }
}
.two-col {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 16px;
  align-items: start;
}
.contact-row,
.expense-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed var(--xlvq-border);
  gap: 12px;
}
.sub-divider {
  height: 1px;
  background: var(--xlvq-border);
  margin: 16px 0 12px;
}
/* 组队经历弹窗：按人分块展示历史结伴记录 */
.history-block {
  padding: 12px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.history-block:last-child {
  border-bottom: none;
}
.history-list {
  margin-top: 8px;
}
.history-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  margin-top: 6px;
  border-radius: 8px;
  background: var(--xlvq-card-2, #fafafa);
}
@media (max-width: 900px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
