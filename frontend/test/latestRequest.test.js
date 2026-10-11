import test from 'node:test'
import assert from 'node:assert/strict'
import { createLatestRequest, isRequestCancelled } from '../src/latestRequest.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

test('only the latest overlapping request is accepted', async () => {
  const requests = createLatestRequest()
  const first = deferred()
  const second = deferred()

  const firstRun = requests.run(() => first.promise)
  const secondRun = requests.run(() => second.promise)
  second.resolve('new result')
  first.resolve('old result')

  assert.deepEqual(await secondRun, { accepted: true, value: 'new result' })
  assert.deepEqual(await firstRun, { accepted: false, cancelled: true })
})

test('starting a newer request aborts the previous request signal', async () => {
  const requests = createLatestRequest()
  let firstSignal
  const firstRun = requests.run((signal) => {
    firstSignal = signal
    return new Promise((resolve) => signal.addEventListener('abort', resolve, { once: true }))
  })

  const secondRun = requests.run(() => Promise.resolve('latest'))

  assert.equal(firstSignal.aborted, true)
  assert.equal((await firstRun).accepted, false)
  assert.equal((await secondRun).accepted, true)
})

test('component cleanup cancels an active request without surfacing an error', async () => {
  const requests = createLatestRequest()
  const run = requests.run((signal) => new Promise((resolve, reject) => {
    signal.addEventListener('abort', () => reject({ code: 'ERR_CANCELED' }), { once: true })
  }))

  requests.cancel()

  assert.deepEqual(await run, { accepted: false, cancelled: true })
  assert.equal(isRequestCancelled({ code: 'ERR_CANCELED' }), true)
  assert.equal(isRequestCancelled(new Error('network failed')), false)
})
