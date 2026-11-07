/**
 * 标签字典前端缓存，只读引用。
 * 选项文案一律从GET/api/tags/dict/{scope} 拉取后缓存，不在前端硬编码。
 * 缓存是进程内一次性的，首屏页和发布表单都可以直接await loadDict()。
 */
import { ref } from 'vue'
import { tagApi } from '@/api'

/** scope -> { dimension -> [{ code, label, multi, core }] } */
const dictCache = {}
const loading = {}
/** 供模板响应式使用的版本号（字典就绪后自增，触发重新渲染） */
export const dictVersion = ref(0)

/**
 * 加载某个scope的字典（带进程内缓存与并发去重）。
 * @param {'USER'|'POST'|'GUIDE'} scope
 * @returns {Promise<Object>} { dimension: [{code,label,multi,core}] }
 */
export async function loadDict(scope = 'USER') {
  if (dictCache[scope]) return dictCache[scope]
  if (loading[scope]) return loading[scope]
  loading[scope] = (async () => {
    try {
      // 后端/api/tags/dict/{scope} 返回扁平行列表，这里按dimension分组
      const rows = await tagApi.dictOfScope(scope)
      const grouped = {}
      for (const t of rows || []) {
        if (!t || !t.dimension) continue
        if (!grouped[t.dimension]) grouped[t.dimension] = []
        grouped[t.dimension].push({
          code: t.code,
          label: t.label,
          multi: !!t.multi,
          core: !!t.core
        })
      }
      dictCache[scope] = grouped
    } catch (e) {
      dictCache[scope] = {}
    } finally {
      delete loading[scope]
      dictVersion.value++
    }
    return dictCache[scope]
  })()
  return loading[scope]
}

/** 某个scope + dimension的选项列表（未加载时返回空数组） */
export function optionsOf(scope, dimension) {
  // 读取版本号以建立响应式依赖：字典异步加载完成后，用到它的computed会自动重算
  void dictVersion.value
  return dictCache[scope]?.[dimension] || []
}

/** 单个选项的展示名；查不到时回退原code，保证不空白 */
export function labelOf(scope, dimension, code) {
  const opt = optionsOf(scope, dimension).find((o) => o.code === code)
  return opt ? opt.label : code
}

/** 逗号串 -> 展示名数组 */
export function labelsOf(scope, dimension, csv) {
  if (!csv) return []
  return String(csv)
    .split(/[,，;；\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((c) => labelOf(scope, dimension, c))
}

/** 人均预算 -> 档位code（与字典POST:costLevel对齐） */
export function costLevelOf(costPerPerson) {
  const n = Number(costPerPerson)
  if (!n && n !== 0) return null
  if (n < 300) return 'ECONOMY'
  if (n < 800) return 'MODERATE'
  if (n <= 1500) return 'COMFORT'
  return 'PREMIUM'
}

/** 天数 -> 档位code（与字典POST:dayRange对齐） */
export function dayRangeOf(days) {
  const n = Number(days)
  if (!n) return null
  if (n <= 1) return 'D1'
  if (n <= 3) return 'D2_3'
  if (n <= 7) return 'D4_7'
  return 'D7P'
}

/** 天数 -> 档位展示文案 */
export function dayRangeLabel(days) {
  const code = dayRangeOf(days)
  return code ? labelOf('POST', 'dayRange', code) : ''
}
