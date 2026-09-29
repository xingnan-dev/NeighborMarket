<script setup>
import { ref } from 'vue'
import { freshnessLabel, isConfirmationStale, formatShanghai } from '../time'

defineProps({ item: Object })
const imageFailed = ref(false)

function price(item) {
  if (item.tradeType === 'FREE') return '免费送'
  if (item.tradeType === 'NEGOTIABLE') return '随便给'
  return `¥${Number(item.price).toFixed(2)}`
}

function statusLabel(status) {
  return ({ AVAILABLE: '可领取', RESERVED: '待交接', COMPLETED: '已送出' })[status]
}
</script>

<template>
  <RouterLink class="card" :to="`/items/${item.id}`">
    <div class="card-media">
      <img v-if="item.imageUrl && !imageFailed" :src="item.imageUrl" :alt="`${item.title}的物品图片`" loading="lazy" @error="imageFailed = true">
      <div v-else class="card-placeholder" aria-hidden="true">
        <span>{{ item.category || '邻里闲置' }}</span>
        <small>等待一张物品照片</small>
      </div>
      <span class="status-badge" :class="`status-${item.status?.toLowerCase()}`">{{ statusLabel(item.status) }}</span>
    </div>
    <div class="card-body">
      <div class="card-topline">
        <span class="card-category">{{ item.category }}</span>
        <span class="freshness">{{ freshnessLabel(item.publishedAt) }}</span>
      </div>
      <h3>{{ item.title }}</h3>
      <p class="card-description">{{ item.description }}</p>
      <div class="card-value-row">
        <strong>{{ price(item) }}</strong>
        <span v-if="item.pickupLocation" class="card-location" :title="item.pickupLocation">{{ item.pickupLocation }}</span>
      </div>
      <div class="card-footnote">
        <span>{{ item.publisher.nickname }} 发布</span>
        <span>最近确认 {{ formatShanghai(item.lastConfirmedAt) }}</span>
      </div>
      <p v-if="isConfirmationStale(item.lastConfirmedAt)" class="stale">状态较久未更新，建议先询问</p>
    </div>
  </RouterLink>
</template>
