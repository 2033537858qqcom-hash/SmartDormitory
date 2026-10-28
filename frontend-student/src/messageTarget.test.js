import { test } from 'node:test'
import assert from 'node:assert/strict'
import { messageTarget } from './messageTarget.js'

test('business messages point to the corresponding student record', () => {
  assert.deepEqual(messageTarget({ msgType: 'REPAIR', bizId: '12' }), { tab: 'repair', key: 'REPAIR:12' })
  assert.deepEqual(messageTarget({ msgType: 'ADJUST', bizId: '3' }), { tab: 'affair', key: 'ADJUST:3' })
  assert.deepEqual(messageTarget({ msgType: 'APPEAL', bizId: '7' }), { tab: 'affair', key: 'APPEAL:7' })
  assert.deepEqual(messageTarget({ msgType: 'RESIDENCE', bizId: '8' }), { tab: 'affair', key: 'RESIDENCE:8' })
  assert.equal(messageTarget({ msgType: 'SYSTEM' }), null)
  assert.equal(messageTarget({ msgType: 'REPAIR', bizId: 'bad' }), null)
})
