function cell(value) {
  let text = String(value ?? '')
  if (/^[\s]*[=+\-@\t\r]/.test(text)) text = `'${text}`
  return `"${text.replace(/"/g, '""')}"`
}

export function allocationReportCsv(result) {
  const rows = [['状态', '楼栋', '寝室', '床号', '学号', '姓名', '组内契合度', '说明']]
  rows.push(['对比', result.buildingName, '', '', '', '', result.smartAvgHarmonyScore, `智能平均分；传统平均分 ${result.traditionalAvgHarmonyScore ?? ''}；差值 ${result.scoreImprovement ?? ''}`])
  for (const room of result.roomAllocations || []) {
    for (const student of room.students || []) {
      rows.push(['已分配', result.buildingName, room.roomNo, student.bedNo, student.studentNo, student.realName, room.harmonyScore, room.explanation])
    }
  }
  for (const student of result.unallocatedStudents || []) {
    rows.push(['未分配', result.buildingName, '', '', student.studentNo, student.realName, '', student.reason])
  }
  rows.push(['说明', result.scoreNote || ''])
  return '\ufeff' + rows.map(row => row.map(cell).join(',')).join('\r\n')
}
