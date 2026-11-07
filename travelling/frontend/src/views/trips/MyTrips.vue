<template>
  <div class="page-wrap">
    <h2 class="page-title">🧳 我的结伴行程</h2>
    <el-tabs v-model="activeTab" @tab-change="load">
      <el-tab-pane label="我发起的" name="created" />
      <el-tab-pane label="我加入的" name="joined" />
    </el-tabs>

    <el-empty v-if="!loading && !list.length" :description="activeTab === 'created' ? '还没有发起过行程' : '还没有加入任何行程'" />

    <div v-loading="loading" class="trip-grid">
      <div v-for="t in list" :key="t.id" class="x-card hoverable" @click="$router.push(`/trips/${t.id}`)">
        <div class="flex-between mb-12">
          <el-tag :type="TRIP_STATUS_TAG[t.status] || 'info'">
            {{ TRIP_STATUS_MAP[t.status] || t.status }}
          </el-tag>
          <el-tag :type="RISK_TAG[t.riskLevel] || 'success'" effect="plain">
            {{ RISK_MAP[t.riskLevel] || '低风险' }}
          </el-tag>
        </div>
        <div class="trip-title">{{ t.title }}</div>
        <div class="sub-text mt-12" style="line-height: 2">
          <div>📍 {{ t.destination }}</div>
          <div>🕒 {{ formatTime(t.departAt) }}</div>
          <div>
            👥 {{ t.approvedCount ?? t.currentMembers ?? 1 }}/{{ t.maxMembers }}人
          </div>
        </div>
        <div v-if="activeTab === 'joined' && t.myMemberStatus" class="mt-12">
          <el-tag :type="MEMBER_STATUS_TAG[t.myMemberStatus] || 'info'" size="small">
            {{ MEMBER_STATUS_MAP[t.myMemberStatus] || t.myMemberStatus }}
          </el-tag>
        </div>
        <div v-if="activeTab === 'created'" class="mt-12 sub-text">
          待审核：
          <el-tag type="warning" size="small">
            {{ (t.members || []).filter((m) => m.status === 'PENDING').length }}人
          </el-tag>
        </div>
        <div class="mt-12 flex gap-8">
          <el-button
            size="small"
            type="primary"
            plain
            @click.stop="$router.push(`/trips/${t.id}/team`)"
          >
            🧭 团队管理
          </el-button>
          <el-button size="small" @click.stop="$router.push(`/trips/${t.id}`)">查看详情</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { tripApi } from '@/api'
import {
  formatTime,
  MEMBER_STATUS_MAP,
  MEMBER_STATUS_TAG,
  RISK_MAP,
  RISK_TAG,
  TRIP_STATUS_MAP,
  TRIP_STATUS_TAG
} from '@/utils/format'

const activeTab = ref('created')
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await tripApi.my()
    if (Array.isArray(data)) {
      // 兼容直接返回数组：用captainId区分
      list.value = data.filter((t) =>
        activeTab.value === 'created' ? t.iAmCaptain : !t.iAmCaptain
      )
      if (!list.value.length) {
        list.value = activeTab.value === 'created' ? data : []
      }
    } else if (data) {
      list.value =
        activeTab.value === 'created'
          ? data.created || data.captainTrips || []
          : data.joined || data.memberTrips || []
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.trip-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.trip-title {
  font-weight: 700;
  font-size: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
@media (max-width: 1100px) {
  .trip-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 600px) {
  .trip-grid {
    grid-template-columns: 1fr;
  }
}
</style>
