<template>
  <div class="x-card">
    <div class="card-title">目的地天气{{ plan.city ? `（${plan.city}）` : '' }}</div>
    <el-skeleton v-if="plan.weatherLoading" :rows="3" animated />
    <template v-else-if="plan.weather">
      <div class="weather-row">
        <div
          v-for="w in plan.weather.days"
          :key="w.date"
          class="weather-item"
          :class="{ bad: isBad(w) }"
        >
          <div class="w-week">{{ w.weekday }}</div>
          <div class="w-icon">{{ w.icon }}</div>
          <div class="w-text">{{ w.text }}</div>
          <div class="w-temp">{{ w.tempMin }}~{{ w.tempMax }}℃</div>
          <div class="w-rain">降水{{ w.rainChance }}%</div>
        </div>
      </div>
      <div class="sub-text" style="margin-top: 8px">{{ plan.weather.summary }}</div>
      <div class="sub-text" style="font-size: 12px; margin-top: 4px">
        数据来源：{{ plan.weather.source }}
      </div>
    </template>
    <el-empty v-else description="选择目的地后自动加载天气" :image-size="70" />
  </div>
</template>

<script setup>
import { useTripPlan } from '@/composables/useTripPlan'

const plan = useTripPlan()

/** 判定「不利天气」：降水概率 >= 60% 或 出现雨/雪/雷 */
function isBad(w) {
  return (w.rainChance || 0) >= 60 || /雨|雪|雷/.test(w.text || '')
}
</script>

<style scoped>
.weather-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}
.weather-item {
  flex: 1;
  min-width: 76px;
  text-align: center;
  padding: 10px 6px;
  border-radius: 10px;
  background: #f7f9fc;
  border: 1px solid var(--xlvq-border);
}
.weather-item.bad {
  background: #fdf6ec;
  border-color: #f3d19e;
}
.w-week {
  font-weight: 600;
  font-size: 13px;
}
.w-icon {
  font-size: 24px;
  margin: 4px 0;
}
.w-text,
.w-rain {
  font-size: 12px;
  color: var(--xlvq-text-sub);
}
.w-temp {
  font-weight: 600;
  font-size: 13px;
}
</style>
