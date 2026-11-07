<template>
  <div class="pf">
    <div class="pf-block-title">🎯 想去哪 · 玩几天</div>

    <el-form label-position="top" size="small" class="pf-form">
      <el-form-item label="目的地">
        <el-select
          v-model="plan.city"
          filterable
          placeholder="选择城市"
          style="width: 100%"
          @change="plan.selectCity"
        >
          <el-option v-for="c in plan.cities" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>

      <div class="pf-row">
        <el-form-item label="行程天数" class="pf-half">
          <el-input-number v-model="plan.tripDays" :min="1" :max="7" style="width: 100%" />
        </el-form-item>
        <el-form-item label="出发时间" class="pf-half">
          <el-time-select
            v-model="plan.startTime"
            start="06:00"
            step="00:30"
            end="21:00"
            placeholder="08:30"
            style="width: 100%"
          />
        </el-form-item>
      </div>

      <el-form-item label="游玩模式">
        <el-select v-model="plan.mode" style="width: 100%">
          <el-option v-for="(name, key) in ROUTE_MODE_MAP" :key="key" :label="name" :value="key" />
        </el-select>
      </el-form-item>

      <el-form-item label="预算档位">
        <el-select v-model="plan.budgetLevel" style="width: 100%">
          <el-option v-for="b in BUDGETS" :key="b.value" :label="b.label" :value="b.value" />
        </el-select>
      </el-form-item>

      <el-form-item label="出发景点">
        <el-select
          v-model="plan.startSpotId"
          clearable
          placeholder="默认从热度最高景点出发"
          style="width: 100%"
        >
          <el-option v-for="s in plan.spots" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="选点方式">
        <el-radio-group v-model="plan.spotSelect" size="small" style="width: 100%">
          <el-radio-button label="AUTO" style="flex: 1">🤖 AI 选点</el-radio-button>
          <el-radio-button label="MANUAL" style="flex: 1">✋ 手动勾选</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <el-button
      type="primary"
      style="width: 100%"
      :icon="Aim"
      :loading="plan.planning"
      :disabled="!plan.planSpotCount"
      @click="plan.generate"
    >
      {{ plan.tripDays > 1 ? `一键规划${plan.tripDays} 天行程` : '一键规划行程' }}
    </el-button>

    <div class="sub-text pf-hint" style="line-height: 1.9">
      <template v-if="plan.spotSelect === 'AUTO'">
        🤖 由 AI 按热度自动选点（全城{{ plan.spots.length }}个候选）
      </template>
      <template v-else>
        ✋ 已勾选{{ plan.selectedSpotIds.length }} / {{ plan.spots.length }}个景点参与规划
      </template>
    </div>

    <!-- 关联结伴行程：动态调整后会同步通知同行成员 -->
    <div v-if="auth.isLoggedIn" class="pf-trip">
      <div class="sub-text" style="margin-bottom: 6px">关联结伴行程（天气变化后自动通知同行成员）</div>
      <el-select
        v-model="plan.tripId"
        clearable
        placeholder="不关联"
        size="small"
        style="width: 100%"
      >
        <el-option v-for="t in plan.myCaptainTrips" :key="t.id" :label="t.title" :value="t.id" />
      </el-select>
    </div>
  </div>
</template>

<script setup>
import { Aim } from '@element-plus/icons-vue'
import { BUDGETS, ROUTE_MODE_MAP } from '@/utils/format'
import { useAuthStore } from '@/store/auth'
import { useTripPlan } from '@/composables/useTripPlan'

const plan = useTripPlan()
const auth = useAuthStore()
</script>

<style scoped>
.pf-block-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 10px;
  color: var(--xlvq-text);
}
.pf :deep(.el-form-item) {
  margin-bottom: 10px;
}
.pf :deep(.el-form-item__label) {
  font-size: 12px;
  color: #6b7a90;
  padding-bottom: 2px;
  line-height: 1.6;
}
.pf-row {
  display: flex;
  gap: 8px;
}
.pf-half {
  flex: 1;
  min-width: 0;
}
.pf-hint {
  margin-top: 8px;
}
/* 选点方式两个按钮平分宽度 */
.pf :deep(.el-radio-group) {
  display: flex;
}
.pf :deep(.el-radio-button__inner) {
  width: 100%;
  padding-left: 0;
  padding-right: 0;
}
.pf-trip {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--xlvq-border);
}
</style>
