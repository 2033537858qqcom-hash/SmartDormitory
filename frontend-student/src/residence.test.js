import { test } from 'node:test'
import assert from 'node:assert/strict'
import { residenceError } from './residence.js'

test('leave requests require a reason and an ordered future date range', () => {
  assert.match(residenceError({ requestType: 'HOLIDAY', reason: '', fromDate: '2026-11-01', toDate: '2026-11-02' }, '2026-10-28'), /说明/)
  assert.match(residenceError({ requestType: 'HOLIDAY', reason: '留校', fromDate: '2026-11-03', toDate: '2026-11-02' }, '2026-10-28'), /日期/)
  assert.equal(residenceError({ requestType: 'HOLIDAY', reason: '留校', fromDate: '2026-11-01', toDate: '2026-11-02' }, '2026-10-28'), '')
  assert.equal(residenceError({ requestType: 'CHECKIN', reason: '办理入住' }, '2026-10-28'), '')
})
