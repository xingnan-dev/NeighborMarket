import test from 'node:test'
import assert from 'node:assert/strict'
import { MAX_IMAGE_BYTES, imageFormData, uploadItemImage, validateImageFile } from '../src/imageUpload.js'

test('accepts only jpeg and png within the client size limit', () => {
  assert.equal(validateImageFile({ type: 'image/jpeg', size: MAX_IMAGE_BYTES }), '')
  assert.equal(validateImageFile({ type: 'image/png', size: 10 }), '')
  assert.match(validateImageFile({ type: 'image/svg+xml', size: 10 }), /JPEG 或 PNG/)
  assert.match(validateImageFile({ type: 'image/png', size: MAX_IMAGE_BYTES + 1 }), /8MB/)
})

test('builds multipart data without trusting the original filename', () => {
  const file = new Blob(['image'], { type: 'image/png' })
  const body = imageFormData(file)
  assert.equal(body.get('file').type, 'image/png')
  assert.equal(body.get('file').size, file.size)
})

test('uploads with the currently loaded item version', async () => {
  const calls = []
  const api = { put: (...args) => { calls.push(args); return Promise.resolve({ data: {} }) } }
  const file = new Blob(['image'], { type: 'image/png' })
  await uploadItemImage(api, 12, 4, file)
  assert.equal(calls[0][0], '/items/12/image')
  assert.equal(calls[0][2].params.expectedVersion, 4)
  assert.equal(calls[0][2].headers['Content-Type'], 'multipart/form-data')
})
