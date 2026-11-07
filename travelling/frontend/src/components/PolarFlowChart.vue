<template>
  <div ref="wrapEl" class="polar-embed" :style="{ height: wrapHeight + 'px' }">
    <iframe :src="src" :style="frameStyle" title="学旅圈 · 城市景点总人流量分布（极坐标变种图）"></iframe>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 极坐标图：把public/polar-flow.html的原版页面用iframe嵌入。
 * 原页面固定1500px宽，这里按容器宽度等比缩放，避免应用内出现横向滚动条。
 */
const PAGE_W = 1500
const PAGE_H = 1370 // 原版页面完整高度（标题区+画布1120+表格+页脚）

const src = `${import.meta.env.BASE_URL}polar-flow.html`
const wrapEl = ref(null)
const scale = ref(1)
let ro = null

function updateScale() {
  const w = wrapEl.value?.clientWidth || PAGE_W
  scale.value = Math.min(1, w / PAGE_W)
}

onMounted(() => {
  updateScale()
  ro = new ResizeObserver(updateScale)
  if (wrapEl.value) ro.observe(wrapEl.value)
})
onBeforeUnmount(() => ro?.disconnect())

const wrapHeight = computed(() => Math.round(PAGE_H * scale.value))
const frameStyle = computed(() => ({
  width: PAGE_W + 'px',
  height: PAGE_H + 'px',
  transform: `scale(${scale.value})`,
  transformOrigin: 'top left'
}))
</script>

<style scoped>
.polar-embed {
  /* 原版页面底色，嵌入后与卡片无缝衔接 */
  position: relative;
  background: #f7f4ee;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(31, 42, 90, 0.06);
}
.polar-embed iframe {
  display: block;
  border: 0;
}
</style>
