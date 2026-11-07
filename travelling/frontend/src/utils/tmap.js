/**
 * 腾讯地图GL JS（WebGL 3D版）公共工具
 *
 * key 在 frontend/.env.local 里配置（VITE_TMAP_KEY）
 * 申请地址：https://lbs.qq.com/dev/console/key/manage
 */

const KEY = import.meta.env.VITE_TMAP_KEY || ''

/** 是否已配置可用的key（占位文案不算） */
export function hasTMapKey() {
  return !!KEY && !KEY.includes('请') && KEY.trim().length > 0
}

let loading = null

/** 懒加载腾讯地图SDK（全局只加载一次），返回Promise<TMap> */
export function loadTMap() {
  if (window.TMap) return Promise.resolve(window.TMap)
  if (loading) return loading
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://map.qq.com/api/gljs?v=1.exp&key=${encodeURIComponent(KEY)}`
    script.onload = () => (window.TMap ? resolve(window.TMap) : reject(new Error('腾讯地图 SDK 初始化失败')))
    script.onerror = () => reject(new Error('腾讯地图 SDK 加载失败，请检查网络'))
    document.head.appendChild(script)
  })
  return loading
}

/** 站点分类符号（与页面图例一致） */
export const CATEGORY_STYLE = {
  NATURE: { symbol: '▲', color: '#52C41A', label: '自然景点' },
  HISTORY: { symbol: '■', color: '#FA8C16', label: '历史人文' },
  TRENDY: { symbol: '★', color: '#EB2F96', label: '网红打卡' },
  FOOD: { symbol: '●', color: '#F5222D', label: '美食地标' },
  SECRET: { symbol: '◆', color: '#722ED1', label: '小众秘境' },
  THEME: { symbol: '◎', color: '#2F80ED', label: '主题乐园' },
  // 用户自己添加的地点（不入景点库，仅本次规划有效）
  CUSTOM: { symbol: '✚', color: '#13C2C2', label: '我要去的地方' }
}

function svgToDataUri(svg) {
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
}

/** 生成“大头针”图标（分类色+符号） */
export function pinIcon(color, symbol, size = 30) {
  const h = Math.round(size * 1.27)
  return svgToDataUri(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${h}">` +
      `<path d="M15 37C15 37 28 21.5 28 13.5 28 6 22.2 0 15 0 7.8 0 2 6 2 13.5 2 21.5 15 37 15 37z" ` +
      `transform="scale(${size / 30})" fill="${color}" stroke="#ffffff" stroke-width="2"/>` +
      `<text x="${size / 2}" y="${size * 0.63}" font-size="${size * 0.47}" text-anchor="middle" ` +
      `fill="#ffffff" font-family="sans-serif">${symbol}</text>` +
      `</svg>`
  )
}

/** 生成路线顺序编号图标 */
export function numIcon(n, size = 24, color = '#2F80ED') {
  const c = size / 2
  return svgToDataUri(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">` +
      `<circle cx="${c}" cy="${c}" r="${c - 1}" fill="${color}" stroke="#ffffff" stroke-width="2"/>` +
      `<text x="${c}" y="${c + size * 0.19}" font-size="${size * 0.5}" text-anchor="middle" ` +
      `fill="#ffffff" font-family="sans-serif">${n}</text>` +
      `</svg>`
  )
}

/** 把颜色按比例向白色混合（用于"非当前天"的弱化配色），ratio 0~1 */
export function mixWhite(hex, ratio = 0.6) {
  const m = /^#?([0-9a-f]{6})$/i.exec(String(hex || '').trim())
  if (!m) return hex
  const num = parseInt(m[1], 16)
  const r = (num >> 16) & 255
  const g = (num >> 8) & 255
  const b = num & 255
  const t = Math.min(1, Math.max(0, ratio))
  const mix = (c) => Math.round(c + (255 - c) * t)
  return '#' + [mix(r), mix(g), mix(b)].map((c) => c.toString(16).padStart(2, '0')).join('')
}

/** 生成“相机”足迹图标 */
export function cameraIcon(size = 34, color = '#FF6B35') {
  const w = size
  const h = size
  return svgToDataUri(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}">` +
      `<circle cx="${w / 2}" cy="${h / 2}" r="${w / 2 - 1}" fill="${color}" stroke="#ffffff" stroke-width="2"/>` +
      `<rect x="${w * 0.28}" y="${h * 0.38}" width="${w * 0.44}" height="${h * 0.28}" rx="3" fill="#ffffff"/>` +
      `<circle cx="${w / 2}" cy="${h * 0.52}" r="${w * 0.09}" fill="${color}"/>` +
      `</svg>`
  )
}
