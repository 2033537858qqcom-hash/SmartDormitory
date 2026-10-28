const api = require('../../utils/api.js');
const authNavigation = require('../../utils/auth-navigation.js');
const util = require('../../utils/util.js');
const app = getApp();

Page({
  data: { name: '', code: '', result: null, resultMessage: '', busy: false, scanning: false, confirming: false },

  onShow() {
    if (!app.isLoggedIn()) { authNavigation.goToLogin(); return; }
    if (app.globalData.userInfo?.role !== 'DORM_MANAGER') {
      wx.reLaunch({ url: '/pages/home/home' });
      return;
    }
    this.setData({ name: app.globalData.userInfo.realName || '宿管' });
  },

  onCodeInput(event) {
    if (this.data.busy || this.data.scanning || this.data.confirming) return;
    this.setData({ code: event.detail.value, result: null, resultMessage: '' });
  },

  async scanPass() {
    if (this.data.busy || this.data.scanning || this.data.confirming) return;
    this.setData({ scanning: true, result: null, resultMessage: '', code: '' });
    try {
      const scan = await new Promise((resolve, reject) => wx.scanCode({ onlyFromCamera: true, scanType: ['qrCode'], success: resolve, fail: reject }));
      await this.verifyCode(scan.result);
    } catch (error) {
      if (!/cancel/i.test(error.errMsg || '')) wx.showToast({ title: '扫码失败，请检查相机权限或手动输入', icon: 'none' });
    } finally { this.setData({ scanning: false }); }
  },

  async verifyInput() { await this.verifyCode(this.data.code); },

  async verifyCode(raw) {
    if (this.data.busy || this.data.confirming) return;
    const code = String(raw || '').trim().toUpperCase();
    this.setData({ code, result: null, resultMessage: '' });
    if (!/^(VP-\d{6}|\d{1,6})$/.test(code)) {
      wx.showToast({ title: '请输入有效的访客通行码', icon: 'none' });
      return;
    }
    await this.submitVerification(code, false);
  },

  async submitVerification(code, checkOut) {
    this.setData({ busy: true });
    try {
      const response = await api.post('/affair/visitor-verify', { passCode: code, checkOut });
      const result = response.data;
      if (!result || !result.passCode) {
        wx.showToast({ title: '核验结果无效，请重试', icon: 'none' });
        return;
      }
      this.setData({
        code: result.passCode,
        result: { ...result, enterTimeText: util.formatTime(result.enterTime), leaveTimeText: util.formatTime(result.leaveTime) },
        resultMessage: response.message || ''
      });
    } catch (_) {
      this.setData({ result: null, resultMessage: '' });
    } finally { this.setData({ busy: false }); }
  },

  async confirmCheckOut() {
    const result = this.data.result;
    if (this.data.busy || this.data.scanning || this.data.confirming || !result || !result.passOk) return;
    this.setData({ confirming: true });
    try {
      const decision = await new Promise((resolve, reject) => wx.showModal({
        title: '确认访客签离', content: `确认 ${result.visitorName || '该访客'} 已离开？签离后通行码将失效。`,
        confirmText: '确认签离', success: resolve, fail: reject
      }));
      if (decision.confirm) await this.submitVerification(result.passCode, true);
    } catch (_) {} finally { this.setData({ confirming: false }); }
  },

  handleLogout() {
    if (this.data.busy || this.data.scanning || this.data.confirming) return;
    wx.showModal({ title: '退出登录', content: '确定退出当前宿管账号？', success: async result => {
      if (!result.confirm) return;
      try { await api.post('/auth/logout'); } catch (_) {}
      app.clearSession();
      authNavigation.goToLogin();
    } });
  }
});
