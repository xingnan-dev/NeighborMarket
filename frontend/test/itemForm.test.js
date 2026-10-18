import test from 'node:test'
import assert from 'node:assert/strict'
import { buildItemPayload, populateItemForm, validateItemForm } from '../src/itemForm.js'

const validForm = () => ({
  title: '台灯', description: '正常使用', category: '日用', tradeType: 'FREE', price: null,
  pickupLocation: '社区门口',
  slots: [{ startAt: '2026-10-20T10:00', endAt: '2026-10-20T11:00' }],
})

test('publish and edit share the same item validation rules', () => {
  const form = validForm()
  assert.equal(validateItemForm(form), '')
  form.tradeType = 'FIXED_PRICE'
  assert.equal(validateItemForm(form), '标价必须大于0元')
  form.price = 12
  form.slots[0].endAt = form.slots[0].startAt
  assert.equal(validateItemForm(form), '请填写有效的未来时段')
})

test('edit payload normalizes price and local date times', () => {
  const form = validForm()
  form.tradeType = 'NEGOTIABLE'
  form.price = 99
  const payload = buildItemPayload(form)
  assert.equal(payload.price, null)
  assert.equal(payload.slots[0].startAt, '2026-10-20T10:00:00')
})

test('detail response populates an editable form without timezone conversion', () => {
  const form = validForm()
  populateItemForm(form, {
    title: '书架', description: '九成新', category: '其他', tradeType: 'FIXED_PRICE',
    price: 20, pickupLocation: '服务中心',
  }, [{ startAt: '2026-10-21T09:30:00', endAt: '2026-10-21T10:30:00' }])
  assert.equal(form.title, '书架')
  assert.equal(form.price, 20)
  assert.deepEqual(form.slots, [{ startAt: '2026-10-21T09:30', endAt: '2026-10-21T10:30' }])
})
