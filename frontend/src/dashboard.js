import { formatShanghai } from './time.js'

export function formatDuration(totalSeconds) {
  const seconds = Math.max(0, Number(totalSeconds) || 0)
  if (seconds < 60) return `${Math.round(seconds)} 秒`
  const totalMinutes = Math.floor(seconds / 60)
  if (totalMinutes < 60) return `${totalMinutes} 分钟`
  const totalHours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (totalHours < 24) return minutes ? `${totalHours} 小时 ${minutes} 分钟` : `${totalHours} 小时`
  const days = Math.floor(totalHours / 24)
  const hours = totalHours % 24
  return hours ? `${days} 天 ${hours} 小时` : `${days} 天`
}

export function formatDashboardTime(value) {
  return formatShanghai(value)
}

export function itemStatusLabel(status) {
  return ({ AVAILABLE: '可领取', RESERVED: '待交接' })[status] || status || '未知状态'
}
