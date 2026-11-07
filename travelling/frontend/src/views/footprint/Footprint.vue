<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <h2 class="page-title" style="margin: 0">📷 我的旅行足迹 · 去过的地方都算数</h2>
      <div class="flex gap-8">
        <el-button type="primary" :icon="Plus" @click="openAdd">添加足迹</el-button>
        <el-button type="warning" plain :icon="Picture" @click="shareVisible = true">
          生成分享图
        </el-button>
      </div>
    </div>

    <div class="home-layout">
      <div class="home-main">
        <div style="position: relative">
          <div ref="mapEl" class="x-map"></div>
          <div v-if="!mapReady && !mapError" class="map-mask">
            <el-icon class="is-loading" :size="22"><Loading /></el-icon>
            <div class="sub-text mt-12">3D 地图加载中…</div>
          </div>
          <div v-if="mapError" class="map-mask">
            <div style="font-size: 26px">🗺️</div>
            <div style="font-weight: 700; margin: 8px 0">3D 地图需要配置腾讯位置服务 key</div>
            <div class="sub-text" style="line-height: 1.9; max-width: 420px">
              1. 到
              <el-link type="primary" href="https://lbs.qq.com/dev/console/key/manage" target="_blank">
                lbs.qq.com/dev/console/key/manage
              </el-link>
              申请（个人认证后免费）<br />
              2. 填到 <code>frontend/.env.local</code> 的 <code>VITE_TMAP_KEY=</code> 后面<br />
              3. 重启前端（<code>npm run dev</code>）
            </div>
            <div class="sub-text mt-12">足迹列表与新增足迹功能不受影响，可正常使用。</div>
          </div>
        </div>

        <div class="x-card">
          <div class="card-title">足迹明细（{{ footprints.length }}）</div>
          <el-table :data="footprints" size="small" empty-text="还没有足迹，去添加第一个吧">
            <el-table-column label="照片" width="76">
              <template #default="{ row }">
                <el-image
                  v-if="row.photo"
                  :src="row.photo"
                  style="width: 52px; height: 52px; border-radius: 8px"
                  fit="cover"
                  :preview-src-list="[row.photo]"
                  preview-teleported
                />
                <span v-else style="font-size: 24px">📍</span>
              </template>
            </el-table-column>
            <el-table-column label="地点" prop="name" min-width="120" />
            <el-table-column label="城市" prop="city" width="90" />
            <el-table-column label="到访时间" width="150">
              <template #default="{ row }">{{ formatDate(row.visitedAt) }}</template>
            </el-table-column>
            <el-table-column label="备注" prop="note" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="home-side">
        <div class="x-card" style="text-align: center">
          <div class="sub-text">我已走过</div>
          <div class="stat-num">{{ cityCount }}</div>
          <div class="sub-text">座城市 · {{ footprints.length }}个足迹点</div>
        </div>
        <div class="x-card">
          <div class="card-title">足迹提示</div>
          <div class="sub-text" style="line-height: 2">
            在地图点位上点击，可以查看当时拍摄的照片与备注；<br />
            添加足迹只需填个地名点「自动定位」，坐标自动带出；<br />
            也可以直接从景点库选择，生成分享图可回顾整段旅程。
          </div>
        </div>
      </div>
    </div>

    <!-- 添加足迹 -->
    <el-dialog v-model="addVisible" title="添加旅行足迹" width="520px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="从景点选择">
          <el-select
            v-model="form.spotId"
            filterable
            clearable
            no-data-text="景点列表没加载出来 —— 关闭弹窗重开一次，或直接填名称点「自动定位」"
            placeholder="选择景点自动带出名称与坐标"
            style="width: 100%"
            @change="onSpotChange"
          >
            <el-option
              v-for="s in spotOptions"
              :key="s.id"
              :label="`${s.city || ''} · ${s.name}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="地点名称" required>
          <el-input v-model="form.name" placeholder="如：橘子洲头" @keyup.enter="locate">
            <template #append>
              <el-button :loading="locating" @click="locate">🔍 自动定位</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="geoSearched" label="定位结果">
          <el-select
            v-model="geoPickId"
            placeholder="搜索到多个同名地点时，在这里换一个"
            style="width: 100%"
            @change="onGeoPick"
          >
            <el-option
              v-for="(c, i) in geoCandidates"
              :key="i"
              :label="`${c.title}（${c.city || c.province || ''}${c.address ? ' · ' + c.address : ''}）`"
              :value="i"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" placeholder="定位后自动带出，也可手改" />
        </el-form-item>
        <el-form-item label="坐标">
          <span v-if="form.lng && form.lat" class="geo-ok">
            ✅ 已定位：{{ form.lng.toFixed(6) }}, {{ form.lat.toFixed(6) }}
            <el-tag v-if="geoSource === 'spot'" size="small" type="info">来自景点库</el-tag>
          </span>
          <span v-else class="geo-none">还没定位 —— 填好名称后点上面「🔍 自动定位」即可，不用手填经纬度</span>
        </el-form-item>
        <el-form-item label="到访时间">
          <el-date-picker
            v-model="form.visitedAt"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="照片">
          <el-upload
            v-model:file-list="photoFileList"
            class="footprint-uploader"
            action="#"
            :show-file-list="true"
            :limit="1"
            accept="image/*"
            :http-request="handleUpload"
            :before-remove="() => { form.photo = ''; return true }"
          >
            <el-button :icon="Plus" :loading="uploadLoading" type="primary" plain>
              {{ form.photo ? '重新上传' : '选择照片' }}
            </el-button>
            <template #tip>
              <div class="el-upload__tip">
                支持 jpg/png/gif/webp，单张不超过5MB；已保存地址：{{ form.photo || '（未上传）' }}
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="那天的故事…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存足迹</el-button>
      </template>
    </el-dialog>

    <!-- 分享图 -->
    <el-dialog v-model="shareVisible" title="我的旅行足迹分享" width="640px">
      <div class="share-poster">
        <div class="gradient-banner" style="padding: 24px">
          <h1 style="font-size: 22px">我的旅行足迹</h1>
          <p>{{ cityCount }}座城市 · {{ footprints.length }}个足迹点 · 学旅圈记录每一次出发</p>
        </div>
        <div class="flex-center gap-16 mt-16" style="flex-wrap: wrap">
          <div class="share-stat">
            <div class="stat-num">{{ cityCount }}</div>
            <div class="sub-text">城市</div>
          </div>
          <div class="share-stat">
            <div class="stat-num">{{ footprints.length }}</div>
            <div class="sub-text">足迹点</div>
          </div>
          <div class="share-stat">
            <div class="stat-num">{{ photoCount }}</div>
            <div class="sub-text">照片</div>
          </div>
        </div>
        <div class="photo-wall mt-16">
          <img
            v-for="(p, i) in photoList.slice(0, 9)"
            :key="i"
            :src="p"
            alt="足迹照片"
          />
          <div v-if="!photoList.length" class="sub-text" style="width: 100%; text-align: center; padding: 24px">
            还没有照片，多记录几次旅程吧
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="shareVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Picture, Loading } from '@element-plus/icons-vue'
import { footprintApi, geoApi, spotApi, uploadApi } from '@/api'
import { formatDate } from '@/utils/format'
import { cameraIcon, hasTMapKey, loadTMap } from '@/utils/tmap'

const mapEl = ref(null)
let TMapRef = null
let map = null
let layer = null          // 足迹标注图层（MultiMarker）
let infoWindow = null
const footById = {}
const mapReady = ref(false)
const mapError = ref(false)

const footprints = ref([])
const spotOptions = ref([])
const addVisible = ref(false)
const shareVisible = ref(false)
const saving = ref(false)
const uploadLoading = ref(false)
const photoFileList = ref([])

/* 自动定位状态 */
const locating = ref(false)
const geoSearched = ref(false)
const geoCandidates = ref([])
const geoPickId = ref(null)
const geoSource = ref('poi')

const emptyForm = () => ({
  spotId: null,
  name: '',
  city: '',
  lng: null,
  lat: null,
  visitedAt: '',
  photo: '',
  note: ''
})
const form = ref(emptyForm())

const cityCount = computed(
  () => new Set(footprints.value.map((f) => f.city).filter(Boolean)).size
)
const photoList = computed(() =>
  footprints.value.map((f) => f.photo).filter(Boolean)
)
const photoCount = computed(() => photoList.value.length)

async function initMap() {
  if (!hasTMapKey()) {
    mapError.value = true
    return
  }
  try {
    const TMap = await loadTMap()
    TMapRef = TMap
    map = new TMap.Map(mapEl.value, {
      center: new TMap.LatLng(28.2, 112.97),
      zoom: 5,
      pitch: 0,
      rotation: 0,
      baseMap: { type: 'vector', features: ['base', 'building3d'] }
    })
    layer = new TMap.MultiMarker({
      map,
      styles: {
        foot: new TMap.MarkerStyle({
          width: 34,
          height: 34,
          anchor: { x: 17, y: 17 },
          src: cameraIcon(34)
        })
      },
      geometries: []
    })
    infoWindow = new TMap.InfoWindow({
      map,
      position: new TMap.LatLng(28.2, 112.97),
      offset: { x: 0, y: -26 },
      enableCustom: true
    })
    infoWindow.close()
    layer.on('click', (evt) => {
      const f = footById[evt.geometry.id]
      if (!f) return
      infoWindow.setPosition(evt.geometry.position)
      infoWindow.setContent(`
        <div class="foot-tip">
          ${f.photo ? `<img src="${f.photo}" alt=""/>` : ''}
          <div class="ft-name">${f.name || ''}</div>
          <div class="sub-text">${f.city || ''} · ${formatDate(f.visitedAt)}</div>
          <div class="ft-note">${f.note || ''}</div>
        </div>`)
      infoWindow.open()
    })
    mapReady.value = true
  } catch (e) {
    console.warn('腾讯地图初始化失败：', e?.message || e)
    mapError.value = true
  }
}

function drawFootprints() {
  if (!map) return
  Object.keys(footById).forEach((k) => delete footById[k])
  const geometries = []
  const bounds = new TMapRef.LatLngBounds()
  footprints.value.forEach((f) => {
    const lng = Number(f.lng)
    const lat = Number(f.lat)
    if (!lng || !lat) return
    footById[f.id] = f
    const pos = new TMapRef.LatLng(lat, lng)
    geometries.push({ id: String(f.id), styleId: 'foot', position: pos })
    bounds.extend(pos)
  })
  layer.setGeometries(geometries)
  if (geometries.length) map.fitBounds(bounds, { padding: 120 })
}

async function load() {
  const data = await footprintApi.mine().catch(() => [])
  footprints.value = data?.list || data || []
  await nextTick()
  drawFootprints()
}

async function loadSpots() {
  const data = await spotApi.list({}).catch(() => [])
  spotOptions.value = data?.list || data || []
}

function onSpotChange(id) {
  const s = spotOptions.value.find((x) => x.id === id)
  if (!s) return
  form.value.name = s.name
  form.value.city = s.city
  form.value.lng = Number(s.lng ?? s.longitude)
  form.value.lat = Number(s.lat ?? s.latitude)
  geoSearched.value = false
  geoCandidates.value = []
}

/** 按地名自动定位：后端调腾讯地点搜索，配额用光时自动用景点库兜底 */
async function locate() {
  const keyword = (form.value.name || '').trim()
  if (!keyword) {
    ElMessage.warning('请先填写地点名称，再点自动定位')
    return
  }
  locating.value = true
  try {
    const data = await geoApi.search(keyword, form.value.city)
    geoCandidates.value = data?.list || []
    geoSource.value = data?.source || 'none'
    geoSearched.value = true
    if (!geoCandidates.value.length) {
      ElMessage.warning('没找到这个地点，试试写得更具体一些（如「长沙 橘子洲」）')
      return
    }
    geoPickId.value = 0
    applyGeo(geoCandidates.value[0])
    const reasonText = {
      quota: '腾讯位置服务今日配额用完',
      timeout: '定位服务响应超时',
      error: '定位服务暂时不可用',
      empty: '在线地图没搜到该地点'
    }[data?.reason]
    ElMessage.success(
      geoSource.value === 'spot'
        ? `${reasonText || '在线定位不可用'}，已用景点库结果定位`
        : `已定位「${geoCandidates.value[0].title}」`
    )
  } catch {
    ElMessage.error('定位服务暂时连不上：请确认后端已重启（本次改动新增了 /api/geo 接口）')
  } finally {
    locating.value = false
  }
}

function onGeoPick(i) {
  const c = geoCandidates.value[i]
  if (c) applyGeo(c)
}

function applyGeo(c) {
  if (geoSource.value === 'poi') form.value.name = c.title
  form.value.city = c.city || c.province || form.value.city
  form.value.lng = Number(c.lng)
  form.value.lat = Number(c.lat)
}

function openAdd() {
  form.value = emptyForm()
  photoFileList.value = []
  uploadLoading.value = false
  geoSearched.value = false
  geoCandidates.value = []
  geoPickId.value = null
  addVisible.value = true
  // 景点列表在页面加载时拉过一次；若当时后端没就绪会静默失败，这里打开弹窗时补一次
  if (!spotOptions.value.length) loadSpots()
}

async function handleUpload(options) {
  uploadLoading.value = true
  try {
    const url = await uploadApi.image(options.file)
    form.value.photo = url
    ElMessage.success('照片上传成功')
  } catch (e) {
    options.onError?.(e)
    throw e
  } finally {
    uploadLoading.value = false
  }
}

async function submit() {
  if (!form.value.name) {
    ElMessage.warning('请填写地点名称')
    return
  }
  if (!form.value.lng || !form.value.lat) {
    ElMessage.warning('还没定位到坐标：点「🔍 自动定位」或从景点选择即可，不用手填经纬度')
    return
  }
  saving.value = true
  try {
    const payload = { ...form.value }
    delete payload.spotId
    // 不选则留空，后端默认记为今天（本地Date.toISOString会带时区偏移，不能用）
    await footprintApi.create(payload)
    ElMessage.success('足迹已记录')
    addVisible.value = false
    photoFileList.value = []
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除足迹「${row.name}」吗？`, '提示', {
    type: 'warning'
  })
  await footprintApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  initMap().then(() => {
    if (footprints.value.length) drawFootprints()
  })
  await Promise.all([load(), loadSpots()])
})

onBeforeUnmount(() => {
  map?.destroy()
  map = null
  mapReady.value = false
})
</script>

<style scoped>
.geo-ok {
  font-size: 13px;
  color: #52c41a;
  line-height: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.geo-none {
  font-size: 12px;
  color: #999;
  line-height: 2;
}
.share-stat {
  text-align: center;
  min-width: 90px;
}
.photo-wall {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.photo-wall img {
  width: 100%;
  height: 130px;
  object-fit: cover;
  border-radius: 10px;
}
</style>
