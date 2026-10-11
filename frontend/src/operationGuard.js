export async function runOperationOnce(pendingKeys, key, operation) {
  if (pendingKeys.has(key)) return { executed: false }

  pendingKeys.add(key)
  try {
    return { executed: true, value: await operation() }
  } finally {
    pendingKeys.delete(key)
  }
}
