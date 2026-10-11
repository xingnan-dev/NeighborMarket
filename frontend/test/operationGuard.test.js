import test from 'node:test'
import assert from 'node:assert/strict'
import { runOperationOnce } from '../src/operationGuard.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

test('double trigger for the same operation executes only one request', async () => {
  const pending = new Set()
  const request = deferred()
  let calls = 0
  const operation = () => {
    calls += 1
    return request.promise
  }

  const first = runOperationOnce(pending, 'handover:1', operation)
  const duplicate = await runOperationOnce(pending, 'handover:1', operation)
  request.resolve('completed')

  assert.deepEqual(duplicate, { executed: false })
  assert.deepEqual(await first, { executed: true, value: 'completed' })
  assert.equal(calls, 1)
})

test('unrelated operation keys can run independently', async () => {
  const pending = new Set()
  const first = deferred()
  const second = deferred()

  const firstRun = runOperationOnce(pending, 'comment', () => first.promise)
  const secondRun = runOperationOnce(pending, 'pickup', () => second.promise)
  assert.deepEqual([...pending].sort(), ['comment', 'pickup'])

  first.resolve('commented')
  second.resolve('configured')
  assert.equal((await firstRun).executed, true)
  assert.equal((await secondRun).executed, true)
})

test('failed operation releases its key for retry', async () => {
  const pending = new Set()
  await assert.rejects(
    runOperationOnce(pending, 'publish', () => Promise.reject(new Error('failed'))),
    /failed/,
  )
  assert.equal(pending.has('publish'), false)

  const retry = await runOperationOnce(pending, 'publish', () => Promise.resolve('retried'))
  assert.deepEqual(retry, { executed: true, value: 'retried' })
})
