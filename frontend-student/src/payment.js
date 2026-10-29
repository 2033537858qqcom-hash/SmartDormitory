export async function submitDemoPayment(api, storage, random, payload) {
  const fingerprint = JSON.stringify([payload.roomId, payload.billId || null, payload.electricRecharge || 0, payload.waterRecharge || 0])
  const key = `dorm_payment_pending:${fingerprint}`
  let requestId = storage.getItem(key)
  if (!requestId) {
    const bytes = random.getRandomValues(new Uint8Array(12))
    requestId = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
    storage.setItem(key, requestId)
  }
  const result = await api.post('/utility/pay-or-recharge', { ...payload, requestId })
  if (result.code && result.code !== 200) throw new Error(result.message || '操作失败')
  storage.removeItem(key)
  return result
}

export function requireSandboxForm(data) {
  if (data?.mode !== 'SDK_PAGE' || !data.formHtml) throw new Error('支付宝沙箱收银台不可用，请检查支付配置')
  return data.formHtml
}
