<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <div>
        <h2 class="page-title" style="margin: 0">💬 AI 旅行搭子</h2>
        <div class="sub-text" style="margin-top: 4px">
          越聊越懂你的旅行 AI：认识你的标签画像、记得你说过的话，本地大模型驱动、旅行数据不出本机
        </div>
      </div>
      <el-tag v-if="engineText" :type="engineTagType" effect="plain" size="large">
        {{ engineText }}
      </el-tag>
    </div>

    <div class="chat-layout">
      <!-- 左侧：对话区 -->
      <div class="x-card chat-card">
        <template v-if="auth.isLoggedIn">
          <div ref="listRef" class="msg-list">
            <el-empty
              v-if="!messages.length && !loading"
              description="和 TA 随便聊聊，比如：周末想去长沙玩，预算800，喜欢拍照和美食"
              :image-size="90"
            />
            <div
              v-for="(m, i) in messages"
              :key="i"
              class="msg-row"
              :class="{ mine: m.role === 'user' }"
            >
              <div class="avatar">{{ m.role === 'user' ? '🧑' : '🤖' }}</div>
              <div class="bubble-wrap">
                <div class="bubble" :class="{ mine: m.role === 'user' }">{{ m.content }}</div>
                <div v-if="m.role === 'assistant' && m.source" class="src-text">
                  {{ sourceText(m.source) }}
                </div>
              </div>
            </div>
            <div v-if="loading" class="msg-row">
              <div class="avatar">🤖</div>
              <div class="bubble-wrap">
                <div class="bubble thinking">正在思考，本地模型首次响应可能要十几秒…</div>
              </div>
            </div>
          </div>

          <div class="input-bar">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="说点什么…（Enter 发送，Shift+Enter 换行）"
              :disabled="loading"
              @keydown.enter.exact.prevent="send"
            />
            <div class="input-actions">
              <el-button link size="small" @click="loadMemory">TA 记得我什么？</el-button>
              <el-button
                type="primary"
                :loading="loading"
                :disabled="!draft.trim()"
                @click="send"
              >
                发送
              </el-button>
            </div>
          </div>
        </template>

        <el-empty v-else description="登录后才能和你的专属 AI 搭子聊天（记忆按账号隔离）">
          <el-button type="primary" @click="router.push('/login')">去登录</el-button>
        </el-empty>
      </div>

      <!-- 右侧：记忆面板 -->
      <div class="x-card memory-card">
        <div class="card-title">🧠 AI 记得我</div>
        <template v-if="auth.isLoggedIn">
          <div class="mem-meta">
            <el-tag size="small" effect="plain">已聊{{ memory.chatCount || 0 }}轮</el-tag>
            <span v-if="memory.updatedAt" class="sub-text">
              更新于{{ formatTime(memory.updatedAt) }}
            </span>
          </div>
          <div v-if="memory.memory" class="mem-body">{{ memory.memory }}</div>
          <el-empty
            v-else
            description="还没有记忆。多聊几轮，AI 会自动把你的偏好总结成画像"
            :image-size="70"
          />
          <div class="mem-actions">
            <el-button size="small" @click="clearHistory">清空对话</el-button>
            <el-button size="small" type="danger" plain @click="forget">让 TA 彻底忘掉我</el-button>
          </div>
          <el-divider />
          <div class="sub-text" style="font-size: 12px; line-height: 1.7">
            个性化原理：AI 每轮对话后自动把「旧记忆 + 最新对话」融合成新画像摘要；
            下次对话连同你的档案标签一起注入提示词——不微调模型，也能越聊越懂你。
          </div>
        </template>
        <el-empty v-else description="登录后查看" :image-size="70" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi } from '@/api'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

const messages = ref([])
const draft = ref('')
const loading = ref(false)
const listRef = ref(null)
const memory = ref({})
const engine = ref(null)

const engineText = computed(() => {
  if (!engine.value) return ''
  const c = engine.value.chat || {}
  if (c.ollamaEnabled) return `本地引擎 · ${c.ollamaModel}`
  if (c.cloudConfigured) return `云端引擎 · ${c.cloudModel}`
  return '离线模式'
})
const engineTagType = computed(() =>
  engine.value?.chat?.ollamaEnabled || engine.value?.chat?.cloudConfigured
    ? 'success'
    : 'info'
)

function sourceText(s) {
  return s === 'ollama' ? '本地模型' : s === 'cloud' ? '云端兜底' : '离线兜底'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function scrollBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function send() {
  const text = draft.value.trim()
  if (!text || loading.value) return
  draft.value = ''
  messages.value.push({ role: 'user', content: text })
  scrollBottom()
  loading.value = true
  try {
    const data = await aiApi.chat(text)
    messages.value.push({
      role: 'assistant',
      content: data.reply,
      source: data.source
    })
    loadMemory()
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: '（这条消息没能送达，请稍后再试）',
      source: 'offline'
    })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

async function loadHistory() {
  if (!auth.isLoggedIn) return
  try {
    messages.value = (await aiApi.chatHistory()) || []
  } catch (e) {
    messages.value = []
  }
  scrollBottom()
}

async function loadMemory() {
  if (!auth.isLoggedIn) return
  try {
    memory.value = (await aiApi.memory()) || {}
  } catch (e) {
    /* 静默 */
  }
}

async function clearHistory() {
  await ElMessageBox.confirm('只清空对话记录，AI 的长期记忆会保留，确定？', '清空对话', {
    type: 'warning'
  })
  await aiApi.clearHistory()
  messages.value = []
  ElMessage.success('对话已清空')
}

async function forget() {
  await ElMessageBox.confirm(
    '将清空 AI 对你的全部长期记忆（对话记录保留），确定？',
    '彻底遗忘',
    { type: 'warning' }
  )
  await aiApi.forgetMemory()
  loadMemory()
  ElMessage.success('TA 已经忘掉你了')
}

onMounted(() => {
  loadHistory()
  loadMemory()
  aiApi.status().then((s) => (engine.value = s)).catch(() => {})
})
</script>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: 16px;
  align-items: stretch;
}
@media (max-width: 1000px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
}
.chat-card {
  display: flex;
  flex-direction: column;
  min-height: 60vh;
  max-height: 74vh;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 6px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.msg-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.msg-row.mine {
  flex-direction: row-reverse;
  justify-content: flex-start;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #f2f4f8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.bubble-wrap {
  max-width: 76%;
  min-width: 0;
  /* Safari下row-reverse + 块级子元素会出现气泡悬空：改成纵向flex，
     气泡宽度收缩为内容宽（fit-content），自己一侧的气泡贴向头像侧 */
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.msg-row.mine .bubble-wrap {
  align-items: flex-end;
}
.bubble {
  width: fit-content;
  max-width: 100%;
  text-align: left;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f5f7fa;
  border: 1px solid var(--xlvq-border, #e4e7ed);
  line-height: 1.7;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble.mine {
  background: var(--xlvq-primary, #2563eb);
  color: #fff;
  border-color: transparent;
}
.bubble.thinking {
  color: var(--xlvq-text-sub, #909399);
  font-style: italic;
}
.src-text {
  font-size: 11px;
  color: var(--xlvq-text-sub, #a8abb2);
  margin-top: 4px;
}
.input-bar {
  border-top: 1px solid var(--xlvq-border, #e4e7ed);
  padding-top: 12px;
  margin-top: 8px;
}
.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.memory-card {
  max-height: 74vh;
  overflow-y: auto;
}
.mem-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.mem-body {
  background: #f7f9fc;
  border: 1px dashed var(--xlvq-border, #e4e7ed);
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.mem-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
