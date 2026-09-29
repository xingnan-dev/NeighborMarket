import test from 'node:test'
import assert from 'node:assert/strict'
import { formatDashboardTime, formatDuration, itemStatusLabel } from '../src/dashboard.js'

test('formats dashboard duration at useful community scales', () => {
  assert.equal(formatDuration(45), '45 秒')
  assert.equal(formatDuration(5400), '1 小时 30 分钟')
  assert.equal(formatDuration(93600), '1 天 2 小时')
})

test('formats dashboard time in Asia Shanghai', () => {
  assert.match(formatDashboardTime('2026-09-20T18:00:00'), /2026/)
  assert.notEqual(formatDashboardTime('2026-09-20T18:00:00'), '暂无')
})

test('maps unfinished item statuses without inventing completed state', () => {
  assert.equal(itemStatusLabel('AVAILABLE'), '可领取')
  assert.equal(itemStatusLabel('RESERVED'), '待交接')
})
