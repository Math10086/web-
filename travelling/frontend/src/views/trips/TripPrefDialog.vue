<template>
  <el-dialog
    :model-value="modelValue"
    title="🎯 我的结伴偏好"
    width="560px"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="load"
  >
    <el-alert
      type="info"
      :closable="false"
      class="mb-16"
      title="填写偏好后，系统会按「天数、节奏、目的地、兴趣、预算、出行方式」为你计算与各队伍的匹配度。没填的项会由下方向上推断的行为画像自动补全。"
      show-icon
    />

    <!-- 动态兴趣画像：系统从历史行为里推断，手填优先级更高 -->
    <div class="derived-box">
      <div class="derived-head">
        <span class="derived-title">🧠 系统从你的行为里读到的兴趣</span>
        <el-tag v-if="profile" size="small" :type="confidenceType" effect="plain">
          {{ confidenceLabel }}
        </el-tag>
      </div>

      <template v-if="profile && knownTags.length">
        <div class="derived-tags">
          <el-tooltip
            v-for="t in knownTags.slice(0, 8)"
            :key="t.tag"
            :content="`强度${t.percent}% · 来源：${t.sources}`"
            placement="top"
          >
            <span
              class="derived-tag"
              :class="{ adopted: tagList.includes(t.tag) }"
              @click="adopt(t.tag)"
            >
              {{ t.tag }}<em>{{ t.percent }}%</em>
            </span>
          </el-tooltip>
          <el-button link type="primary" size="small" @click="adoptAll">全部加入</el-button>
        </div>
        <ul v-if="profile.evidence.length" class="derived-evidence">
          <li v-for="e in profile.evidence" :key="e">{{ e }}</li>
        </ul>
        <div v-if="autoFilled.length" class="derived-auto">
          以下项目你还没填，已按画像自动补全参与匹配：{{ autoFilled.join(' · ') }}
        </div>
        <div class="derived-tip">点标签可加入下方「兴趣标签」，手填的标签始终优先于推断结果。</div>
      </template>

      <div v-else class="derived-empty">
        还没有足够的行为数据。记一条足迹、发一篇游记或报名一支队伍后，这里会自动长出你的兴趣画像。
      </div>
    </div>

    <el-form :model="form" label-width="110px">
      <el-form-item label="预期游玩天数">
        <el-input-number v-model="form.expectDays" :min="1" :max="15" />
        <span class="sub-text" style="margin-left: 8px">天（留空表示不限）</span>
      </el-form-item>

      <el-form-item label="游玩节奏">
        <el-radio-group v-model="form.paceType">
          <el-radio v-for="p in PACE_OPTIONS" :key="p.value" :label="p.value">
            {{ p.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="目的地偏好">
        <el-input v-model="form.destinationPref" placeholder="如：张家界（留空表示不限）" />
      </el-form-item>

      <el-form-item label="兴趣标签">
        <el-checkbox-group v-model="tagList">
          <el-checkbox v-for="t in TRIP_INTEREST_TAGS" :key="t" :label="t">{{ t }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="人均预算上限">
        <el-input-number v-model="form.budget" :min="0" :step="100" :precision="0" />
        <span class="sub-text" style="margin-left: 8px">元/人</span>
      </el-form-item>

      <el-form-item label="偏好出行方式">
        <el-select v-model="form.transport" placeholder="不限" clearable style="width: 220px">
          <el-option
            v-for="(label, key) in TRANSPORT_MODE_MAP"
            :key="key"
            :label="label"
            :value="key"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="跨校组队">
        <el-switch v-model="form.openToCrossSchool" active-text="接受三校跨校队伍推荐" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存偏好</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { tripApi } from '@/api'
import { PACE_OPTIONS, TRANSPORT_MODE_MAP, TRIP_INTEREST_TAGS, splitTags } from '@/utils/format'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const tagList = ref([])
const profile = ref(null)
const form = ref({
  expectDays: null,
  paceType: 'BALANCED',
  destinationPref: '',
  budget: null,
  transport: '',
  openToCrossSchool: true
})

const confidenceLabel = computed(() => {
  const map = { HIGH: '画像清晰', MEDIUM: '初步画像', LOW: '数据较少', NONE: '暂无数据' }
  return map[profile.value?.confidence] || '暂无数据'
})
const confidenceType = computed(() => {
  const map = { HIGH: 'success', MEDIUM: 'primary', LOW: 'warning', NONE: 'info' }
  return map[profile.value?.confidence] || 'info'
})

/** 只展示词表内、强度≥20% 的标签：太弱的信号列出来反而像噪声 */
const knownTags = computed(() =>
  (profile.value?.tags || []).filter((t) => TRIP_INTEREST_TAGS.includes(t.tag) && t.percent >= 20)
)

/** 标出哪些字段是画像自动补充的（用户没手动填） */
const autoFilled = computed(() => {
  const p = profile.value?.derivedPref
  if (!p) return []
  const out = []
  if (!form.value.expectDays && p.expectDays) out.push(`${p.expectDays} 天`)
  if (!form.value.budget && p.budget) out.push(`预算 ¥${p.budget}`)
  if (!form.value.destinationPref && p.destinationPref) out.push(`目的地${p.destinationPref}`)
  if (!form.value.transport && p.transport) out.push(TRANSPORT_MODE_MAP[p.transport] || p.transport)
  return out
})

function adopt(tag) {
  if (!tagList.value.includes(tag)) {
    tagList.value.push(tag)
  }
}
function adoptAll() {
  knownTags.value.slice(0, 8).forEach((t) => adopt(t.tag))
}

async function load() {
  const [p, prof] = await Promise.all([
    tripApi.getPref().catch(() => null),
    tripApi.getPrefProfile().catch(() => null)
  ])
  profile.value = prof
  if (!p) return
  // 只回填「手填过的」值：接口返回的有效值里混着画像推断的结果，
  // 若直接回填，用户一保存就把推断值写死成了自己选的。
  const raw = p.manual || {}
  form.value = {
    expectDays: raw.expectDays ?? null,
    paceType: raw.paceType || 'BALANCED',
    destinationPref: raw.destinationPref || '',
    budget: raw.budget ?? null,
    transport: raw.transport || '',
    openToCrossSchool: raw.openToCrossSchool !== false
  }
  tagList.value = splitTags(raw.interestTags)
}

async function submit() {
  saving.value = true
  try {
    await tripApi.savePref({ ...form.value, interestTags: tagList.value.join(',') })
    ElMessage.success('偏好已保存，推荐结果会随之更新')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.derived-box {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px dashed var(--xlvq-primary);
  border-radius: 10px;
  background: var(--xlvq-primary-light);
}

.derived-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.derived-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--xlvq-text);
}

.derived-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.derived-tag {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid rgba(47, 128, 237, 0.35);
  border-radius: 999px;
  background: #fff;
  color: var(--xlvq-primary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.derived-tag:hover {
  border-color: var(--xlvq-primary);
  box-shadow: var(--xlvq-shadow);
}

.derived-tag em {
  font-style: normal;
  font-size: 11px;
  color: var(--xlvq-text-sub);
}

.derived-tag.adopted {
  background: var(--xlvq-primary);
  color: #fff;
}

.derived-tag.adopted em {
  color: rgba(255, 255, 255, 0.85);
}

.derived-evidence {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--xlvq-text-sub);
  line-height: 1.7;
}

.derived-tip,
.derived-empty {
  margin-top: 8px;
  font-size: 12px;
  color: var(--xlvq-text-sub);
  line-height: 1.6;
}

.derived-auto {
  margin-top: 6px;
  font-size: 12px;
  color: var(--xlvq-orange);
  line-height: 1.6;
}
</style>
