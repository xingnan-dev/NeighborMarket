<script setup>
import { ref, onMounted } from 'vue'
import { api, messageOf } from '../api'
import ItemCard from '../components/ItemCard.vue'

const categories = ['日用', '家电', '图书', '数码', '其他']
const items = ref([])
const loading = ref(true)
const error = ref('')
const keyword = ref('')
const category = ref('')
const tradeType = ref('')
const status = ref('AVAILABLE')
const page = ref(1)
const pages = ref(1)

async function load(p = 1) {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/items', {
      params: {
        page: p,
        size: 12,
        keyword: keyword.value || undefined,
        category: category.value || undefined,
        tradeType: tradeType.value || undefined,
        status: status.value,
      },
    })
    items.value = data.records
    page.value = data.current
    pages.value = data.pages || 1
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  load(1)
}

onMounted(load)
</script>

<template>
  <section class="hero market-hero">
    <div class="hero-copy">
      <p class="eyebrow">梧桐社区 · 邻里循环</p>
      <h1>在邻里间，遇见刚好需要的闲置</h1>
      <p class="hero-note">就近分享，让每件好东西继续被使用。</p>
    </div>
    <form class="filters filters-stage2" aria-label="闲置筛选" @submit.prevent="applyFilters">
      <label class="filter-field filter-search">
        <span>搜索</span>
        <input v-model.trim="keyword" placeholder="名称或描述" aria-label="搜索名称或描述">
      </label>
      <label class="filter-field">
        <span>分类</span>
        <select v-model="category" aria-label="商品分类" @change="applyFilters">
          <option value="">全部分类</option>
          <option v-for="value in categories" :key="value">{{ value }}</option>
        </select>
      </label>
      <label class="filter-field">
        <span>方式</span>
        <select v-model="tradeType" aria-label="交易方式" @change="applyFilters">
          <option value="">全部方式</option>
          <option value="FREE">免费送</option>
          <option value="NEGOTIABLE">随便给</option>
          <option value="FIXED_PRICE">标价</option>
        </select>
      </label>
      <label class="filter-field">
        <span>状态</span>
        <select v-model="status" aria-label="物品状态" @change="applyFilters">
          <option value="AVAILABLE">可领取</option>
          <option value="RESERVED">待交接</option>
          <option value="COMPLETED">已送出</option>
        </select>
      </label>
      <button class="filter-submit" type="submit">搜索</button>
    </form>
  </section>

  <p v-if="loading" class="state">正在按当前条件查找邻里好物…</p>
  <p v-else-if="error" class="state error">{{ error }} <button type="button" @click="load(page)">重试</button></p>
  <div v-else-if="!items.length" class="state empty-state">
    <strong>暂时没有符合条件的物品</strong>
    <span>试试减少一个筛选条件，或换个关键词看看。</span>
  </div>
  <section v-else class="grid" aria-label="闲置物品列表">
    <ItemCard v-for="item in items" :key="item.id" :item="item" />
  </section>
  <div v-if="pages > 1" class="pager">
    <button type="button" :disabled="page <= 1" @click="load(page - 1)">上一页</button>
    <span>第 {{ page }} / {{ pages }} 页</span>
    <button type="button" :disabled="page >= pages" @click="load(page + 1)">下一页</button>
  </div>
</template>
