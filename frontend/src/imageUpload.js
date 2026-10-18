export const MAX_IMAGE_BYTES = 8 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png']

export function validateImageFile(file) {
  if (!file) return '请选择图片'
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) return '仅支持 JPEG 或 PNG 图片'
  if (file.size > MAX_IMAGE_BYTES) return '图片不能超过 8MB'
  return ''
}

export function imageFormData(file) {
  const body = new FormData()
  body.append('file', file)
  return body
}

export function uploadItemImage(api, itemId, expectedVersion, file, onProgress) {
  return api.put(`/items/${itemId}/image`, imageFormData(file), {
    params: { expectedVersion },
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress ? (event) => {
      if (event.total) onProgress(Math.round((event.loaded * 100) / event.total))
    } : undefined,
  })
}
