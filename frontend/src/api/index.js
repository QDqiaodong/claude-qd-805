import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const unwrap = (res) => res?.data

/** 卤度 → 热力色（0~30 °Bé，越深越绿） */
export function brineColor(degree) {
  const v = Number(degree || 0)
  if (v >= 28) return '#0d3b12'
  if (v >= 25) return '#1b5e20'
  if (v >= 22) return '#2e7d32'
  if (v >= 19) return '#43a047'
  if (v >= 16) return '#7cb342'
  if (v >= 13) return '#9ccc65'
  if (v >= 10) return '#c5e1a5'
  if (v >= 6) return '#dcedc8'
  return '#f1f8e9'
}

export const BRINE_LEGEND = [
  { min: 28, color: '#0d3b12', label: '≥28' },
  { min: 25, color: '#1b5e20', label: '25' },
  { min: 22, color: '#2e7d32', label: '22' },
  { min: 19, color: '#43a047', label: '19' },
  { min: 16, color: '#7cb342', label: '16' },
  { min: 13, color: '#9ccc65', label: '13' },
  { min: 10, color: '#c5e1a5', label: '10' },
  { min: 6, color: '#dcedc8', label: '6' },
  { min: 0, color: '#f1f8e9', label: '<6' }
]

export const POND_STATUS_COLOR = {
  SUN: '#7cb342',
  CRYST: '#43a047',
  WAIT: '#f9a825',
  REPAIR: '#e53935',
  RAIN_STOP: '#1e88e5'
}

export const api = {
  // ---- 池区 / 池子 ----
  pondTree: () => http.get('/ponds/tree').then(unwrap),
  ponds: () => http.get('/ponds').then(unwrap),
  areaStats: () => http.get('/ponds/area-stats').then(unwrap),
  pondDetail: (id) => http.get(`/ponds/${id}`).then(unwrap),
  pondLogs: (id) => http.get(`/ponds/${id}/logs`).then(unwrap),
  registerBrine: (id, data) => http.post(`/ponds/${id}/brine`, data).then(unwrap),

  // ---- 气象 / 卤度 ----
  dailyWeather: (days = 14) => http.get('/weather/daily', { params: { days } }).then(unwrap),
  weatherByPond: (pondId) => http.get(`/weather/pond/${pondId}`).then(unwrap),
  recentRain: (pondId) => http.get(`/weather/pond/${pondId}/rain`).then(unwrap),
  createWeather: (data) => http.post('/weather', data).then(unwrap),

  // ---- 收盐作业 ----
  harvestBoard: (date) => http.get('/harvest/board', { params: date ? { date } : {} }).then(unwrap),
  createHarvest: (data) => http.post('/harvest', data).then(unwrap),
  updateHarvestStatus: (id, taskStatus) => http.put(`/harvest/${id}/status`, { taskStatus }).then(unwrap),
  resumeHarvest: (id, operator) => http.post(`/harvest/${id}/resume`, { operator }).then(unwrap),

  // ---- 坨台 ----
  platforms: () => http.get('/platforms').then(unwrap),
  platformStockStats: () => http.get('/platforms/stock-stats').then(unwrap),
  platformOutbounds: (id) => http.get(`/platforms/${id}/outbounds`).then(unwrap),
  outbound: (id, data) => http.post(`/platforms/${id}/outbound`, data).then(unwrap),

  // ---- 多态作业记录 / 调度 / 概览 ----
  fieldLogs: (targetType) => http.get('/field-logs', { params: targetType ? { targetType } : {} }).then(unwrap),
  logOptions: () => http.get('/field-logs/options').then(unwrap),
  createFieldLog: (data) => http.post('/field-logs', data).then(unwrap),
  dispatchTasks: () => http.get('/dispatch').then(unwrap),
  createDispatch: (data) => http.post('/dispatch', data).then(unwrap),
  updateDispatch: (id, taskStatus) => http.put(`/dispatch/${id}/status`, { taskStatus }).then(unwrap),
  overview: () => http.get('/overview').then(unwrap),
  equipments: () => http.get('/equipments').then(unwrap)
}

export default http
