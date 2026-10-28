const api = require('../../utils/api.js');
const util = require('../../utils/util.js');
const authNavigation = require('../../utils/auth-navigation.js');
const app = getApp();

Page({
  data: {
    requestType: 'CHECKIN',
    fromDate: '',
    toDate: '',
    reason: '',
    submitting: false,
    loading: true,
    loadError: false,
    requests: []
  },

  onShow() {
    if (!app.isLoggedIn()) {
      authNavigation.goToLogin();
      return;
    }

    const today = new Date();
    const future = new Date(today.getTime() + 30 * 86400000);
    this.setData({
      fromDate: util.formatDate(today),
      toDate: util.formatDate(future)
    });

    this.loadRequests();
  },

  onPullDownRefresh() {
    this.loadRequests(() => wx.stopPullDownRefresh());
  },

  selectType(e) {
    this.setData({ requestType: e.currentTarget.dataset.type });
  },

  onFromDateChange(e) {
    this.setData({ fromDate: e.detail.value });
  },

  onToDateChange(e) {
    this.setData({ toDate: e.detail.value });
  },

  onInputReason(e) {
    this.setData({ reason: e.detail.value });
  },

  async loadRequests(callback) {
    this.setData({ loading: true, loadError: false });
    try {
      const res = await api.get('/residence/requests');
      if (res && res.data) {
        const list = res.data.map(item => {
          let typeText = '入住登记报到';
          if (item.requestType === 'HOLIDAY') typeText = '寒暑假留宿';
          else if (item.requestType === 'CHECKOUT') typeText = '提前退宿';

          let statusText = '审核中';
          let statusClass = 'badge-amber';
          if (item.status === 'APPROVED') {
            statusText = '已审批通过';
            statusClass = 'badge-green';
          } else if (item.status === 'REJECTED') {
            statusText = '已驳回';
            statusClass = 'badge-red';
          }

          return {
            ...item,
            typeText,
            statusText,
            statusClass,
            fromDate: item.fromDate ? util.formatDate(item.fromDate) : null,
            toDate: item.toDate ? util.formatDate(item.toDate) : null
          };
        });

        this.setData({ requests: list });
      }
    } catch (_) { this.setData({ loadError: true }); }
    finally {
      this.setData({ loading: false });
      if (typeof callback === 'function') callback();
    }
  },

  async handleSubmit() {
    if (this.data.submitting) return;
    const { requestType, fromDate, toDate, reason } = this.data;
    if (!reason.trim()) {
      wx.showToast({ title: '请填写申请说明', icon: 'none' });
      return;
    }

    const payload = {
      requestType,
      reason: reason.trim()
    };

    if (requestType === 'HOLIDAY') {
      if (!fromDate || !toDate || toDate < fromDate || fromDate < util.formatDate(new Date())) {
        wx.showToast({ title: '请选择完整的留宿起止日期', icon: 'none' });
        return;
      }
      payload.fromDate = fromDate;
      payload.toDate = toDate;
    }

    this.setData({ submitting: true });

    try {
      await api.post('/residence/requests', payload);
      wx.showToast({ title: '申请提交成功', icon: 'success' });
      this.setData({ reason: '' });
      this.loadRequests();
    } catch (_) {}
    finally {
      this.setData({ submitting: false });
    }
  }
});
