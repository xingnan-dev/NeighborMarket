export function isRequestCancelled(error) {
  return error?.code === 'ERR_CANCELED' || error?.name === 'AbortError'
}

export function createLatestRequest() {
  let sequence = 0
  let activeController = null

  async function run(request) {
    const requestSequence = ++sequence
    activeController?.abort()
    const controller = new AbortController()
    activeController = controller

    try {
      const value = await request(controller.signal)
      if (requestSequence !== sequence) return { accepted: false, cancelled: true }
      return { accepted: true, value }
    } catch (error) {
      if (requestSequence !== sequence || isRequestCancelled(error)) {
        return { accepted: false, cancelled: true }
      }
      throw error
    } finally {
      if (requestSequence === sequence) activeController = null
    }
  }

  function cancel() {
    sequence += 1
    activeController?.abort()
    activeController = null
  }

  return { run, cancel }
}
