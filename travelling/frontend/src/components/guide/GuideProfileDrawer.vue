<template>
  <el-drawer
    :model-value="modelValue"
    direction="rtl"
    size="520px"
    :with-header="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="load"
  >
    <div v-loading="loading" class="gp-wrap">
      <template v-if="data">
        <!-- 头部 -->
        <div class="gp-head">
          <el-avatar :size="64" :src="data.avatar">{{ (data.nickname || '?').charAt(0) }}</el-avatar>
          <div style="flex: 1; min-width: 0">
            <div class="gp-name">
              {{ data.nickname }}
              <el-tag size="small" :type="data.supplyTier === 'CERTIFIED_GUIDE' ? 'success' : 'warning'">
                {{ data.supplyTierLabel }}
              </el-tag>
              <el-tag v-if="data.verified" size="small" type="primary" effect="plain">三校认证</el-tag>
            </div>
            <div class="sub-text" style="margin-top: 4px">
              {{ SCHOOL_MAP[data.school] || data.school || '三校达人' }}
            </div>
            <div style="margin-top: 6px">
              <el-rate :model-value="Number(data.ratingAvg) || 0" disabled size="small" show-score />
              <span class="sub-text" style="margin-left: 8px">
                {{ data.stats?.reviewCount || 0 }}条评价 · 接待{{ data.stats?.completedCount || 0 }}次
              </span>
            </div>
          </div>
          <el-button link :icon="Close" @click="emit('update:modelValue', false)" />
        </div>

        <!-- 数据条 -->
        <div class="gp-kpi">
          <div class="gp-kpi-item">
            <div class="gp-kpi-num">{{ data.stats?.completedCount || 0 }}</div>
            <div class="sub-text">已接待</div>
          </div>
          <div class="gp-kpi-item">
            <div class="gp-kpi-num">{{ data.stats?.goodRate == null ? '—' : data.stats.goodRate + '%' }}</div>
            <div class="sub-text">好评率（4星以上）</div>
          </div>
          <div class="gp-kpi-item">
            <div class="gp-kpi-num">{{ data.stats?.reviewAvg ?? '—' }}</div>
            <div class="sub-text">平均评分</div>
          </div>
          <div class="gp-kpi-item">
            <div class="gp-kpi-num">{{ data.stats?.cityCount || 0 }}</div>
            <div class="sub-text">覆盖城市</div>
          </div>
        </div>

        <!-- 简介 -->
        <div class="gp-section">
          <div class="gp-title">关于这位向导</div>
          <div class="gp-intro">{{ data.intro || '这位向导还没有填写自我介绍' }}</div>
          <div class="mt-12">
            <el-tag
              v-for="t in splitThemes(data.themes)"
              :key="t"
              size="small"
              type="info"
              effect="plain"
              class="gp-tag"
            >
              {{ t }}
            </el-tag>
          </div>
          <div v-if="data.stats?.cities?.length" class="sub-text mt-12">
            📍 常带线路：{{ data.stats.cities.join(' · ') }}
          </div>
        </div>

        <!-- 评价分布 -->
        <div class="gp-section">
          <div class="gp-title">评价分布</div>
          <div v-if="!data.stats?.reviewCount" class="sub-text">还没有游客留下评价</div>
          <div v-else class="gp-dist">
            <div v-for="d in data.ratingDistribution" :key="d.star" class="gp-dist-row">
              <span class="gp-dist-star">{{ d.star }}星</span>
              <div class="gp-dist-bar">
                <div class="gp-dist-fill" :style="{ width: d.ratio + '%' }" />
              </div>
              <span class="sub-text" style="width: 52px; text-align: right">{{ d.count }}条</span>
            </div>
          </div>
        </div>

        <!-- 游客评价 -->
        <div class="gp-section">
          <div class="gp-title">大家怎么说（{{ data.reviews?.length || 0 }}）</div>
          <el-empty v-if="!data.reviews?.length" description="暂无评价" :image-size="60" />
          <div v-for="(r, i) in data.reviews" :key="i" class="gp-review">
            <div class="flex-between">
              <span>
                <el-rate :model-value="r.rating" disabled size="small" />
                <span class="gp-review-user">{{ r.traveler }}</span>
              </span>
              <span class="sub-text">{{ r.date }}</span>
            </div>
            <div class="gp-review-text">{{ r.content || '（未填写文字评价）' }}</div>
            <div class="sub-text" style="font-size: 12px">
              {{ r.destination }} · {{ r.serviceTitle }}
            </div>
          </div>
        </div>

        <!-- 过往经历 -->
        <div class="gp-section">
          <div class="gp-title">过往接待经历（{{ data.experiences?.length || 0 }}）</div>
          <el-empty
            v-if="!data.experiences?.length"
            description="还没有完成的接待记录，这位向导是新人"
            :image-size="60"
          />
          <el-timeline v-else>
            <el-timeline-item
              v-for="e in data.experiences"
              :key="e.id"
              :timestamp="String(e.tripDate || '')"
              placement="top"
              :type="e.reviewed ? 'success' : 'primary'"
            >
              <div style="font-weight: 600">{{ e.serviceTitle }}</div>
              <div class="sub-text">📍 {{ e.destination }}<span v-if="e.themes"> ｜ {{ e.themes }}</span></div>
              <div class="sub-text" style="font-size: 12px">
                <template v-if="e.rating">
                  <el-rate :model-value="e.rating" disabled size="small" style="display: inline-block" />
                  已评价
                </template>
                <template v-else>已完成 · 游客未评价</template>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 在售服务 -->
        <div class="gp-section">
          <div class="gp-title">可预约的服务</div>
          <el-empty
            v-if="!data.services?.length"
            description="这位向导暂未发布可预约服务"
            :image-size="60"
          />
          <div v-for="s in data.services" :key="s.id" class="gp-service">
            <div class="flex-between">
              <span style="font-weight: 700">{{ s.title }}</span>
              <span class="gp-price">¥{{ s.price }}<span class="sub-text">/次</span></span>
            </div>
            <div class="sub-text mt-8">📍 {{ s.destination }}<span v-if="s.themes"> ｜ {{ s.themes }}</span></div>
            <div v-if="s.schedule" class="sub-text">🗓️ {{ s.schedule }}</div>
            <el-button
              size="small"
              type="primary"
              round
              style="margin-top: 8px"
              @click="emit('book', s)"
            >
              立即预约
            </el-button>
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="没有找到这位向导的公开资料" />
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { guideApi } from '@/api'
import { SCHOOL_MAP } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  userId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'book'])

const loading = ref(false)
const data = ref(null)

function splitThemes(t) {
  if (!t) return []
  return String(t).split(/[,，、]/).filter(Boolean)
}

async function load() {
  if (!props.userId) return
  loading.value = true
  data.value = null
  try {
    data.value = await guideApi.publicProfile(props.userId)
  } catch (e) {
    data.value = null
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.gp-wrap {
  padding: 20px 22px 30px;
  min-height: 200px;
}
.gp-head {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--xlvq-border);
}
.gp-name {
  font-size: 18px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.gp-kpi {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 14px 0 4px;
}
.gp-kpi-item {
  text-align: center;
  padding: 10px 4px;
  background: #f7f9fc;
  border-radius: 10px;
}
.gp-kpi-num {
  font-size: 18px;
  font-weight: 800;
  color: var(--xlvq-primary);
}
.gp-section {
  margin-top: 18px;
}
.gp-title {
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 10px;
}
.gp-intro {
  font-size: 13px;
  line-height: 1.8;
  color: var(--xlvq-text);
}
.gp-tag {
  margin-right: 6px;
  margin-bottom: 6px;
}
.gp-dist-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.gp-dist-star {
  font-size: 12px;
  color: var(--xlvq-text-sub);
  width: 34px;
}
.gp-dist-bar {
  flex: 1;
  height: 8px;
  background: #eef1f6;
  border-radius: 4px;
  overflow: hidden;
}
.gp-dist-fill {
  height: 100%;
  background: linear-gradient(90deg, #ffb74d, #ff8a00);
  border-radius: 4px;
}
.gp-review {
  padding: 10px 12px;
  background: #fbfcfe;
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  margin-bottom: 8px;
}
.gp-review-user {
  margin-left: 6px;
  font-size: 12px;
  color: var(--xlvq-text-sub);
}
.gp-review-text {
  font-size: 13px;
  line-height: 1.7;
  margin: 6px 0 4px;
}
.gp-service {
  border: 1px solid var(--xlvq-border);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
}
.gp-price {
  color: #f5222d;
  font-weight: 800;
}
.mt-8 {
  margin-top: 8px;
}
</style>
