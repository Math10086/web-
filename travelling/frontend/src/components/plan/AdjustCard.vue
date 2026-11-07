<template>
  <div class="x-card">
    <div class="card-title">动态行程调整</div>
    <div class="sub-text mb-12">
      接入天气等外部因素，遇变化时生成调整后行程与理由，并可通知同行成员
    </div>
    <el-button
      type="warning"
      plain
      :disabled="!plan.city"
      :loading="plan.adjusting"
      style="width: 100%"
      @click="plan.doAdjust"
    >
      按天气生成调整方案
    </el-button>

    <template v-if="plan.adjust">
      <el-alert
        type="warning"
        :closable="false"
        class="mt-12"
        :title="plan.adjust.summary"
        show-icon
      />
      <div v-for="d in plan.adjust.adjustedDays" :key="d.day" class="adj-item">
        <div class="adj-head">
          <span class="day-badge" :style="{ background: d.bad ? '#e6a23c' : '#67c23a' }">
            Day {{ d.day }}
          </span>
          <span class="sub-text">{{ d.weekday }} · {{ d.weather?.text }}</span>
          <el-tag size="small" :type="d.bad ? 'warning' : 'success'" effect="plain">
            {{
              d.bad
                ? d.action === 'SWAP_INDOOR'
                  ? '建议室内替换'
                  : '建议顺延'
                : '维持不变'
            }}
          </el-tag>
        </div>
        <div class="adj-suggest">{{ d.suggestion }}</div>
        <div v-if="d.alternatives?.length" class="day-spots">
          <el-tag
            v-for="a in d.alternatives"
            :key="a.id"
            size="small"
            type="warning"
            effect="plain"
          >
            {{ a.name }}
          </el-tag>
        </div>
      </div>
      <div v-if="plan.adjust.notified" class="sub-text" style="margin-top: 8px">
        ✅ 已通知{{ plan.adjust.notified }}位同行成员
      </div>
    </template>
  </div>
</template>

<script setup>
import { useTripPlan } from '@/composables/useTripPlan'

const plan = useTripPlan()
</script>

<style scoped>
.adj-item {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fbfcfe;
  border: 1px solid var(--xlvq-border);
}
.adj-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.day-badge {
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
}
.adj-suggest {
  font-size: 13px;
  margin-top: 6px;
  color: var(--xlvq-text);
  line-height: 1.6;
}
.day-spots {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
