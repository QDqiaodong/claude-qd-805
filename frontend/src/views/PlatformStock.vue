<template>
  <div>
    <div class="sf-card">
      <div class="sf-card-title">
        盐坨库存立方体
        <span class="sf-muted">立方体体积按库存吨数缩放 · 点卡片登记出仓（客户 / 吨数 / 车牌）</span>
        <div style="margin-left: auto"><el-button size="small" @click="load">刷新</el-button></div>
      </div>

      <div class="ps-grid" v-loading="loading">
        <div
          v-for="p in platforms"
          :key="p.id"
          class="ps-card"
          :class="{ 'is-active': current && current.id === p.id }"
        >
          <!-- CSS 3D 盐坨立方体 -->
          <div class="ps-stage" @click="openOutbound(p)">
            <div class="ps-cube" :style="cubeStyle(p)">
              <div class="ps-face ps-face-top" :style="faceStyle(p, 'top')"></div>
              <div class="ps-face ps-face-front" :style="faceStyle(p, 'front')"></div>
              <div class="ps-face ps-face-right" :style="faceStyle(p, 'right')"></div>
            </div>
            <div class="ps-shadow" :style="{ width: cubeSize(p) + 'px' }"></div>
          </div>

          <div class="ps-info">
            <div class="ps-head">
              <span class="ps-no">{{ p.platformNo }}</span>
              <el-tag size="small" :type="saltTag(p.saltType)">{{ p.saltTypeName }}</el-tag>
            </div>
            <div class="ps-name">{{ p.name }} · {{ p.pileShape }} · {{ p.location }}</div>
            <div class="ps-metrics">
              <div><span class="sf-muted">库存</span><b>{{ Number(p.stockTons || 0).toFixed(1) }} t</b></div>
              <div><span class="sf-muted">容量</span><b>{{ Number(p.capacityTons || 0).toFixed(0) }} t</b></div>
              <div><span class="sf-muted">含水率</span><b>{{ Number(p.moisture || 0).toFixed(2) }} %</b></div>
              <div><span class="sf-muted">累计发运</span><b>{{ Number(p.outTons || 0).toFixed(1) }} t</b></div>
            </div>
            <el-progress
              :percentage="fillPercent(p)"
              :stroke-width="8"
              :color="fillPercent(p) > 85 ? '#e53935' : '#1b5e20'"
            />
            <el-button size="small" type="primary" style="width: 100%; margin-top: 8px" @click="openOutbound(p)">
              登记出仓
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="sf-card" style="margin-top: 14px">
      <div class="sf-card-title">坨台库存与发运汇总</div>
      <el-table :data="stockStats" size="small" stripe>
        <el-table-column prop="platformNo" label="坨台" width="90" />
        <el-table-column prop="platformName" label="名称" />
        <el-table-column label="盐种" width="100">
          <template #default="{ row }">{{ saltName(row.saltType) }}</template>
        </el-table-column>
        <el-table-column prop="stockTons" label="库存（吨）" width="110" align="right" />
        <el-table-column prop="capacityTons" label="容量（吨）" width="110" align="right" />
        <el-table-column label="累计发运（吨）" width="130" align="right">
          <template #default="{ row }">{{ Number(row.outTons || 0).toFixed(1) }}</template>
        </el-table-column>
        <el-table-column prop="outCount" label="发运次数" width="100" align="center" />
      </el-table>
    </div>

    <el-dialog v-model="dlg" :title="current ? `${current.platformNo} ${current.name} · 出仓登记` : '出仓登记'" width="520px">
      <div v-if="current">
        <el-descriptions :column="2" size="small" border style="margin-bottom: 12px">
          <el-descriptions-item label="盐种">{{ current.saltTypeName }}</el-descriptions-item>
          <el-descriptions-item label="垛形">{{ current.pileShape }}</el-descriptions-item>
          <el-descriptions-item label="现存">{{ current.stockTons }} 吨</el-descriptions-item>
          <el-descriptions-item label="含水率">{{ current.moisture }} %</el-descriptions-item>
        </el-descriptions>

        <el-form :model="form" label-width="90px" size="small">
          <el-form-item label="客户">
            <el-input v-model="form.customer" placeholder="例如：渤海化工集团" />
          </el-form-item>
          <el-form-item label="出仓吨数">
            <el-input-number v-model="form.tons" :min="1" :max="2000" :step="10" />
            <span class="sf-muted" style="margin-left: 10px">库存 {{ current.stockTons }} 吨</span>
          </el-form-item>
          <el-form-item label="车牌">
            <el-input v-model="form.plateNo" placeholder="例如：冀B·A1276" />
          </el-form-item>
          <el-form-item label="司磅员">
            <el-input v-model="form.operator" />
          </el-form-item>
        </el-form>

        <div class="sf-card-title" style="margin-top: 6px">历史出仓</div>
        <el-table :data="outbounds" size="small" max-height="200">
          <el-table-column prop="outDate" label="日期" width="110" />
          <el-table-column prop="customer" label="客户" />
          <el-table-column prop="tons" label="吨数" width="80" align="right" />
          <el-table-column prop="plateNo" label="车牌" width="110" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="submit">确认出仓</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const platforms = ref([])
const stockStats = ref([])
const loading = ref(false)
const dlg = ref(false)
const current = ref(null)
const outbounds = ref([])
const form = reactive({ customer: '', tons: 100, plateNo: '冀B·A1276', operator: '刘司磅' })

/** 立方体边长：按库存吨数在容量中的占比缩放（40px ~ 132px） */
function cubeSize(p) {
  const ratio = Number(p.fillRatio || 0)
  return Math.round(40 + Math.min(1, ratio) * 92)
}

function cubeStyle(p) {
  const s = cubeSize(p)
  return { width: s + 'px', height: s + 'px' }
}

function faceStyle(p, face) {
  const s = cubeSize(p)
  const base = saltColor(p.saltType)
  if (face === 'top') {
    return {
      background: shade(base, 26),
      transform: `rotateX(90deg) translateZ(${s / 2}px)`,
      width: s + 'px',
      height: s + 'px'
    }
  }
  if (face === 'front') {
    return {
      background: base,
      transform: `translateZ(${s / 2}px)`,
      width: s + 'px',
      height: s + 'px'
    }
  }
  return {
    background: shade(base, -22),
    transform: `rotateY(90deg) translateZ(${s / 2}px)`,
    width: s + 'px',
    height: s + 'px'
  }
}

function saltColor(type) {
  if (type === 'REFINED') return '#eceff1'
  if (type === 'SUN') return '#cfd8dc'
  return '#b0bec5'
}

function shade(hex, amount) {
  const n = parseInt(hex.slice(1), 16)
  const r = Math.min(255, Math.max(0, ((n >> 16) & 255) + amount))
  const g = Math.min(255, Math.max(0, ((n >> 8) & 255) + amount))
  const b = Math.min(255, Math.max(0, (n & 255) + amount))
  return `rgb(${r},${g},${b})`
}

function saltName(t) {
  return { INDUSTRIAL: '工业盐', SUN: '日晒盐', REFINED: '精制盐' }[t] || t
}

function saltTag(t) {
  return { INDUSTRIAL: '', SUN: 'warning', REFINED: 'info' }[t] || 'info'
}

function fillPercent(p) {
  return Math.min(100, Math.round(Number(p.fillRatio || 0) * 100))
}

async function openOutbound(p) {
  current.value = p
  form.tons = Math.max(10, Math.round(Number(p.stockTons || 0) * 0.1))
  try {
    outbounds.value = await api.platformOutbounds(p.id)
  } catch (e) {
    outbounds.value = []
  }
  dlg.value = true
}

async function submit() {
  try {
    await api.outbound(current.value.id, { ...form })
    ElMessage.success('出仓已登记')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function load() {
  loading.value = true
  try {
    const [list, stats] = await Promise.all([api.platforms(), api.platformStockStats()])
    platforms.value = list || []
    stockStats.value = stats || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.ps-card {
  border: 1px solid #dfe6e4;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.ps-card:hover {
  box-shadow: 0 6px 16px rgba(27, 94, 32, 0.14);
  transform: translateY(-2px);
}

.ps-card.is-active {
  border-color: #1b5e20;
}

.ps-stage {
  height: 190px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: radial-gradient(circle at 50% 60%, #f2f7f2 0%, #e7efe8 100%);
  perspective: 620px;
  cursor: pointer;
}

.ps-cube {
  position: relative;
  transform-style: preserve-3d;
  transform: rotateX(-22deg) rotateY(34deg);
  animation: ps-spin 9s linear infinite;
}

.ps-face {
  position: absolute;
  top: 0;
  left: 0;
  border: 1px solid rgba(255, 255, 255, 0.65);
  box-shadow: inset 0 0 18px rgba(0, 0, 0, 0.08);
}

@keyframes ps-spin {
  0% {
    transform: rotateX(-22deg) rotateY(24deg);
  }
  100% {
    transform: rotateX(-22deg) rotateY(384deg);
  }
}

.ps-shadow {
  height: 10px;
  border-radius: 50%;
  background: rgba(27, 94, 32, 0.16);
  filter: blur(3px);
}

.ps-info {
  padding: 10px 12px 14px;
}

.ps-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ps-no {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 15px;
  font-weight: 700;
  color: #1b5e20;
}

.ps-name {
  font-size: 12px;
  color: #6b7280;
  margin: 4px 0 8px;
}

.ps-metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin-bottom: 8px;
}

.ps-metrics div {
  display: flex;
  flex-direction: column;
}

.ps-metrics b {
  font-size: 14px;
  font-family: "SFMono-Regular", Consolas, monospace;
}
</style>
