<template>
  <div ref="boxEl" class="swc-box">
    <span
      v-for="w in placed"
      :key="w.name"
      class="swc-word"
      :style="{
        left: w.x + 'px',
        top: w.y + 'px',
        fontSize: w.size + 'px',
        color: w.color,
        fontWeight: w.bold ? 700 : 500,
        transform: 'translate(-50%, -50%) rotate(' + w.rot + 'deg)'
      }"
      :title="`热度${w.value}${w.cat ? ' · ' + w.cat : ''}`"
    >{{ w.name }}</span>
    <!-- 分类图例：按目的地、美食、户外、玩法分组着色 -->
    <div v-if="categorize && legend.length" class="swc-legend">
      <span v-for="c in legend" :key="c.name" class="swc-legend-item">
        <i :style="{ background: c.color }"></i>{{ c.name }}
      </span>
    </div>
    <div v-if="!placed.length && settled" class="swc-empty">暂无词云数据</div>
  </div>
</template>

<script setup>
// 自研轻量词云：纯DOM + 阿基米德螺旋布局。
// 为什么不用echarts-wordcloud插件：它内部import的是echarts的裸核心入口，
// 与项目主入口不是同一模块实例，打包后词云注册进"另一份" echarts，页面静默空白
// （2026-10-18排查结论）。纯DOM实现零依赖、不依赖打包器模块合并，且支持悬停提示。
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  words: { type: Array, default: () => [] },
  minSize: { type: Number, default: 13 },
  maxSize: { type: Number, default: 40 },
  /** 开启后按目的地、美食、户外、玩法分组着色，并显示图例 */
  categorize: { type: Boolean, default: false }
})

// 分类着色：关键词 → 类目（按声明顺序首个命中生效）
const CATS = [
  { name: '目的地', color: '#2F80ED', keys: ['长沙', '张家界', '西安', '成都', '重庆', '凤凰', '岳阳', '衡山', '韶山', '武汉', '桂林', '大理', '厦门', '杭州', '北京', '上海', '广州', '敦煌', '青海', '西藏', '丽江', '贵州', '景德镇', '洛阳'] },
  { name: '美食', color: '#FA8C16', keys: ['美食', '小吃', '火锅', '臭豆腐', '茶颜', '探店', '米粉', '烧烤', '夜市', '奶茶', '湘菜', '小吃街', '面包', '咖啡'] },
  { name: '户外', color: '#52C41A', keys: ['徒步', '露营', '登山', '野路', '骑行', '漂流', '溯溪', '攀岩', '装备', '帐篷', ' canoe', '皮划艇', '滑雪', '热气球'] },
  { name: '玩法', color: '#722ED1', keys: ['拍照', '摄影', '古镇', 'Citywalk', 'citywalk', '汉服', '演出', '温泉', '剧本', '音乐节', '烟花', '慢旅行', '手作', '向导', '研学'] }
]
const OTHER_COLOR = '#8A94A6'

function catOf(name) {
  for (const c of CATS) {
    if (c.keys.some((k) => name.includes(k))) return c
  }
  return null
}

const boxEl = ref(null)
const placed = ref([])
const settled = ref(false)
const legend = ref([])

const COLORS = ['#2F80ED', '#FF8A65', '#52C41A', '#722ED1', '#13C2C2', '#EB2F96', '#FA8C16']

let measureCtx = null
let ro = null

function layout() {
  const box = boxEl.value
  if (!box) return
  const W = box.clientWidth
  const H = box.clientHeight
  if (!W || !H) return
  const data = (props.words || [])
    .filter((w) => w && w.name)
    .map((w) => ({ name: String(w.name), value: Number(w.value) || 1 }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 60)
  if (!data.length) {
    placed.value = []
    settled.value = true
    return
  }

  if (!measureCtx) {
    measureCtx = document.createElement('canvas').getContext('2d')
  }
  const maxV = data[0].value
  const minV = data[data.length - 1].value
  const span = Math.max(1, maxV - minV)

  const rects = []
  const out = []
  const cx = W / 2
  const cy = H / 2

  for (const w of data) {
    const size = Math.round(props.minSize + ((props.maxSize - props.minSize) * (w.value - minV)) / span)
    measureCtx.font = `500${size}px system-ui, 'Microsoft YaHei', sans-serif`
    const textW = measureCtx.measureText(w.name).width
    const textH = size * 1.25
    const rot = Math.random() < 0.25 ? (Math.random() < 0.5 ? -20 : 20) : 0
    const rad = (Math.abs(rot) * Math.PI) / 180
    const bw = textW * Math.cos(rad) + textH * Math.sin(rad)
    const bh = textW * Math.sin(rad) + textH * Math.cos(rad)

    let ok = false
    let px = cx
    let py = cy
    for (let t = 0; t < 900; t += 0.32) {
      px = cx + t * 1.35 * Math.cos(t)
      py = cy + t * 0.58 * Math.sin(t)
      const l = px - bw / 2
      const r = px + bw / 2
      const tp = py - bh / 2
      const bt = py + bh / 2
      if (l < 4 || r > W - 4 || tp < 2 || bt > H - 2) continue
      let hit = false
      for (const rc of rects) {
        if (l < rc.r && r > rc.l && tp < rc.b && bt > rc.t) {
          hit = true
          break
        }
      }
      if (!hit) {
        ok = true
        break
      }
    }
    if (!ok) continue
    rects.push({ l: px - bw / 2, r: px + bw / 2, t: py - bh / 2, b: py + bh / 2 })
    const cat = props.categorize ? catOf(w.name) : null
    out.push({
      name: w.name,
      value: w.value,
      x: Math.round(px * 10) / 10,
      y: Math.round(py * 10) / 10,
      size,
      color: cat ? cat.color : COLORS[out.length % COLORS.length],
      cat: cat?.name,
      bold: w.value >= maxV * 0.6,
      rot
    })
  }
  placed.value = out
  legend.value = props.categorize
    ? CATS.filter((c) => out.some((w) => w.cat === c.name)).map((c) => ({ name: c.name, color: c.color }))
    : []
  settled.value = true
}

function relayout() {
  placed.value = []
  settled.value = false
  nextTick(layout)
}

onMounted(() => {
  layout()
  ro = new ResizeObserver(() => layout())
  if (boxEl.value) ro.observe(boxEl.value)
})
onBeforeUnmount(() => {
  if (ro) ro.disconnect()
})
watch(() => props.words, relayout)
</script>

<style scoped>
.swc-box {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.swc-word {
  position: absolute;
  white-space: nowrap;
  cursor: default;
  line-height: 1.1;
  user-select: none;
}
.swc-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}
</style>
