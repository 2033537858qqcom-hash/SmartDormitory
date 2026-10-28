export function residenceError(form, today) {
  if (!form.reason?.trim()) return '请填写申请说明'
  if (form.requestType === 'HOLIDAY' && (!form.fromDate || !form.toDate || form.fromDate < today || form.toDate < form.fromDate)) return '请填写有效的留宿日期'
  return ''
}
