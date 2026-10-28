import { test } from 'node:test'
import assert from 'node:assert/strict'
import { allocationReportCsv } from './allocationReport.js'

test('allocation report includes assigned and unassigned students with safe CSV cells', () => {
  const csv = allocationReportCsv({ buildingName: 'A栋', scoreNote: '仅本次分配', roomAllocations: [
    { roomNo: '301', harmonyScore: 90, explanation: '作息相差 2 档', students: [{ studentNo: '=1+1', realName: '张,明', bedNo: 1 }] }
  ], unallocatedStudents: [{ studentNo: '02', realName: '李', reason: '空床不足' }] })
  assert.match(csv, /"'=1\+1"/)
  assert.match(csv, /"张,明"/)
  assert.match(csv, /空床不足/)
  assert.match(csv, /仅本次分配/)
})
