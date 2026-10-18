<script setup>
import { onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, messageOf } from '../api'
import { applyRecommendedPrice, buildPublishPayload } from '../pricing'
import { validateItemForm } from '../itemForm'
import { createLatestRequest } from '../latestRequest'
import { uploadItemImage } from '../imageUpload'
import ImagePicker from '../components/ImagePicker.vue'

const router = useRouter()
const busy = ref(false)
const error = ref('')
const pricingBusy = ref(false)
const pricingError = ref('')
const pricingResult = ref(null)
const pricingStale = ref(false)
const pricingRequests = createLatestRequest()
const imageFile = ref(null)
const createdItem = ref(null)
const imageProgress = ref(0)
const form = reactive({
  title: '',
  description: '',
  category: '日用',
  tradeType: 'FREE',
  price: null,
  brandModel: '',
  purchasePrice: null,
  usageDuration: '',
  condition: '',
  defects: '',
  accessories: '',
  pickupLocation: '',
  slots: [{ startAt: '', endAt: '' }],
})

const addSlot = () => {
  if (form.slots.length < 5) form.slots.push({ startAt: '', endAt: '' })
}
const removeSlot = (index) => form.slots.splice(index, 1)

const pricingInput = () => ({
  tradeType: form.tradeType,
  title: form.title,
  category: form.category,
  description: form.description,
  brandModel: form.brandModel || null,
  purchasePrice: form.purchasePrice === '' ? null : form.purchasePrice,
  usageDuration: form.usageDuration || null,
  condition: form.condition || null,
  defects: form.defects || null,
  accessories: form.accessories || null,
})

watch(
  () => [
    form.title,
    form.description,
    form.category,
    form.tradeType,
    form.brandModel,
    form.purchasePrice,
    form.usageDuration,
    form.condition,
    form.defects,
    form.accessories,
  ],
  () => {
    pricingRequests.cancel()
    pricingBusy.value = false
    if (pricingResult.value) pricingStale.value = true
    if (form.tradeType !== 'FIXED_PRICE') {
      pricingResult.value = null
      pricingError.value = ''
      pricingStale.value = false
    }
  },
)

async function estimate() {
  if (pricingBusy.value) return
  pricingBusy.value = true
  pricingError.value = ''
  pricingResult.value = null
  pricingStale.value = false
  try {
    const result = await pricingRequests.run((signal) => api.post('/ai/pricing', pricingInput(), { signal }))
    if (!result.accepted) return
    pricingResult.value = result.value.data
    pricingBusy.value = false
  } catch (e) {
    pricingError.value = messageOf(e)
    pricingBusy.value = false
  }
}

function useRecommended() {
  if (applyRecommendedPrice(form, pricingResult.value)) pricingStale.value = false
}

async function submit() {
  if (busy.value) return
  if (createdItem.value) return retryImage()
  error.value = ''
  error.value = validateItemForm(form)
  if (error.value) return
  busy.value = true
  try {
    const payload = buildPublishPayload(form)
    const { data } = await api.post('/items', payload)
    if (imageFile.value) {
      try {
        imageProgress.value = 0
        await uploadItemImage(api, data.id, data.version, imageFile.value,
          (progress) => { imageProgress.value = progress })
      } catch (e) {
        createdItem.value = { id: data.id, version: data.version }
        error.value = `物品已发布，但图片上传失败：${messageOf(e)}。你可以重试图片上传，不会重复发布物品。`
        return
      }
    }
    router.push(`/items/${data.id}`)
  } catch (e) {
    error.value = messageOf(e)
    if (e.response?.status === 401) router.push('/login')
  } finally {
    busy.value = false
  }
}

async function retryImage() {
  if (!createdItem.value || !imageFile.value || busy.value) return
  busy.value = true
  imageProgress.value = 0
  error.value = ''
  try {
    await uploadItemImage(api, createdItem.value.id, createdItem.value.version, imageFile.value,
      (progress) => { imageProgress.value = progress })
    router.push(`/items/${createdItem.value.id}`)
  } catch (e) {
    error.value = `图片仍未上传成功：${messageOf(e)}。物品已经保存，可以继续重试。`
  } finally {
    busy.value = false
  }
}

onUnmounted(pricingRequests.cancel)
</script>

<template>
  <section class="panel publish-panel">
    <div class="publish-heading">
      <p class="eyebrow">发布闲置</p>
      <h1>把物品交给刚好需要的邻居</h1>
      <p>信息越清楚，邻居越容易放心做决定。</p>
    </div>

    <form class="publish-form" @submit.prevent="submit">
      <section class="form-section" aria-labelledby="basic-heading">
        <div class="section-heading">
          <span class="section-number">01</span>
          <div>
            <h2 id="basic-heading">基本信息</h2>
            <p>介绍物品，并选择合适的分享方式。</p>
          </div>
        </div>
        <div class="form-fields">
          <label>物品名称<input v-model="form.title" maxlength="100" placeholder="例如：九成新落地灯"></label>
          <label>物品描述<textarea v-model="form.description" maxlength="2000" rows="3" placeholder="描述使用情况、外观和适合的使用场景"></textarea></label>
          <div class="form-row">
            <label>分类
              <select v-model="form.category">
                <option>日用</option><option>家电</option><option>图书</option><option>数码</option><option>其他</option>
              </select>
            </label>
            <label>交易方式
              <select v-model="form.tradeType">
                <option value="FREE">免费送</option><option value="NEGOTIABLE">随便给</option><option value="FIXED_PRICE">标价</option>
              </select>
            </label>
          </div>
          <label v-if="form.tradeType === 'FIXED_PRICE'" class="price-input">最终价格（元）
            <input v-model.number="form.price" type="number" min="0.01" step="0.01" placeholder="请输入最终挂牌价">
            <small>AI 结果只是参考，你可以随时手工调整。</small>
          </label>
        </div>
      </section>

      <section v-if="form.tradeType === 'FIXED_PRICE'" class="form-section ai-section" aria-labelledby="ai-heading">
        <div class="section-heading">
          <span class="section-number">02</span>
          <div>
            <h2 id="ai-heading">AI 定价参考</h2>
            <p>补充物品情况，获得温和、可解释的价格建议。</p>
          </div>
        </div>
        <div class="ai-pricing">
          <div class="form-row">
            <label>品牌／型号（可选）<input v-model="form.brandModel" maxlength="120"></label>
            <label>购入价格（可选）<input v-model.number="form.purchasePrice" type="number" min="0" step="0.01"></label>
          </div>
          <div class="form-row">
            <label>使用时间（可选）<input v-model="form.usageDuration" maxlength="100" placeholder="例如：约一年"></label>
            <label>成色情况<input v-model="form.condition" maxlength="300" placeholder="例如：轻微使用痕迹"></label>
          </div>
          <div class="form-row">
            <label>已知缺陷<textarea v-model="form.defects" maxlength="500" rows="2"></textarea></label>
            <label>配件情况<textarea v-model="form.accessories" maxlength="300" rows="2"></textarea></label>
          </div>
          <div class="ai-actions">
            <button type="button" class="ai-estimate-action" :disabled="pricingBusy" @click="estimate">
              {{ pricingBusy ? '正在分析物品信息…' : 'AI 帮我估价' }}
            </button>
            <p class="ai-disclaimer">未查询实时成交数据，仅供参考，最终价格由你决定。</p>
          </div>
          <p v-if="pricingBusy" class="pricing-feedback pricing-loading" role="status">正在整理价格区间和影响因素，请稍候。</p>
          <p v-if="pricingError" class="pricing-feedback error" role="alert">
            <strong>暂时无法完成估价</strong>
            <span>{{ pricingError }} 手动填写价格仍可正常发布。</span>
          </p>
          <p v-if="pricingStale" class="pricing-feedback stale">物品信息已修改，旧估价可能不再准确，请重新估价。</p>

          <div v-if="pricingResult?.status === 'NEED_MORE_INFO'" class="pricing-result pricing-more-info" role="status">
            <span class="result-kicker">需要更多信息</span>
            <h3>再补充一点，估价会更可靠</h3>
            <p v-if="pricingResult.missingFields?.length">建议补充：{{ pricingResult.missingFields.join('、') }}</p>
            <ul><li v-for="question in pricingResult.questions" :key="question">{{ question }}</li></ul>
          </div>
          <div v-else-if="pricingResult?.status === 'ESTIMATE'" class="pricing-result estimate-result" role="status">
            <div class="estimate-summary">
              <div>
                <span>建议区间</span>
                <strong>¥{{ pricingResult.minPrice }}–¥{{ pricingResult.maxPrice }}</strong>
              </div>
              <div>
                <span>推荐挂牌价</span>
                <strong>¥{{ pricingResult.recommendedPrice }}</strong>
              </div>
              <div>
                <span>置信度</span>
                <strong>{{ pricingResult.confidence }}</strong>
              </div>
            </div>
            <div class="estimate-detail">
              <h3>为什么是这个价格</h3>
              <p>{{ pricingResult.rationale }}</p>
              <ul><li v-for="factor in pricingResult.factors" :key="factor">{{ factor }}</li></ul>
              <p class="uncertainty"><strong>不确定性</strong>{{ pricingResult.uncertainty }}</p>
            </div>
            <button type="button" class="use-price-action" :disabled="pricingStale" @click="useRecommended">填入推荐价 ¥{{ pricingResult.recommendedPrice }}</button>
          </div>
        </div>
      </section>

      <section class="form-section" aria-labelledby="handover-heading">
        <div class="section-heading">
          <span class="section-number">{{ form.tradeType === 'FIXED_PRICE' ? '03' : '02' }}</span>
          <div>
            <h2 id="handover-heading">交接安排</h2>
            <p>填写公共地点和你方便的时间。</p>
          </div>
        </div>
        <div class="form-fields">
          <label>公共交接地点<input v-model="form.pickupLocation" maxlength="200" placeholder="例如：社区服务中心门口"></label>
          <fieldset>
            <legend>可选交接时段（北京时间）</legend>
            <div v-for="(slot, index) in form.slots" :key="index" class="slot-edit">
              <label><span>开始</span><input v-model="slot.startAt" type="datetime-local"></label>
              <span class="slot-separator">至</span>
              <label><span>结束</span><input v-model="slot.endAt" type="datetime-local"></label>
              <button v-if="form.slots.length > 1" type="button" class="secondary compact-button" @click="removeSlot(index)">删除</button>
            </div>
            <button v-if="form.slots.length < 5" type="button" class="secondary add-slot-action" @click="addSlot">＋ 添加时段</button>
          </fieldset>
        </div>
      </section>

      <section class="form-section" aria-labelledby="image-heading">
        <div class="section-heading">
          <span class="section-number">{{ form.tradeType === 'FIXED_PRICE' ? '04' : '03' }}</span>
          <div><h2 id="image-heading">物品图片（可选）</h2><p>选择后会在物品发布成功后上传。</p></div>
        </div>
        <div class="form-fields">
          <ImagePicker :disabled="busy" @select="imageFile = $event" />
        </div>
      </section>

      <p v-if="error" class="submit-error error" role="alert">{{ error }}</p>
      <p v-if="busy && imageFile && imageProgress" class="upload-progress" role="progressbar" aria-label="图片上传进度" :aria-valuenow="imageProgress">正在上传图片 {{ imageProgress }}%</p>
      <div v-if="createdItem" class="publish-recovery">
        <button type="button" :disabled="busy || !imageFile" @click="retryImage">{{ busy ? '重试中…' : '重试上传图片' }}</button>
        <RouterLink class="secondary-link" :to="`/items/${createdItem.id}`">暂不上传，查看物品</RouterLink>
      </div>
      <button v-else class="publish-submit" :disabled="busy">{{ busy ? '发布中…' : '确认发布' }}</button>
    </form>
  </section>
</template>
