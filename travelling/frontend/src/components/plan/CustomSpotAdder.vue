<template>
  <div class="csa">
    <div class="csa-head">
      <span class="csa-title">➕ 添加想去的地点</span>
      <span v-if="plan.activeCustomSpots.length" class="csa-count">
        {{ plan.activeCustomSpots.length }}个
      </span>
    </div>

    <div class="csa-sub">
      不在景点库里也能加：搜到地名即可，规划时一定会被排进路线。
    </div>

    <div class="csa-form">
      <el-input
        v-model="keyword"
        size="small"
        :placeholder="`在「${plan.city}」搜索，如：天府广场`"
        clearable
        @keyup.enter="doSearch"
      />
      <el-button size="small" type="primary" :loading="searching" @click="doSearch">
        搜索
      </el-button>
    </div>

    <!-- 搜索结果：点一条即加入 -->
    <div v-if="results.length" class="csa-results">
      <div v-for="(r, i) in results" :key="i" class="csa-result" @click="pick(r)">
        <div class="csa-r-main">
          <div class="csa-r-title">{{ r.title }}</div>
          <div class="csa-r-addr">{{ r.address || r.city || '—' }}</div>
        </div>
        <span class="csa-r-add">＋</span>
      </div>
    </div>
    <div v-else-if="searched && !searching" class="csa-hint" :class="{ 'csa-quota': quotaOut }">
      {{ failText }}
      <div v-if="quotaOut" class="csa-quota-tip">
        💡 替代方案：点地图右上角 <strong>✚</strong> 开启「点图添加」，
        在地图上直接点出这个位置、起个名字即可加入行程。
      </div>
    </div>

    <!-- 已添加：可单个删除，也可整城清空 -->
    <div v-if="plan.activeCustomSpots.length" class="csa-chips">
      <el-tag
        v-for="s in plan.activeCustomSpots"
        :key="s.key"
        closable
        type="info"
        size="small"
        @close="plan.removeCustomSpot(s.key)"
      >
        ✚ {{ s.name }}
      </el-tag>
      <el-button link type="primary" size="small" @click="plan.clearCustomSpots">
        清空
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { geoApi } from '@/api'
import { useTripPlan } from '@/composables/useTripPlan'

const plan = useTripPlan()

const keyword = ref('')
const results = ref([])
const searching = ref(false)
const searched = ref(false)
const failText = ref('')
/** 腾讯公共演示key当日额度用完时置真，提示改用「点图添加」 */
const quotaOut = ref(false)

/** 地点搜索：后端优先调腾讯位置服务，配额用尽时自动降级为景点库模糊匹配 */
async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('先输入一个地名')
    return
  }
  searching.value = true
  searched.value = false
  results.value = []
  quotaOut.value = false
  try {
    const data = await geoApi.search(kw, plan.city)
    results.value = (data?.list || []).filter((r) => r.lng && r.lat)
    searched.value = true
    if (!results.value.length) {
      quotaOut.value = data?.source === 'none' && data?.reason === 'quota'
      failText.value = quotaOut.value
        ? `「${kw}」搜不到：腾讯地图搜索接口的公共演示 key 今日额度已用完，景点库里也还没有收录这个地名。`
        : data?.source === 'none'
          ? `没找到「${kw}」，换个更具体的名字试试（比如加上“广场 / 公园 / 景区”）`
          : '没有匹配到带坐标的地点，换个名字再试'
    }
  } catch (e) {
    searched.value = true
    failText.value = '地点搜索暂时不可用，请稍后再试'
  } finally {
    searching.value = false
  }
}

function pick(r) {
  const ok = plan.addCustomSpot({
    name: r.title,
    address: r.address,
    lng: r.lng,
    lat: r.lat,
    city: plan.city
  })
  if (ok) {
    ElMessage.success(`已添加「${r.title}」，点「一键规划行程」即可纳入路线`)
    results.value = []
    keyword.value = ''
    searched.value = false
  }
}
</script>

<style scoped>
.csa {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--xlvq-border);
}
.csa-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.csa-title {
  font-size: 13px;
  font-weight: 700;
}
.csa-count {
  font-size: 11.5px;
  color: #13c2c2;
  background: #13c2c218;
  border-radius: 999px;
  padding: 1px 8px;
}
.csa-sub {
  font-size: 11.5px;
  color: #8a94a6;
  line-height: 1.7;
  margin-top: 4px;
}
.csa-form {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}
.csa-results {
  margin-top: 8px;
  max-height: 156px;
  overflow-y: auto;
  border: 1px solid var(--xlvq-border);
  border-radius: 8px;
}
.csa-result {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  cursor: pointer;
  transition: background 0.18s ease;
}
.csa-result:hover {
  background: #f4f8fe;
}
.csa-r-main {
  flex: 1;
  min-width: 0;
}
.csa-r-title {
  font-size: 12.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.csa-r-addr {
  font-size: 11px;
  color: #8a94a6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.csa-r-add {
  color: #13c2c2;
  font-weight: 700;
  flex: none;
}
.csa-hint {
  margin-top: 8px;
  font-size: 11.5px;
  color: #b06a2c;
  background: #fff7e6;
  border: 1px solid #ffe1b3;
  border-radius: 8px;
  padding: 6px 8px;
  line-height: 1.7;
}
/* 搜索服务额度用完：换更醒目的警示样式+给出「点图添加」替代路径 */
.csa-hint.csa-quota {
  color: #ad4e00;
  background: #fff1f0;
  border-color: #ffccc7;
}
.csa-quota-tip {
  margin-top: 5px;
  padding-top: 5px;
  border-top: 1px dashed #ffd6cf;
  color: #ad4e00;
  line-height: 1.8;
}
.csa-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}
</style>
