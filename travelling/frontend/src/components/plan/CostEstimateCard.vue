<template>
  <div class="x-card">
    <div class="card-title">全程费用估算</div>
    <div class="sub-text mb-12">
      依据：{{ plan.city || '—' }} · {{ plan.tripDays }}天 ·
      {{ budgetLabel }}（基于采集到的交通 / 住宿 / 门票 / 餐饮参考价）
    </div>

    <el-skeleton v-if="plan.estLoading" :rows="3" animated />
    <template v-else-if="plan.estimate">
      <el-alert
        :type="plan.estimate.withinBudget ? 'success' : 'warning'"
        :closable="false"
        class="mb-12"
        :title="
          plan.estimate.withinBudget
            ? `在「${plan.estimate.budgetLabel}」预算内（人均约 ¥${plan.estimate.perPersonLow} - ${plan.estimate.perPersonHigh}）`
            : `超出「${plan.estimate.budgetLabel}」（人均约 ¥${plan.estimate.perPersonLow} - ${plan.estimate.perPersonHigh}）`
        "
        show-icon
      />
      <div class="est-list">
        <div v-for="it in plan.estimate.items" :key="it.name" class="est-item">
          <div class="est-line">
            <span class="est-name">{{ it.name }}</span>
            <span class="est-price">¥{{ it.low }} - {{ it.high }}</span>
          </div>
          <div v-if="it.note" class="sub-text" style="font-size: 12px">{{ it.note }}</div>
        </div>
      </div>
      <div class="est-total">
        <span>人均合计</span>
        <span class="est-total-value">¥{{ plan.estimate.totalLow }} - {{ plan.estimate.totalHigh }}</span>
      </div>
      <div v-if="plan.estimate.note" class="sub-text" style="font-size: 12px; margin-top: 8px">
        {{ plan.estimate.note }}
      </div>
    </template>
    <el-empty v-else description="选择目的地后自动估算费用" :image-size="70" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { BUDGETS } from '@/utils/format'
import { useTripPlan } from '@/composables/useTripPlan'

const plan = useTripPlan()

const budgetLabel = computed(
  () => BUDGETS.find((b) => b.value === plan.budgetLevel)?.label || '不限预算'
)
</script>

<style scoped>
.est-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.est-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--xlvq-border);
}
.est-item:last-child {
  border-bottom: none;
}
.est-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.est-name {
  font-weight: 600;
  font-size: 13px;
}
.est-price {
  color: var(--xlvq-orange);
  font-weight: 700;
}
.est-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--xlvq-border);
  font-weight: 700;
}
.est-total-value {
  color: var(--xlvq-primary);
  font-size: 18px;
}
</style>
