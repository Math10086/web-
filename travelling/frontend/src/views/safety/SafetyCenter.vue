<template>
  <div class="page-wrap">
    <div class="x-card mb-16">
      <div class="card-title">🛡️ 安全中心 · 家校知情</div>
      <el-alert type="success" :closable="false" show-icon class="mb-12">
        <template #title>家校联动，安全出行</template>
        紧急联系人可设为<b>家长</b>或<b>辅导员</b>；出行前由队长一键通知，家校共同知情；
        位置共享需<b>学生本人限时授权</b>，仅限紧急联系人查看，到期自动失效。
      </el-alert>
      <div class="stat-row">
        <div class="stat-item">
          <div class="stat-num">{{ contacts.length }}</div>
          <div class="sub-text">紧急联系人</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ authorizedCount }}</div>
          <div class="sub-text">已授权位置查看</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ activeShares.length }}</div>
          <div class="sub-text">生效中的共享</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ myNoticeTotal }}</div>
          <div class="sub-text">家校知情通知</div>
        </div>
      </div>
    </div>

    <el-tabs v-model="tab" class="x-card safety-tabs">
      <!-- ============ 紧急联系人 ============ -->
      <el-tab-pane label="紧急联系人" name="contacts">
        <div class="flex-between mb-12">
          <div class="sub-text">
            设置家长 / 辅导员作为紧急联系人，并授权其查看你的实时位置（家校知情与位置共享的基础）
          </div>
          <el-button type="primary" :icon="Plus" @click="openContactDialog()">添加联系人</el-button>
        </div>

        <el-empty v-if="!contacts.length" description="尚未设置紧急联系人，出行前请先添加" :image-size="70" />
        <div v-for="c in contacts" :key="c.id" class="contact-card">
          <el-avatar :size="42" :style="{ background: c.relation === 'COUNSELOR' ? '#fa8c16' : '#2f80ed' }">
            {{ c.name?.charAt(0) }}
          </el-avatar>
          <div style="flex: 1">
            <div style="font-weight: 700">
              {{ c.name }}
              <el-tag size="small" class="ml-8" :type="RELATION_TAG[c.relation] || 'info'">
                {{ RELATION_MAP[c.relation] || c.relation }}
              </el-tag>
              <el-tag v-if="c.authorized" size="small" type="success" effect="plain" class="ml-8">
                已授权查看位置
              </el-tag>
            </div>
            <div class="sub-text">📞 {{ c.phone }}</div>
          </div>
          <div class="flex-center gap-8">
            <span class="sub-text">授权查看位置</span>
            <el-switch :model-value="!!c.authorized" @change="(v) => toggleAuth(c, v)" />
            <el-button size="small" text type="primary" @click="openContactDialog(c)">编辑</el-button>
            <el-button size="small" text type="danger" @click="removeContact(c)">删除</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- ============ 家校知情通知 ============ -->
      <el-tab-pane label="家校知情通知" name="notices">
        <el-form inline class="mb-12">
          <el-form-item label="选择行程">
            <el-select v-model="noticeTripId" placeholder="选择我的行程" style="width: 320px" @change="loadNotices">
              <el-option
                v-for="t in myTrips"
                :key="t.id"
                :label="t.title"
                :value="t.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :icon="Bell"
              :disabled="!noticeTripId || !isCaptainOfNoticeTrip"
              :loading="notifying"
              @click="sendNotice"
            >
              出行前通知家长与辅导员
            </el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="noticeTripId && !isCaptainOfNoticeTrip"
          type="info"
          :closable="false"
          class="mb-12"
          title="仅队长可发起全队家校知情通知；你可以查看本行程的通知记录。"
        />
        <el-alert
          v-if="noticeTripId && isCaptainOfNoticeTrip"
          type="warning"
          :closable="false"
          class="mb-12"
          title="点击后系统会向全队成员「已授权」的家长 / 辅导员发送行程报备通知，并给成员本人发送提醒。"
        />

        <div v-if="skipped.length" class="skip-box mb-12">
          <div style="font-weight: 700; margin-bottom: 6px">⚠️ 以下成员尚未设置已授权的紧急联系人，未能通知：</div>
          <div v-for="s in skipped" :key="s.memberUserId" class="sub-text">
            · {{ s.memberName }} —— {{ s.reason }}
          </div>
        </div>

        <el-empty v-if="!notices.length" description="该行程暂无家校知情通知记录" :image-size="70" />
        <div v-for="n in notices" :key="n.id" class="notice-card">
          <div class="flex-between">
            <div>
              <strong>{{ n.memberName }}</strong>
              <span class="sub-text"> · {{ n.memberSchool }}</span>
              <el-tag size="small" class="ml-8" type="primary" effect="plain">
                {{ NOTICE_TYPE_MAP[n.noticeType] || n.noticeType }}
              </el-tag>
            </div>
            <span class="sub-text">{{ formatTime(n.createdAt) }}</span>
          </div>
          <div class="mt-12" style="font-size: 13px">
            已通知：<strong>{{ n.contactName }}</strong>
            <el-tag size="small" :type="RELATION_TAG[n.relation] || 'info'" class="ml-8">
              {{ RELATION_MAP[n.relation] || n.relationName }}
            </el-tag>
            <span class="sub-text">（📞 {{ n.phone }}）</span>
          </div>
          <el-collapse class="mt-12">
            <el-collapse-item title="查看通知内容">
              <div class="notice-content">{{ n.content }}</div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-tab-pane>

      <!-- ============ 位置共享 ============ -->
      <el-tab-pane label="位置共享" name="shares">
        <el-alert type="warning" :closable="false" class="mb-12" show-icon>
          <template #title>限时授权 · 最小化收集</template>
          位置共享由你本人发起，需先在上方「紧急联系人」中打开该联系人的授权开关；
          家长 / 辅导员凭专属链接查看，无需注册账号，到期自动失效，可随时撤销。
        </el-alert>

        <el-form :model="shareForm" label-width="112px" class="share-form">
          <el-form-item label="共享行程">
            <el-select v-model="shareForm.tripId" placeholder="选择我参与的行程" style="width: 300px">
              <el-option v-for="t in myTrips" :key="t.id" :label="t.title" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权给">
            <el-select v-model="shareForm.contactId" placeholder="选择已授权的紧急联系人" style="width: 300px">
              <el-option
                v-for="c in authorizedContacts"
                :key="c.id"
                :label="`${c.name}（${RELATION_MAP[c.relation] || c.relation}）`"
                :value="c.id"
              />
            </el-select>
            <span v-if="!authorizedContacts.length" class="sub-text" style="margin-left: 10px">
              暂无已授权的联系人，请先到「紧急联系人」里打开授权开关
            </span>
          </el-form-item>
          <el-form-item label="有效时长">
            <el-radio-group v-model="shareForm.hours">
              <el-radio-button v-for="h in HOUR_OPTIONS" :key="h" :label="h">{{ h }}小时</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="同行队友位置">
            <el-switch v-model="shareForm.includeTeammates" />
            <span class="sub-text" style="margin-left: 10px">
              开启后家长可看到队友的大致位置（脱敏显示），便于判断是否离队
            </span>
          </el-form-item>
          <el-form-item label="留言">
            <el-input
              v-model="shareForm.note"
              maxlength="60"
              show-word-limit
              placeholder="如：妈，我到张家界了，链接可以看我位置"
              style="width: 380px"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :icon="Link"
              :loading="creatingShare"
              :disabled="!shareForm.tripId || !shareForm.contactId"
              @click="createShare"
            >
              生成共享链接
            </el-button>
          </el-form-item>
        </el-form>

        <div v-if="newShare" class="new-share mb-16">
          <div style="font-weight: 700">✅ 共享链接已生成，发给{{ newShare.contactName }}即可查看</div>
          <div class="link-row mt-12">
            <el-input :model-value="trackUrl(newShare.shareUrl)" readonly />
            <el-button type="primary" plain :icon="CopyDocument" @click="copy(trackUrl(newShare.shareUrl))">
              复制
            </el-button>
            <el-button :icon="View" @click="openTrack(newShare)">打开查看页</el-button>
          </div>
          <div class="sub-text mt-12">
            有效期至{{ formatTime(newShare.endAt) }}，剩余{{ remainText(newShare) }}，
            撤销后链接立即失效。
          </div>
        </div>

        <div class="card-title" style="margin-top: 18px">我的位置共享授权</div>
        <el-empty v-if="!shares.length" description="暂无位置共享授权" :image-size="70" />
        <div v-for="s in shares" :key="s.id" class="share-card">
          <div class="flex-between" style="flex-wrap: wrap; gap: 8px">
            <div>
              <strong>{{ s.contactName }}</strong>
              <el-tag size="small" class="ml-8" :type="RELATION_TAG[s.relation] || 'info'">
                {{ RELATION_MAP[s.relation] || s.relationName }}
              </el-tag>
              <el-tag size="small" class="ml-8" :type="SHARE_STATUS_TAG[s.status]">
                {{ SHARE_STATUS_MAP[s.status] || s.status }}
              </el-tag>
              <div class="sub-text" style="margin-top: 4px">
                《{{ s.tripTitle }}》· {{ s.startAt ? formatTime(s.startAt) : '-' }}至
                {{ s.endAt ? formatTime(s.endAt) : '-' }}
              </div>
            </div>
            <div class="flex-center gap-8">
              <span class="sub-text">
                {{ s.viewCount || 0 }}次查看
                <template v-if="s.lastViewedAt">· 最近{{ formatTime(s.lastViewedAt) }}</template>
              </span>
              <template v-if="s.status === 'ACTIVE'">
                <el-tag size="small" type="warning" effect="plain">剩余{{ remainText(s) }}</el-tag>
                <el-button size="small" text type="primary" :icon="CopyDocument" @click="copy(trackUrl(s.shareUrl))">
                  复制链接
                </el-button>
                <el-button size="small" text type="primary" :icon="View" @click="openTrack(s)">查看</el-button>
                <el-button size="small" text type="danger" @click="revoke(s)">撤销</el-button>
              </template>
            </div>
          </div>
          <div v-if="s.note" class="sub-text" style="margin-top: 6px">留言：{{ s.note }}</div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 紧急联系人弹窗 -->
    <el-dialog v-model="contactDialog" :title="contactForm.id ? '编辑紧急联系人' : '添加紧急联系人'" width="460px">
      <el-form :model="contactForm" label-width="84px">
        <el-form-item label="姓名" required>
          <el-input v-model="contactForm.name" placeholder="联系人真实姓名" />
        </el-form-item>
        <el-form-item label="关系" required>
          <el-radio-group v-model="contactForm.relation">
            <el-radio label="PARENT">家长</el-radio>
            <el-radio label="COUNSELOR">辅导员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="contactForm.phone" placeholder="11位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="授权">
          <el-switch v-model="contactForm.authorized" />
          <span class="sub-text" style="margin-left: 10px">允许其查看我的实时位置</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="saveContact">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Bell, Link, CopyDocument, View } from '@element-plus/icons-vue'
import { emergencyApi, safetyApi, tripApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import {
  formatTime,
  NOTICE_TYPE_MAP,
  RELATION_MAP,
  RELATION_TAG,
  SHARE_STATUS_MAP,
  SHARE_STATUS_TAG,
  trackUrl
} from '@/utils/format'

const auth = useAuthStore()

const HOUR_OPTIONS = [6, 12, 24, 48, 72]

const tab = ref('contacts')
const acting = ref(false)
const notifying = ref(false)
const creatingShare = ref(false)

const contacts = ref([])
const myTrips = ref([])
const shares = ref([])
const notices = ref([])
const skipped = ref([])
const newShare = ref(null)
const myNoticeTotal = ref(0)

const noticeTripId = ref(null)
const shareForm = ref({
  tripId: null,
  contactId: null,
  hours: 24,
  includeTeammates: true,
  note: ''
})

const contactDialog = ref(false)
const contactForm = ref({ id: null, name: '', relation: 'PARENT', phone: '', authorized: false })

const authorizedContacts = computed(() => contacts.value.filter((c) => c.authorized))
const authorizedCount = computed(() => authorizedContacts.value.length)
const activeShares = computed(() => shares.value.filter((s) => s.status === 'ACTIVE'))
const isCaptainOfNoticeTrip = computed(() => {
  const t = myTrips.value.find((x) => x.id === noticeTripId.value)
  return !!t && t.captainId === auth.user?.id
})

async function loadContacts() {
  const data = await emergencyApi.list().catch(() => [])
  contacts.value = data?.list || data || []
}

async function loadTrips() {
  const data = await tripApi.my().catch(() => [])
  myTrips.value = data?.list || data || []
  if (!noticeTripId.value && myTrips.value.length) {
    noticeTripId.value = myTrips.value[0].id
  }
  if (!shareForm.value.tripId && myTrips.value.length) {
    shareForm.value.tripId = myTrips.value[0].id
  }
}

async function loadShares() {
  const data = await safetyApi.shares().catch(() => [])
  shares.value = data?.list || data || []
}

/** 我（作为学生）累计收到的家校知情通知条数（顶部统计用） */
async function loadMyNoticeTotal() {
  const data = await safetyApi.myNotices().catch(() => [])
  myNoticeTotal.value = (data?.list || data || []).length
}

async function loadNotices() {
  skipped.value = []
  if (!noticeTripId.value) {
    notices.value = []
    return
  }
  const data = await safetyApi.notices(noticeTripId.value).catch(() => [])
  notices.value = data?.list || data || []
}

function openContactDialog(c) {
  contactDialog.value = true
  contactForm.value = c
    ? { id: c.id, name: c.name, relation: c.relation, phone: c.phone, authorized: !!c.authorized }
    : { id: null, name: '', relation: 'PARENT', phone: '', authorized: false }
}

async function saveContact() {
  const f = contactForm.value
  if (!f.name?.trim() || !f.phone?.trim()) {
    ElMessage.warning('请填写姓名与手机号')
    return
  }
  acting.value = true
  try {
    if (f.id) {
      await emergencyApi.update(f.id, {
        name: f.name,
        relation: f.relation,
        phone: f.phone,
        authorized: f.authorized
      })
    } else {
      const created = await emergencyApi.create({
        name: f.name,
        relation: f.relation,
        phone: f.phone
      })
      const id = created?.id
      if (id && f.authorized) {
        await emergencyApi.update(id, { authorized: true })
      }
    }
    ElMessage.success('已保存，请提醒家长 / 辅导员关注你的行程')
    contactDialog.value = false
    await loadContacts()
  } finally {
    acting.value = false
  }
}

async function toggleAuth(c, v) {
  await emergencyApi.update(c.id, { authorized: v })
  c.authorized = v
  ElMessage.success(v ? '已授权查看实时位置' : '已取消授权，相关共享链接将无法再创建')
}

async function removeContact(c) {
  await ElMessageBox.confirm(`确定删除紧急联系人「${c.name}」？`, '提示', { type: 'warning' })
  await emergencyApi.remove(c.id)
  ElMessage.success('已删除')
  await loadContacts()
}

async function sendNotice() {
  notifying.value = true
  try {
    const res = await safetyApi.notifyDeparture(noticeTripId.value)
    skipped.value = res?.skipped || []
    ElMessage.success(
      `已向${res.noticeCount} 位家长 / 辅导员发送家校知情通知` +
        (skipped.value.length ? `，${skipped.value.length} 位成员因未授权联系人被跳过` : '')
    )
    await loadNotices()
    await loadMyNoticeTotal()
  } finally {
    notifying.value = false
  }
}

async function createShare() {
  creatingShare.value = true
  try {
    const res = await safetyApi.createShare({ ...shareForm.value })
    newShare.value = res
    ElMessage.success('共享链接已生成，复制发给家人即可')
    tab.value = 'shares'
    await loadShares()
  } finally {
    creatingShare.value = false
  }
}

async function revoke(s) {
  await ElMessageBox.confirm(
    `撤销后「${s.contactName}」的查看链接将立即失效，确定撤销？`,
    '撤销位置共享',
    { type: 'warning' }
  )
  await safetyApi.revokeShare(s.id)
  ElMessage.success('已撤销该位置共享授权')
  if (newShare.value?.id === s.id) newShare.value = null
  await loadShares()
}

function remainText(s) {
  if (s.status !== 'ACTIVE' || !s.endAt) return '—'
  const ms = new Date(s.endAt).getTime() - Date.now()
  if (ms <= 0) return '已到期'
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  return h > 0 ? `${h} 小时${m} 分` : `${m} 分钟`
}

function openTrack(s) {
  if (s.shareUrl) {
    window.open(trackUrl(s.shareUrl), '_blank')
    return
  }
  const base = (import.meta.env.BASE_URL || '/').replace(/\/$/, '')
  const path = s.trackPath || ''
  const url = `${window.location.origin}${base}${path.startsWith('/') ? path : '/' + path}`
  window.open(url, '_blank')
}

async function copy(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('链接已复制，发给家人即可')
  } catch (e) {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('链接已复制')
  }
}

onMounted(async () => {
  await Promise.all([loadContacts(), loadTrips(), loadShares(), loadMyNoticeTotal()])
  await loadNotices()
})
</script>

<style scoped>
.safety-tabs {
  padding: 6px 18px 18px;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat-item {
  background: var(--xlvq-card-2, #fafafa);
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  padding: 10px 14px;
  text-align: center;
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--xlvq-primary, #2f80ed);
}
.contact-card,
.notice-card,
.share-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  margin-bottom: 10px;
  background: #fff;
}
.notice-card,
.share-card {
  display: block;
}
.new-share {
  border: 1px dashed var(--xlvq-primary, #2f80ed);
  background: #f4f8ff;
  border-radius: 10px;
  padding: 12px 14px;
}
.link-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.skip-box {
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  padding: 10px 12px;
}
.notice-content {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.9;
  color: var(--xlvq-text-sub);
}
.share-form {
  max-width: 720px;
}
.ml-8 {
  margin-left: 8px;
}
@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
