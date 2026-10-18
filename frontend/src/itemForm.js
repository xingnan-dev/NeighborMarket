const localDateTime = (value) => value ? `${value.slice(0, 16)}:00` : null

export function validateItemForm(form) {
  if (!form.title.trim() || !form.description.trim() || !form.pickupLocation.trim()) {
    return '请完整填写物品与公共交接地点'
  }
  if (form.tradeType === 'FIXED_PRICE' && (!form.price || Number(form.price) <= 0)) {
    return '标价必须大于0元'
  }
  if (!form.slots.length || form.slots.length > 5 || form.slots.some(
    (slot) => !slot.startAt || !slot.endAt || slot.endAt <= slot.startAt,
  )) return '请填写有效的未来时段'
  return ''
}

export function buildItemPayload(form) {
  return {
    title: form.title,
    description: form.description,
    category: form.category,
    tradeType: form.tradeType,
    price: form.tradeType === 'FIXED_PRICE' ? Number(form.price) : null,
    pickupLocation: form.pickupLocation,
    slots: form.slots.map((slot) => ({
      startAt: localDateTime(slot.startAt),
      endAt: localDateTime(slot.endAt),
    })),
  }
}

export function populateItemForm(form, item, slots) {
  form.title = item.title
  form.description = item.description
  form.category = item.category
  form.tradeType = item.tradeType
  form.price = item.tradeType === 'FIXED_PRICE' ? Number(item.price) : null
  form.pickupLocation = item.pickupLocation || ''
  form.slots = slots.length ? slots.map((slot) => ({
    startAt: slot.startAt.slice(0, 16),
    endAt: slot.endAt.slice(0, 16),
  })) : [{ startAt: '', endAt: '' }]
}
