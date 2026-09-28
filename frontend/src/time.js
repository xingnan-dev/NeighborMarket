export const DISPLAY_TIME_ZONE = 'Asia/Shanghai'
export const JUST_LISTED_HOURS = 24
export const NEW_LISTING_HOURS = 72
export const STALE_CONFIRMATION_DAYS = 7

export function parseShanghai(value) {
  if (!value) return null
  return new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}+08:00`)
}

export function freshnessLabel(publishedAt, now = new Date()) {
  const date = parseShanghai(publishedAt)
  const ageMs = Math.max(0, now.getTime() - date.getTime())
  const hours = ageMs / 3600000
  if (hours < JUST_LISTED_HOURS) return '刚上架'
  if (hours < NEW_LISTING_HOURS) return '新上架'
  return `已上架${Math.max(3, Math.floor(hours / 24))}天`
}

export function isConfirmationStale(confirmedAt, now = new Date()) {
  return now.getTime() - parseShanghai(confirmedAt).getTime() > STALE_CONFIRMATION_DAYS * 86400000
}

export function formatShanghai(value) {
  const date = parseShanghai(value)
  return date ? new Intl.DateTimeFormat('zh-CN', {timeZone: DISPLAY_TIME_ZONE, dateStyle: 'medium', timeStyle: 'short'}).format(date) : '暂无'
}