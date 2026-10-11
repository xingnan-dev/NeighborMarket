<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { api, messageOf } from '../api'
import { createLatestRequest } from '../latestRequest'
import { formatDashboardTime, formatDuration, itemStatusLabel } from '../dashboard'

const dashboard = ref(null)
const loading = ref(true)
const error = ref('')
const dashboardRequests = createLatestRequest()

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await dashboardRequests.run((signal) => api.get('/dashboard', { signal }))
    if (!result.accepted) return
    dashboard.value = result.value.data
    loading.value = false
  } catch (e) {
    error.value = messageOf(e)
    loading.value = false
  }
}

onMounted(load)
onUnmounted(dashboardRequests.cancel)
</script>

<template>
  <section class="dashboard-page">
    <header class="dashboard-heading">
      <div>
        <p class="eyebrow">社区数据</p>
        <h1>看见邻里间的每一次流转</h1>
      </div>
      <p>来自真实发布、意向与交接记录，每月自然更新。</p>
    </header>

    <div v-if="loading" class="state dashboard-state" role="status">正在整理社区数据…</div>
    <div v-else-if="error" class="state dashboard-state error" role="alert">
      <strong>暂时无法读取社区数据</strong>
      <span>{{ error }}</span>
      <button type="button" @click="load">重新加载</button>
    </div>

    <template v-else-if="dashboard">
      <section class="metric-grid" aria-label="社区核心指标">
        <article class="metric-card">
          <span>本月发布</span>
          <strong>{{ dashboard.publishedThisMonth }}</strong>
          <p>件闲置来到社区</p>
        </article>
        <article class="metric-card">
          <span>本月成交</span>
          <strong>{{ dashboard.completedThisMonth }}</strong>
          <p>次交接顺利完成</p>
        </article>
        <article class="metric-card">
          <span>当前在售</span>
          <strong>{{ dashboard.availableNow }}</strong>
          <p>件物品等待邻居</p>
        </article>
      </section>

      <section class="dashboard-detail-grid">
        <article class="dashboard-card fastest-card">
          <div class="dashboard-card-heading">
            <div>
              <p class="eyebrow">流转效率</p>
              <h2>最快被领走</h2>
            </div>
            <span class="dashboard-icon" aria-hidden="true">快</span>
          </div>
          <div v-if="dashboard.fastestCompleted" class="fastest-content">
            <RouterLink :to="`/items/${dashboard.fastestCompleted.itemId}`">
              {{ dashboard.fastestCompleted.title }}
            </RouterLink>
            <strong>{{ formatDuration(dashboard.fastestCompleted.durationSeconds) }}</strong>
            <p>从发布到完成交接</p>
            <small>完成于 {{ formatDashboardTime(dashboard.fastestCompleted.completedAt) }}</small>
          </div>
          <div v-else class="dashboard-empty">
            <strong>还没有已完成的交接</strong>
            <p>第一份完整记录出现后，这里会展示最快流转的物品。</p>
          </div>
        </article>

        <article class="dashboard-card wanted-card">
          <div class="dashboard-card-heading">
            <div>
              <p class="eyebrow">邻里关注</p>
              <h2>最想要但尚未成交</h2>
            </div>
            <span class="dashboard-definition">等待中与已确认意向</span>
          </div>
          <ol v-if="dashboard.mostWanted?.length" class="wanted-list">
            <li v-for="(item, index) in dashboard.mostWanted" :key="item.itemId">
              <span class="rank">{{ String(index + 1).padStart(2, '0') }}</span>
              <RouterLink :to="`/items/${item.itemId}`">{{ item.title }}</RouterLink>
              <span class="wanted-count">{{ item.interestCount }} 人想要</span>
              <span class="wanted-status">{{ itemStatusLabel(item.itemStatus) }}</span>
            </li>
          </ol>
          <div v-else class="dashboard-empty">
            <strong>暂时没有有效意向</strong>
            <p>有人对未成交物品表达想要后，排行会显示在这里。</p>
          </div>
        </article>
      </section>

      <p class="dashboard-footnote">统计时区：Asia/Shanghai · 数据来自当前社区真实记录</p>
    </template>
  </section>
</template>
