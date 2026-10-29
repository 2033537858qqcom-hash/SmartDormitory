import { test } from 'node:test'
import assert from 'node:assert/strict'
import { submitDemoPayment } from './payment.js'

test('failed payment retry reuses its request id and success clears it', async () => {
  const data = new Map()
  const storage = { getItem: key => data.get(key) || null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) }
  let counter = 0
  const random = { getRandomValues: bytes => bytes.fill(++counter) }
  const ids = []
  const api = { post: async (_, payload) => { ids.push(payload.requestId); if (ids.length === 1) throw new Error('timeout'); return { code: 200 } } }
  const payload = { roomId: 1, electricRecharge: 10 }
  await assert.rejects(submitDemoPayment(api, storage, random, payload), /timeout/)
  await submitDemoPayment(api, storage, random, payload)
  await submitDemoPayment(api, storage, random, payload)
  assert.equal(ids[0], ids[1])
  assert.notEqual(ids[1], ids[2])
})

test('sandbox checkout rejects missing form instead of performing a simulated recharge', async () => {
  const payment = await import('./payment.js')
  assert.throws(() => payment.requireSandboxForm({ mode: 'DEMO_FALLBACK' }), /支付宝沙箱/)
  assert.equal(payment.requireSandboxForm({ mode: 'SDK_PAGE', formHtml: '<form></form>' }), '<form></form>')
})
