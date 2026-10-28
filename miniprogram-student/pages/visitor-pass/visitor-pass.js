const api = require('../../utils/api.js');
const util = require('../../utils/util.js');
const qr = require('../../utils/qrcode-core.js');
const authNavigation = require('../../utils/auth-navigation.js');
const app = getApp();

Page({
  data: {
    activePass: null, roomInfo: '', showForm: false, submitting: false, loading: true, loadError: false,
    reasonPresets: ['亲友探访', '协助搬运行李', '学习交流', '其他'], reasonIndex: 0,
    form: { visitorName: '', visitorPhone: '', relation: '' }
  },
  onShow() {
    if (!app.isLoggedIn()) { authNavigation.goToLogin(); return; }
    this.loadPassData();
  },
  async loadPassData() {
    this.setData({ loading: true, loadError: false });
    try {
      const res = await api.get(`/auth/student-dashboard/${app.globalData.studentId}`);
      const dash = res.data;
      app.globalData.dashData = dash;
      const visitor = (dash.myVisitors || []).find(item => item.status === 0 && item.passCode);
      const activePass = visitor ? { ...visitor, visitDateText: util.formatTime(visitor.enterTime) } : null;
      const roomInfo = dash.room ? `${dash.building?.buildingName || ''} ${dash.room.roomNo}室` : '暂无宿舍信息';
      this.setData({ activePass, roomInfo, showForm: !activePass });
    } catch (_) { this.setData({ loadError: true }); }
    finally { this.setData({ loading: false }, () => { if (!this.data.loadError && this.data.activePass) this.renderQRCode(this.data.activePass.passCode); }); }
  },
  toggleNewForm() { this.setData({ showForm: !this.data.showForm }); },
  onInputName(e) { this.setData({ 'form.visitorName': e.detail.value }); },
  onInputPhone(e) { this.setData({ 'form.visitorPhone': e.detail.value }); },
  onInputRelation(e) { this.setData({ 'form.relation': e.detail.value }); },
  onReasonChange(e) { this.setData({ reasonIndex: Number(e.detail.value) }); },
  async handleSubmitVisitor() {
    if (this.data.submitting) return;
    const { form, reasonPresets, reasonIndex } = this.data;
    if (!form.visitorName.trim()) { wx.showToast({ title: '请输入访客姓名', icon: 'none' }); return; }
    if (!/^1\d{10}$/.test(form.visitorPhone.trim())) { wx.showToast({ title: '请输入正确的手机号', icon: 'none' }); return; }
    this.setData({ submitting: true });
    try {
      await api.post('/affair/visitor', {
        visitorName: form.visitorName.trim(), visitorPhone: form.visitorPhone.trim(),
        relation: form.relation.trim(), visitReason: reasonPresets[reasonIndex]
      });
      wx.showToast({ title: '访客登记成功', icon: 'success' });
      this.setData({ form: { visitorName: '', visitorPhone: '', relation: '' } });
      await this.loadPassData();
    } catch (_) {} finally { this.setData({ submitting: false }); }
  },
  renderQRCode(code) {
    const matrix = qr.create(code, { errorCorrectionLevel: 'M' }).modules;
    wx.createSelectorQuery().select('#qrCanvas').fields({ node: true, size: true }).exec(result => {
      if (!result || !result[0] || !result[0].node) return;
      const { node: canvas, width, height } = result[0];
      const dpr = wx.getSystemInfoSync().pixelRatio || 1;
      canvas.width = Math.round(width * dpr); canvas.height = Math.round(height * dpr);
      const ctx = canvas.getContext('2d');
      const size = Math.min(canvas.width, canvas.height);
      const unit = size / (matrix.size + 8);
      ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = '#1d1d1f';
      for (let row = 0; row < matrix.size; row++) {
        for (let col = 0; col < matrix.size; col++) {
          if (matrix.get(row, col)) {
            const x1 = Math.round((col + 4) * unit), y1 = Math.round((row + 4) * unit);
            const x2 = Math.round((col + 5) * unit), y2 = Math.round((row + 5) * unit);
            ctx.fillRect(x1, y1, x2 - x1, y2 - y1);
          }
        }
      }
    });
  }
});
