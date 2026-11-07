<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <h2 class="page-title" style="margin: 0">🎯 社区圈层 · 和同好一起玩</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">创建圈层</el-button>
    </div>

    <el-empty v-if="!loading && !circles.length" description="还没有圈层，快来创建第一个吧" />

    <div v-loading="loading" class="circle-grid">
      <div
        v-for="c in circles"
        :key="c.id"
        class="x-card hoverable circle-card"
        @click="$router.push(`/circles/${c.id}`)"
      >
        <div class="circle-icon">{{ c.icon || '🏕️' }}</div>
        <div style="font-size: 16px; font-weight: 700">{{ c.name }}</div>
        <el-tag class="mt-12" size="small" type="info" effect="plain">
          {{ c.theme || '兴趣交流' }}
        </el-tag>
        <div class="sub-text mt-12" style="min-height: 36px">
          {{ c.description || '圈内聚合动态、精华帖与结伴活动' }}
        </div>
        <div class="flex-center gap-12 mt-12 sub-text">
          <span>👥 {{ c.memberCount ?? 0 }}成员</span>
          <span>📝 {{ c.postCount ?? 0 }}帖子</span>
        </div>
      </div>
    </div>

    <!-- 创建圈层弹窗 -->
    <el-dialog v-model="dialogVisible" title="创建兴趣圈层" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="圈层名称" prop="name">
          <el-input v-model="form.name" placeholder="如：长沙美食圈" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="圈层主题" prop="theme">
          <el-select v-model="form.theme" placeholder="选择主题" style="width: 100%">
            <el-option label="🍜 美食探店" value="美食" />
            <el-option label="🥾 户外徒步" value="徒步" />
            <el-option label="📷 摄影出片" value="摄影" />
            <el-option label="⛺ 露营野趣" value="露营" />
            <el-option label="🚶 Citywalk" value="Citywalk" />
            <el-option label="⚡ 特种兵速通" value="特种兵速通" />
            <el-option label="🎢 乐园演艺" value="乐园演艺" />
            <el-option label="🤫 小众秘境" value="小众秘境" />
          </el-select>
        </el-form-item>
        <el-form-item label="圈层图标">
          <el-input v-model="form.icon" placeholder="选一个 emoji 作为图标">
            <template #append>
              <el-dropdown trigger="click" @command="(v) => (form.icon = v)">
                <el-button>选择</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="ic in iconChoices"
                      :key="ic"
                      :command="ic"
                    >
                      {{ ic }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="圈层介绍" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="介绍一下这个圈层的玩法、适合什么样的同学加入"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { circleApi } from '@/api'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

const circles = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()

const iconChoices = ['🍜', '🥾', '📷', '⛺', '🚶', '⚡', '🏔️', '🏖️', '🎢', '🌃', '🤫', '🎒', '🍵', '🌸']

const form = ref({ name: '', theme: '', description: '', icon: '🏕️' })
const rules = {
  name: [{ required: true, message: '请输入圈层名称', trigger: 'blur' }],
  theme: [{ required: true, message: '请选择圈层主题', trigger: 'change' }],
  description: [{ required: true, message: '请填写圈层介绍', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await circleApi.list()
    circles.value = data?.list || data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/circles' } })
    return
  }
  form.value = { name: '', theme: '', description: '', icon: '🏕️' }
  dialogVisible.value = true
}

async function handleCreate() {
  await formRef.value.validate()
  saving.value = true
  try {
    const data = await circleApi.create({ ...form.value })
    ElMessage.success('圈层创建成功')
    dialogVisible.value = false
    if (data?.id) router.push(`/circles/${data.id}`)
    else load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
