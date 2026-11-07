<template>
  <div class="page-wrap">
    <div class="x-card" style="padding: 0; overflow: hidden">
      <div class="chat-wrap">
        <!-- 会话列表 -->
        <div class="chat-sider">
          <div
            style="padding: 14px; font-weight: 700; border-bottom: 1px solid var(--xlvq-border)"
          >
            💬 我的会话
          </div>
          <el-empty
            v-if="!conversations.length"
            description="暂无会话，可从帖子或队员页发起私信"
            :image-size="60"
            style="padding-top: 40px"
          />
          <div
            v-for="c in conversations"
            :key="c.peerId ?? c.userId"
            class="conv-item"
            :class="{ active: activePeer === (c.peerId ?? c.userId) }"
            @click="selectPeer(c.peerId ?? c.userId)"
          >
            <el-badge :value="c.unreadCount" :hidden="!c.unreadCount" :max="99">
              <el-avatar :size="42" :src="c.avatar">
                {{ c.nickname?.charAt(0) }}
              </el-avatar>
            </el-badge>
            <div style="flex: 1; min-width: 0">
              <div class="flex-between">
                <strong style="font-size: 14px">{{ c.nickname }}</strong>
                <span class="bubble-time">{{ formatTime(c.updatedAt || c.createdAt) }}</span>
              </div>
              <div class="sub-text" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                {{ c.lastMessage || '开始聊天吧' }}
              </div>
            </div>
          </div>
        </div>

        <!-- 聊天主区域 -->
        <div class="chat-main">
          <template v-if="activePeer">
            <div
              style="padding: 12px 18px; border-bottom: 1px solid var(--xlvq-border); font-weight: 700; background: #fff"
              class="flex-between"
            >
              <span>
                {{ peerName }}
                <el-button link type="primary" size="small" @click="$router.push(`/profile?id=${activePeer}`)">
                  TA 的主页
                </el-button>
              </span>
              <el-button
                size="small"
                @click="$router.push(`/trips/my`)"
                text
              >
                结伴成员可在此沟通
              </el-button>
            </div>

            <div ref="msgBoxEl" class="chat-msgs">
              <el-empty v-if="!messages.length" description="还没有消息，发个招呼吧" :image-size="70" />
              <div
                v-for="m in messages"
                :key="m.id"
                class="bubble-row"
                :class="{ mine: isMine(m) }"
              >
                <el-avatar :size="34" :src="isMine(m) ? auth.user?.avatar : peerAvatar">
                  {{ (isMine(m) ? auth.user?.nickname : peerName)?.charAt(0) }}
                </el-avatar>
                <div>
                  <div class="bubble">{{ m.content }}</div>
                  <div class="bubble-time mt-12" :class="isMine(m) ? 'flex' : ''" style="justify-content: flex-end">
                    {{ formatTime(m.createdAt) }}
                  </div>
                </div>
              </div>
            </div>

            <div class="chat-input-bar">
              <el-input
                v-model="draft"
                type="textarea"
                :rows="2"
                resize="none"
                placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                @keydown.enter.exact.prevent="send"
              />
              <el-button type="primary" :loading="sending" @click="send">发送</el-button>
            </div>
          </template>
          <div v-else style="flex: 1" class="flex-center">
            <el-empty description="选择左侧会话开始聊天" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { messageApi, userApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import { formatTime } from '@/utils/format'

const route = useRoute()
const auth = useAuthStore()

const conversations = ref([])
const activePeer = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const peerName = ref('')
const peerAvatar = ref('')
const msgBoxEl = ref(null)
let timer = null

function isMine(m) {
  return m.fromId === auth.user?.id || m.mine === true || m.senderId === auth.user?.id
}

async function loadConversations() {
  const data = await messageApi.conversations().catch(() => [])
  const list = data?.list || data || []
  const prevActive = activePeer.value
  conversations.value = list
  // 从帖子/成员页跳转携带userId
  if (!prevActive && route.query.userId) {
    const uid = Number(route.query.userId)
    selectPeer(uid)
    const c = list.find((x) => (x.peerId ?? x.userId) === uid)
    if (!c) loadPeerInfo(uid)
  } else if (prevActive) {
    const c = list.find((x) => (x.peerId ?? x.userId) === prevActive)
    if (c) {
      peerName.value = c.nickname
      peerAvatar.value = c.avatar
    }
  }
}

async function loadPeerInfo(uid) {
  const u = await userApi.get(uid).catch(() => null)
  if (u) {
    peerName.value = u.nickname
    peerAvatar.value = u.avatar
  } else {
    peerName.value = `用户${uid}`
  }
}

async function selectPeer(peerId) {
  activePeer.value = peerId
  const c = conversations.value.find((x) => (x.peerId ?? x.userId) === peerId)
  if (c) {
    peerName.value = c.nickname
    peerAvatar.value = c.avatar
  } else {
    loadPeerInfo(peerId)
  }
  const data = await messageApi.withPeer(peerId).catch(() => [])
  messages.value = data?.list || data || []
  scrollBottom()
  loadConversations()
}

async function send() {
  const content = draft.value.trim()
  if (!content || !activePeer.value) return
  sending.value = true
  try {
    await messageApi.send(activePeer.value, content)
    draft.value = ''
    const data = await messageApi.withPeer(activePeer.value)
    messages.value = data?.list || data || []
    loadConversations()
    scrollBottom()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    sending.value = false
  }
}

function scrollBottom() {
  nextTick(() => {
    if (msgBoxEl.value) msgBoxEl.value.scrollTop = msgBoxEl.value.scrollHeight
  })
}

watch(
  () => route.query.userId,
  (v) => {
    if (v && Number(v) !== activePeer.value) selectPeer(Number(v))
  }
)

onMounted(() => {
  loadConversations()
  timer = setInterval(loadConversations, 5000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>
