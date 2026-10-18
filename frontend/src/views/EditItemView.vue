<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, messageOf } from '../api'
import { auth, initializeAuth } from '../auth'
import { buildItemPayload, populateItemForm, validateItemForm } from '../itemForm'
import { uploadItemImage } from '../imageUpload'
import ImagePicker from '../components/ImagePicker.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const expectedVersion = ref(null)
const imageUrl = ref('')
const imageFile = ref(null)
const imageBusy = ref(false)
const imageProgress = ref(0)
const form = reactive({
  title: '', description: '', category: '日用', tradeType: 'FREE', price: null,
  pickupLocation: '', slots: [{ startAt: '', endAt: '' }],
})

const addSlot = () => {
  if (form.slots.length < 5) form.slots.push({ startAt: '', endAt: '' })
}
const removeSlot = (index) => form.slots.splice(index, 1)

async function load() {
  loading.value = true
  error.value = ''
  try {
    await initializeAuth()
    if (!auth.user) return router.push({ path: '/login', query: { redirect: route.fullPath } })
    const [itemResponse, slotResponse] = await Promise.all([
      api.get(`/items/detail/${route.params.id}`),
      api.get(`/items/${route.params.id}/slots`),
    ])
    const item = itemResponse.data
    if (item.publisher.id !== auth.user.id) throw new Error('只有发布者可以编辑物品')
    if (item.status !== 'AVAILABLE') throw new Error('只有可领取物品可以编辑')
    expectedVersion.value = item.version
    imageUrl.value = item.imageUrl
    populateItemForm(form, item, slotResponse.data)
  } catch (e) {
    error.value = e.response ? messageOf(e) : e.message
  } finally {
    loading.value = false
  }
}

async function uploadImage() {
  if (!imageFile.value || imageBusy.value || busy.value) return
  imageBusy.value = true
  imageProgress.value = 0
  error.value = ''
  notice.value = ''
  try {
    const { data } = await uploadItemImage(api, route.params.id, expectedVersion.value, imageFile.value,
      (progress) => { imageProgress.value = progress })
    expectedVersion.value = data.version
    imageUrl.value = data.imageUrl
    imageFile.value = null
    notice.value = '图片已上传。'
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    imageBusy.value = false
  }
}

async function removeImage() {
  if (imageBusy.value || busy.value) return
  imageBusy.value = true
  error.value = ''
  notice.value = ''
  try {
    const { data } = await api.delete(`/items/${route.params.id}/image`, { params: { expectedVersion: expectedVersion.value } })
    expectedVersion.value = data.version
    imageUrl.value = data.imageUrl
    imageFile.value = null
    notice.value = '图片已删除。'
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    imageBusy.value = false
  }
}

async function submit() {
  if (busy.value) return
  error.value = validateItemForm(form)
  notice.value = ''
  if (error.value) return
  busy.value = true
  try {
    const payload = { expectedVersion: expectedVersion.value, ...buildItemPayload(form) }
    const { data } = await api.put(`/items/${route.params.id}`, payload)
    expectedVersion.value = data.version
    notice.value = '修改已保存。'
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <p v-if="loading" class="state">加载编辑信息中…</p>
  <section v-else class="panel publish-panel">
    <div class="publish-heading">
      <p class="eyebrow">编辑物品</p>
      <h1>更新物品与交接安排</h1>
      <p>保存时会核对版本；若其他操作已改变物品，请刷新后重试。</p>
    </div>
    <form v-if="expectedVersion !== null" class="publish-form" @submit.prevent="submit">
      <section class="form-section">
        <div class="section-heading"><span class="section-number">01</span><div><h2>基本信息</h2><p>沿用发布时的字段与价格规则。</p></div></div>
        <div class="form-fields">
          <label>物品名称<input v-model="form.title" maxlength="100"></label>
          <label>物品描述<textarea v-model="form.description" maxlength="2000" rows="4"></textarea></label>
          <div class="form-row">
            <label>分类<select v-model="form.category"><option>日用</option><option>家电</option><option>图书</option><option>数码</option><option>其他</option></select></label>
            <label>交易方式<select v-model="form.tradeType"><option value="FREE">免费送</option><option value="NEGOTIABLE">随便给</option><option value="FIXED_PRICE">标价</option></select></label>
          </div>
          <label v-if="form.tradeType === 'FIXED_PRICE'" class="price-input">最终价格（元）<input v-model.number="form.price" type="number" min="0.01" step="0.01"></label>
        </div>
      </section>
      <section class="form-section">
        <div class="section-heading"><span class="section-number">02</span><div><h2>物品图片</h2><p>图片操作独立保存，失败不会丢失其他表单内容。</p></div></div>
        <div class="form-fields">
          <ImagePicker :current-url="imageUrl" :disabled="busy || imageBusy" @select="imageFile = $event" />
          <div class="image-actions">
            <button type="button" :disabled="!imageFile || busy || imageBusy" @click="uploadImage">{{ imageBusy ? '处理中…' : '上传所选图片' }}</button>
            <button type="button" class="secondary" :disabled="busy || imageBusy" @click="removeImage">删除当前图片</button>
          </div>
          <p v-if="imageBusy && imageFile" class="upload-progress" role="progressbar" aria-label="图片上传进度" :aria-valuenow="imageProgress">正在上传 {{ imageProgress }}%</p>
        </div>
      </section>
      <section class="form-section">
        <div class="section-heading"><span class="section-number">03</span><div><h2>交接安排</h2><p>保存后旧时段会作为历史保留。</p></div></div>
        <div class="form-fields">
          <label>公共交接地点<input v-model="form.pickupLocation" maxlength="200"></label>
          <fieldset><legend>未来交接时段（北京时间）</legend>
            <div v-for="(slot, index) in form.slots" :key="index" class="slot-edit">
              <label><span>开始</span><input v-model="slot.startAt" type="datetime-local"></label><span class="slot-separator">至</span>
              <label><span>结束</span><input v-model="slot.endAt" type="datetime-local"></label>
              <button v-if="form.slots.length > 1" type="button" class="secondary compact-button" @click="removeSlot(index)">删除</button>
            </div>
            <button v-if="form.slots.length < 5" type="button" class="secondary add-slot-action" @click="addSlot">＋ 添加时段</button>
          </fieldset>
        </div>
      </section>
      <p v-if="notice" class="submit-notice success" role="status">{{ notice }}</p>
      <p v-if="error" class="submit-error error" role="alert">{{ error }}</p>
      <div class="edit-actions"><RouterLink class="secondary-link" :to="`/items/${route.params.id}`">取消</RouterLink><button :disabled="busy">{{ busy ? '保存中…' : '保存修改' }}</button></div>
    </form>
    <div v-else class="edit-load-error"><p class="error" role="alert">{{ error }}</p><button type="button" @click="load">重试</button></div>
  </section>
</template>
