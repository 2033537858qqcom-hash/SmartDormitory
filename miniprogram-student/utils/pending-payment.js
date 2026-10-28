// Retain the same id after an uncertain network response, so a retry is idempotent.
function storageKey(studentId, operation) {
  return `dorm_pending_payment:${studentId}:${operation}`;
}

function get(studentId, operation) {
  const key = storageKey(studentId, operation);
  let id = wx.getStorageSync(key);
  if (!id) {
    id = `MP${Date.now().toString(36)}${Math.random().toString(36).slice(2, 14).padEnd(12, '0')}`;
    wx.setStorageSync(key, id);
  }
  return id;
}

function clear(studentId, operation) {
  wx.removeStorageSync(storageKey(studentId, operation));
}

module.exports = { get, clear };
