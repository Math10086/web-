<template>
  <div class="sp">
    <div class="sp-head" @click="open = !open">
      <span class="sp-title">📍 景点与地图符号</span>
      <span class="sp-count">{{ plan.categories.length }}/{{ categoryList.length }}</span>
      <el-icon class="sp-arrow" :class="{ open }"><ArrowRight /></el-icon>
    </div>

    <div v-show="open" class="sp-body">
      <!-- 分类符号：点击切换地图上是否显示该类 -->
      <div class="cat-chips">
        <span
          v-for="cat in categoryList"
          :key="cat.key"
          class="cat-chip"
          :class="{ off: !plan.categories.includes(cat.key) }"
          @click="plan.toggleCategory(cat.key)"
        >
          <span class="cc-sym" :style="{ color: cat.color }">{{ cat.symbol }}</span>
          {{ cat.label }}
        </span>
      </div>

      <el-input
        v-model="plan.spotKeyword"
        placeholder="筛选景点名称"
        clearable
        size="small"
        class="mt-12"
      />

      <div class="sp-list">
        <div
          v-for="s in plan.filteredSpots"
          :key="s.id"
          class="sp-item"
          @click="$emit('focus', s)"
        >
          <span class="sp-thumb" :style="{ background: `${categoryColor(s.category)}22` }">
            <img v-if="s.image" :src="s.image" :alt="s.name" />
            <span v-else class="sp-sym" :style="{ color: categoryColor(s.category) }">
              {{ categorySymbol(s.category) }}
            </span>
          </span>
          <span class="sp-name">{{ s.name }}</span>
          <span class="sp-heat">🔥{{ s.heat ?? 0 }}</span>
        </div>
        <el-empty v-if="!plan.filteredSpots.length" description="该城市暂无景点" :image-size="60" />
      </div>

      <!-- 自定义添加想去的地点：不在景点库里也能加，规划时一定纳入路线 -->
      <CustomSpotAdder />

      <!-- 手动选点：勾选参与规划的景点（AI 模式下按热度自动选点，不需要勾） -->
      <template v-if="plan.spotSelect === 'MANUAL'">
        <div class="sp-check-head">
          <span>勾选参与规划（{{ plan.selectedSpotIds.length }}）</span>
          <span class="sp-check-ops">
            <el-button link type="primary" size="small" @click="plan.selectAllSpots">全选</el-button>
            <el-button link type="primary" size="small" @click="plan.clearSpots">清空</el-button>
          </span>
        </div>
        <div class="sp-check-list">
          <el-checkbox-group v-model="plan.selectedSpotIds">
            <el-checkbox v-for="s in plan.spots" :key="s.id" :label="s.id" class="sp-check">
              {{ s.name }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { CATEGORY_STYLE } from '@/utils/tmap'
import { useTripPlan } from '@/composables/useTripPlan'
import CustomSpotAdder from '@/components/plan/CustomSpotAdder.vue'

defineEmits(['focus'])

const plan = useTripPlan()
const open = ref(true)

// 分类清单直接由地图符号表派生（原先 地图路线 页面里又抄了一份一模一样的常量）
const categoryList = computed(() =>
  Object.entries(CATEGORY_STYLE).map(([key, v]) => ({ key, ...v }))
)
const categoryMeta = (cat) => CATEGORY_STYLE[cat] || CATEGORY_STYLE.NATURE
const categorySymbol = (cat) => categoryMeta(cat).symbol
const categoryColor = (cat) => categoryMeta(cat).color
</script>

<style scoped>
.sp {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--xlvq-border);
}
.sp-head {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
}
.sp-title {
  font-size: 13px;
  font-weight: 700;
  flex: 1;
}
.sp-count {
  font-size: 11.5px;
  color: #8a94a6;
}
.sp-arrow {
  color: #b6c1d1;
  transition: transform 0.2s ease;
  font-size: 13px;
}
.sp-arrow.open {
  transform: rotate(90deg);
}
.sp-body {
  margin-top: 10px;
}
.cat-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cat-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11.5px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f0f4fa;
  color: #5b6b82;
  cursor: pointer;
  transition: all 0.18s ease;
}
.cat-chip:hover {
  background: #e5edf9;
}
.cat-chip.off {
  background: #fff;
  border: 1px dashed #d8e0ec;
  color: #b6c1d1;
}
.cat-chip.off .cc-sym {
  filter: grayscale(1);
  opacity: 0.5;
}
.cc-sym {
  font-size: 11px;
}
.sp-list {
  max-height: 176px;
  overflow-y: auto;
  margin-top: 8px;
}
.sp-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 6px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.18s ease;
}
.sp-item:hover {
  background: #f4f8fe;
}
.sp-thumb {
  width: 30px;
  height: 24px;
  border-radius: 5px;
  flex: none;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.sp-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.sp-sym {
  font-size: 12px;
}
.sp-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sp-heat {
  font-size: 11.5px;
  color: #ff8a65;
  flex: none;
}
.sp-check-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7a90;
  margin: 10px 0 4px;
  padding-top: 10px;
  border-top: 1px dashed var(--xlvq-border);
}
.sp-check-ops {
  display: inline-flex;
  gap: 4px;
}
.sp-check-list {
  max-height: 150px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.sp-check-list :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
  padding: 2px 0;
}
.sp-check {
  display: flex;
  width: 100%;
}
</style>
