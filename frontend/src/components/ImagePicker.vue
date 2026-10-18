<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { validateImageFile } from '../imageUpload'

const props = defineProps({ currentUrl: String, disabled: Boolean })
const emit = defineEmits(['select'])
const previewUrl = ref('')
const error = ref('')

function revokePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

function choose(event) {
  const file = event.target.files?.[0]
  error.value = validateImageFile(file)
  if (error.value) {
    event.target.value = ''
    return
  }
  revokePreview()
  previewUrl.value = URL.createObjectURL(file)
  emit('select', file)
}

function clear() {
  revokePreview()
  error.value = ''
  emit('select', null)
}

watch(() => props.currentUrl, () => {
  revokePreview()
  error.value = ''
})
onUnmounted(revokePreview)
</script>

<template>
  <div class="image-picker">
    <div class="image-preview">
      <img v-if="previewUrl || currentUrl" :src="previewUrl || currentUrl" alt="物品图片预览">
      <span v-else>尚未选择图片</span>
    </div>
    <div class="image-picker-controls">
      <label class="file-action">选择 JPEG 或 PNG
        <input type="file" accept="image/jpeg,image/png" :disabled="disabled" @change="choose">
      </label>
      <button v-if="previewUrl" type="button" class="secondary compact-button" :disabled="disabled" @click="clear">取消选择</button>
      <small>最大 8MB；上传后服务端会检查真实格式、尺寸并移除元数据。</small>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </div>
  </div>
</template>
