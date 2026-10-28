export function messageTarget(message) {
  const tabs = { REPAIR: 'repair', ADJUST: 'affair', APPEAL: 'affair', VISITOR: 'affair', UTILITY: 'utility', RESIDENCE: 'affair' }
  const tab = tabs[message.msgType]
  const id = String(message.bizId || '')
  if (!tab || !/^\d+$/.test(id)) return null
  return { tab, key: `${message.msgType}:${id}` }
}
