<template>
  <div class="page-wrap">
    <div class="x-card" v-loading="loading" style="max-width: 860px; margin: 0 auto">
      <h2 class="page-title">{{ isEdit ? '编辑帖子' : '发布新帖' }}</h2>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="帖子类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button label="GUIDE">📖 攻略</el-radio-button>
            <el-radio-button label="MOMENT">✨ 动态</el-radio-button>
            <el-radio-button label="QUESTION">❓ 问答</el-radio-button>
            <el-radio-button label="LOCAL">🏘️ 本地攻略</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="一个吸引人的标题，如：长沙三天两夜极限逛吃攻略"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="所属圈层" prop="circleId">
          <el-select v-model="form.circleId" placeholder="选择圈层（可选）" clearable style="width: 100%">
            <el-option v-for="c in circles" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>

        <div class="flex gap-12" style="flex-wrap: wrap">
          <el-form-item label="城市" style="flex: 1; min-width: 220px">
            <el-input v-model="form.city" placeholder="如：长沙" list="city-list" />
            <datalist id="city-list">
              <option v-for="c in cityOptions" :key="c" :value="c" />
            </datalist>
          </el-form-item>
          <el-form-item label="目的地" style="flex: 1; min-width: 220px">
            <el-input v-model="form.destination" placeholder="如：岳麓山 · 橘子洲" />
          </el-form-item>
        </div>

        <!-- 预算向结构化攻略：人均预算、建议天数、避坑主题、适合人群 -->
        <div class="flex gap-12" style="flex-wrap: wrap">
          <el-form-item label="人均预算" style="flex: 1; min-width: 200px">
            <el-input-number
              v-model="form.costPerPerson"
              :min="0"
              :max="99999"
              :step="50"
              controls-position="right"
              style="width: 100%"
              placeholder="元 / 人"
            />
            <span class="sub-text" style="margin-left: 8px">元 / 人</span>
          </el-form-item>
          <el-form-item label="建议天数" style="flex: 1; min-width: 200px">
            <el-input-number
              v-model="form.days"
              :min="1"
              :max="30"
              controls-position="right"
              style="width: 100%"
            />
            <span class="sub-text" style="margin-left: 8px">天</span>
          </el-form-item>
        </div>

        <el-form-item label="避坑主题">
          <el-select
            v-model="avoidList"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择本篇攻略重点提醒的坑（可选）"
            style="width: 100%"
          >
            <el-option
              v-for="o in avoidOptions"
              :key="o.code"
              :label="o.label"
              :value="o.code"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="适合人群">
          <el-select
            v-model="suitableList"
            multiple
            placeholder="选择攻略适合的人群（可选）"
            style="width: 100%"
          >
            <el-option
              v-for="o in suitableOptions"
              :key="o.code"
              :label="o.label"
              :value="o.code"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="玩法模式">
          <el-select v-model="form.modeTag" placeholder="选择玩法标签（可选）" clearable style="width: 100%">
            <el-option
              v-for="(label, key) in ROUTE_MODE_MAP"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="图片">
          <div style="width: 100%">
            <div
              v-for="(url, i) in imageList"
              :key="i"
              class="flex gap-8 mb-12"
              style="align-items: center"
            >
              <el-input v-model="imageList[i]" placeholder="粘贴图片 URL（后端返回的图片地址）" />
              <el-avatar :size="40" :src="url" shape="square">图</el-avatar>
              <el-button :icon="Delete" circle @click="imageList.splice(i, 1)" />
            </div>
            <el-button type="primary" plain size="small" :icon="Plus" @click="imageList.push('')">
              添加图片
            </el-button>
            <span class="sub-text" style="margin-left: 10px">多张图片将以逗号拼接提交</span>
          </div>
        </el-form-item>

        <el-form-item label="正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="详细记录你的行程、避坑经验、预算与推荐理由；问答帖请清楚描述你的问题"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item>
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">
            {{ isEdit ? '保存修改' : '发布' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { circleApi, postApi } from '@/api'
import { ROUTE_MODE_MAP, splitImages } from '@/utils/format'
import { loadDict, optionsOf } from '@/utils/tagDict'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)
const formRef = ref()
const circles = ref([])
const imageList = ref([''])

// 攻略标签选项，取自POST作用域的字典
const avoidList = ref([])
const suitableList = ref([])
const avoidOptions = computed(() => optionsOf('POST', 'avoid'))
const suitableOptions = computed(() => optionsOf('POST', 'suitable'))

const cityOptions = ['长沙', '张家界', '重庆', '成都', '西安', '厦门', '武汉', '广州']

const form = ref({
  type: 'GUIDE',
  title: '',
  circleId: route.query.circleId || '',
  city: '',
  destination: '',
  modeTag: '',
  costPerPerson: undefined,
  days: undefined,
  content: ''
})

const rules = {
  type: [{ required: true, message: '选择帖子类型', trigger: 'change' }],
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 4, message: '标题至少4个字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请填写正文', trigger: 'blur' },
    { min: 5, message: '正文至少5个字', trigger: 'blur' }
  ]
}

async function loadForEdit() {
  loading.value = true
  try {
    const p = await postApi.detail(route.params.id)
    form.value = {
      type: p.type || 'GUIDE',
      title: p.title || '',
      circleId: p.circleId || '',
      city: p.city || '',
      destination: p.destination || '',
      modeTag: p.modeTag || '',
      costPerPerson: p.costPerPerson ?? undefined,
      days: p.days ?? undefined,
      content: p.content || ''
    }
    avoidList.value = splitCsv(p.avoidTags)
    suitableList.value = splitCsv(p.suitableFor)
    imageList.value = splitImages(p.images)
    if (!imageList.value.length) imageList.value = ['']
  } finally {
    loading.value = false
  }
}

function splitCsv(v) {
  if (!v) return []
  return String(v)
    .split(/[,，;；\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function submit() {
  await formRef.value.validate()
  const payload = {
    ...form.value,
    circleId: form.value.circleId || null,
    costPerPerson: form.value.costPerPerson ?? null,
    days: form.value.days ?? null,
    avoidTags: avoidList.value.join(','),
    suitableFor: suitableList.value.join(','),
    images: imageList.value.map((u) => u.trim()).filter(Boolean).join(',')
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await postApi.update(route.params.id, payload)
      ElMessage.success('修改已保存')
      router.replace(`/posts/${route.params.id}`)
    } else {
      const data = await postApi.create(payload)
      ElMessage.success('发布成功，等待审核后将公开可见')
      router.replace(data?.id ? `/posts/${data.id}` : '/posts')
    }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  // 先载入POST字典，拿到避坑主题与合适人群的选项
  await loadDict('POST')
  const data = await circleApi.list().catch(() => [])
  circles.value = data?.list || data || []
  if (isEdit.value) loadForEdit()
})
</script>
