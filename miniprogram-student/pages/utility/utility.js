const api = require('../../utils/api.js');
const util = require('../../utils/util.js');
const app = getApp();
const pendingPayment = require('../../utils/pending-payment.js');
const tabbar = require('../../utils/tabbar.js');
const authNavigation = require('../../utils/auth-navigation.js');

Page(require('../../utils/theme').withTheme({
  data: {
    room: null,
    loading: true,
    loadError: false,
    electricBalanceText: '¥ 0.00',
    waterBalanceText: '¥ 0.00',
    rechargeTarget: 'ELECTRIC',
    payChannel: 'DEMO',
    demoSupported: false,
    paymentConfigLoaded: false,
    rechargeAmount: 50,
    amountPills: [20, 50, 100, 200],
    recharging: false,
    paying: false,
    rechargeVisible: false,
    rechargeClosing: false,
    bills: [],
    paymentLogs: []
  },

  onShow() {
    if (!app.isLoggedIn()) {
      authNavigation.goToLogin();
      return;
    }
    tabbar.update(this, { selected: 2, visible: !this.data.rechargeVisible });
    this.loadUtilityData();
    this.loadPaymentConfig();
  },

  async loadPaymentConfig() {
    try {
      const result = await api.get('/utility/pay/config');
      this.setData({ demoSupported: result.data?.demoSupported === true, paymentConfigLoaded: true });
    } catch (_) { this.setData({ demoSupported: false, paymentConfigLoaded: true }); }
  },

  onPullDownRefresh() {
    this.loadUtilityData(() => wx.stopPullDownRefresh());
  },

  selectTarget(e) {
    this.setData({ rechargeTarget: e.currentTarget.dataset.target });
  },

  selectChannel(e) {
    this.setData({ payChannel: e.currentTarget.dataset.channel });
  },

  openRecharge() { tabbar.update(this, { visible: false }); this.setData({ rechargeVisible: true, rechargeClosing: false }); },
  closeRecharge() {
    if (this.data.recharging || this.data.rechargeClosing) return;
    this.setData({ rechargeClosing: true });
    this._sheetTimer = setTimeout(() => { this.setData({ rechargeVisible: false, rechargeClosing: false }); tabbar.update(this, { visible: true }); }, 220);
  },
  stopPropagation() {},
  onUnload() { if (this._sheetTimer) clearTimeout(this._sheetTimer); },

  selectAmount(e) {
    this.setData({ rechargeAmount: Number(e.currentTarget.dataset.amount) });
  },

  async loadUtilityData(callback) {
    const studentId = app.globalData.studentId;
    if (!studentId) return;

    this.setData({ loading: true, loadError: false });
    try {
      const res = await api.get(`/auth/student-dashboard/${studentId}`);
      if (res && res.data) {
        const dash = res.data;
        app.globalData.dashData = dash;
        const room = dash.room;

        if (room) {
          const eleBal = Number(room.electricBalance || 0);
          const watBal = Number(room.waterBalance || 0);
          const electricBalanceText = room.electricBalance == null ? '暂无数据' : util.formatMoney(eleBal);
          const waterBalanceText = room.waterBalance == null ? '暂无数据' : util.formatMoney(watBal);
          this.setData({
            room,
            electricBalanceText,
            waterBalanceText,
            bills: dash.bills || []
          });

          this.loadPaymentLogs(room.id);
        } else {
          this.setData({ room: null, bills: [], paymentLogs: [] });
        }
      }
    } catch (_) { this.setData({ loadError: true }); }
    finally {
      this.setData({ loading: false });
      if (typeof callback === 'function') callback();
    }
  },

  async loadPaymentLogs(roomId) {
    try {
      const res = await api.get(`/utility/payment-logs?roomId=${roomId}`);
      if (res && res.data) {
        const logs = res.data.map(l => ({
          ...l,
          actionText: l.actionType === 'PAY_BILL' ? '账单核销' : '账户充值',
          createTimeText: util.formatTime(l.createTime)
        }));
        this.setData({ paymentLogs: logs });
      }
    } catch (_) {}
  },

  async handleRecharge() {
    const { room, rechargeTarget, rechargeAmount } = this.data;
    if (!room || this.data.recharging || this.data.paying) return;

    this.setData({ recharging: true });

    try {
      if (this.data.payChannel === 'ALIPAY') {
        wx.showToast({ title: '请在学生 H5 使用支付宝沙箱', icon: 'none' });
        return;
      }

      if (!this.data.demoSupported) { wx.showToast({ title: '演示充值未启用', icon: 'none' }); return; }
      const isEle = rechargeTarget === 'ELECTRIC';
      const payload = {
        roomId: room.id,
        electricRecharge: isEle ? rechargeAmount : 0,
        waterRecharge: !isEle ? rechargeAmount : 0,
        requestId: pendingPayment.get(app.globalData.studentId, `recharge:${room.id}:${rechargeTarget}:${rechargeAmount}`)
      };

      const res = await api.post('/utility/pay-or-recharge', payload);
      pendingPayment.clear(app.globalData.studentId, `recharge:${room.id}:${rechargeTarget}:${rechargeAmount}`);
      this.setData({ rechargeVisible: false, rechargeClosing: false });
      tabbar.update(this, { visible: true });
      wx.showModal({
        title: '【演示模拟】充值成功',
        content: `${res.message || '账户充值核销成功'}\n流水号：${res.data?.txnNo || 'DEMO-OK'}\n（本系统仅为答辩演示仿真，非真实微信支付）`,
        showCancel: false,
        confirmText: '我知道了'
      });
      this.loadUtilityData();
    } catch (_) {}
    finally {
      this.setData({ recharging: false });
    }
  },

  async handlePayBill(e) {
    const bill = e.currentTarget.dataset.bill;
    const room = this.data.room;
    if (!bill || !room || this.data.paying || this.data.recharging) return;
    if (!this.data.demoSupported) { wx.showToast({ title: '演示缴费未启用', icon: 'none' }); return; }
    this.setData({ paying: true });

    wx.showModal({
      title: '确认缴费核销',
      content: `是否确认使用寝室预存水电余额抵扣核销【${bill.billMonth}】账单 ¥${bill.totalCost}？`,
      success: async (res) => {
        if (res.confirm) {
          wx.showLoading({ title: '核销处理中...' });
          try {
            await api.post('/utility/pay-or-recharge', {
              roomId: room.id,
              billId: bill.id,
              requestId: pendingPayment.get(app.globalData.studentId, `bill:${room.id}:${bill.id}`)
            });
            pendingPayment.clear(app.globalData.studentId, `bill:${room.id}:${bill.id}`);
            wx.hideLoading();
            wx.showToast({ title: '账单缴费成功', icon: 'success' });
            this.loadUtilityData();
          } catch (_) {
            wx.hideLoading();
          } finally {
            this.setData({ paying: false });
          }
        } else {
          this.setData({ paying: false });
        }
      },
      fail: () => this.setData({ paying: false })
    });
  }
}));
