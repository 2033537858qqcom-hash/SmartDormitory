const api = require('../../utils/api.js');
const util = require('../../utils/util.js');
const config = require('../../utils/config.js');
const tabbar = require('../../utils/tabbar.js');
const authNavigation = require('../../utils/auth-navigation.js');
const app = getApp();

const HIGH_PRIORITY_KEYWORDS = ['跳闸', '短路', '火花', '爆管', '漏水'];

const STAR_HINTS = {
  1: '非常不满意，问题未解决',
  2: '不满意，维修较慢/态度欠佳',
  3: '一般，基本修复',
  4: '满意，响应迅速且解决问题',
  5: '非常满意，师傅专业且服务热情！'
};

Page({
  data: {
    activeTab: 'list',
    formSheetClosing: false,
    loading: true,
    loadError: false,
    roomSummary: '',
    categories: [
      { key: 'ELECTRIC', name: '电力照明', icon: '电' },
      { key: 'WATER', name: '水暖管道', icon: '水' },
      { key: 'WOOD', name: '门窗桌椅', icon: '木' },
      { key: 'NETWORK', name: '网络空调', icon: '网' }
    ],
    form: {
      repairType: 'ELECTRIC',
      title: '',
      description: ''
    },
    photoUrls: [],
    isHighPriority: false,
    submitting: false,
    orderList: [],
    unratedCount: 0,

    // 评分弹窗
    rateModalVisible: false,
    rateSheetClosing: false,
    curRateOrder: null,
    rateForm: {
      rating: 5,
      comment: ''
    },
    rateHintText: '非常满意，师傅专业且服务热情！',
    submittingRate: false
  },

  onShow() {
    if (!app.isLoggedIn()) {
      authNavigation.goToLogin();
      return;
    }
    tabbar.update(this, { selected: 1, visible: this.data.activeTab !== 'submit' && !this.data.rateModalVisible });
    this.loadOrders();
  },

  onPullDownRefresh() {
    this.loadOrders(() => wx.stopPullDownRefresh());
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === 'list' && this.data.activeTab === 'submit') {
      if (this.data.submitting || this.data.formSheetClosing) return;
      this.setData({ formSheetClosing: true });
      this._formSheetTimer = setTimeout(() => { this.setData({ activeTab: 'list', formSheetClosing: false }); tabbar.update(this, { visible: true }); }, 220);
    } else {
      tabbar.update(this, { visible: tab !== 'submit' });
      this.setData({ activeTab: tab, formSheetClosing: false });
    }
  },

  stopPropagation() {},
  onUnload() { clearTimeout(this._formSheetTimer); clearTimeout(this._rateSheetTimer); },

  selectCategory(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ 'form.repairType': key });
  },

  onInputTitle(e) {
    const val = e.detail.value;
    const isHigh = HIGH_PRIORITY_KEYWORDS.some(kw => val.includes(kw));
    this.setData({
      'form.title': val,
      isHighPriority: isHigh
    });
  },

  onInputDesc(e) {
    this.setData({ 'form.description': e.detail.value });
  },

  async chooseAndUploadPhoto() {
    wx.chooseMedia({
      count: 3 - this.data.photoUrls.length,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        wx.showLoading({ title: '照片上传中...' });
        for (const file of res.tempFiles) {
          try {
            const relUrl = await api.uploadImage(file.tempFilePath);
            // 补全前缀
            const fullUrl = relUrl.startsWith('http') ? relUrl : `${config.fileBaseUrl}${relUrl}`;
            this.setData({
              photoUrls: [...this.data.photoUrls, fullUrl]
            });
          } catch (_) {}
        }
        wx.hideLoading();
      }
    });
  },

  deletePhoto(e) {
    const idx = e.currentTarget.dataset.index;
    const list = [...this.data.photoUrls];
    list.splice(idx, 1);
    this.setData({ photoUrls: list });
  },

  previewImage(e) {
    const url = e.currentTarget.dataset.url;
    wx.previewImage({
      current: url,
      urls: [url]
    });
  },

  async handleSubmitOrder() {
    if (this.data.submitting) return;
    const { form, photoUrls } = this.data;
    if (!form.title.trim()) {
      wx.showToast({ title: '请输入故障简述标题', icon: 'none' });
      return;
    }
    if (!form.description.trim()) {
      wx.showToast({ title: '请填写详细情况说明', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });

    try {
      const payload = {
        repairType: form.repairType,
        title: form.title.trim(),
        description: form.description.trim(),
        photoUrls: JSON.stringify(photoUrls.map(url => url.startsWith(config.fileBaseUrl) ? url.slice(config.fileBaseUrl.length) : url))
      };

      const res = await api.post('/repair/submit', payload);
      wx.showToast({ title: res.message || '报修提交成功', icon: 'success' });

      // 重置表单并切换到列表
      this.setData({
        form: { repairType: 'ELECTRIC', title: '', description: '' },
        photoUrls: [],
        isHighPriority: false,
        activeTab: 'list'
      });
      tabbar.update(this, { visible: true });
      this.loadOrders();
    } catch (_) {}
    finally {
      this.setData({ submitting: false });
    }
  },

  async loadOrders(callback) {
    const studentId = app.globalData.studentId;
    if (!studentId) return;

    this.setData({ loading: true, loadError: false });
    try {
      const res = await api.get(`/auth/student-dashboard/${studentId}`);
      if (res && res.data) {
        const dash = res.data;
        app.globalData.dashData = dash;
        const list = (dash.myRepairs || []).map(item => {
          let statusText = '待派单';
          let statusClass = 'badge-amber';
          if (item.status === 'ASSIGNED') {
            statusText = '已派单';
            statusClass = 'badge-blue';
          } else if (item.status === 'PROCESSING') {
            statusText = '维修中';
            statusClass = 'badge-blue';
          } else if (item.status === 'COMPLETED') {
            statusText = '已完工';
            statusClass = 'badge-green';
          } else if (item.status === 'RATED') {
            statusText = '已评价';
            statusClass = 'badge-green';
          } else if (item.status === 'CANCELLED') {
            statusText = '已取消';
            statusClass = 'badge-gray';
          }

          let priorityText = '普通';
          let priorityClass = 'badge-blue';
          if (item.priority === 'HIGH') {
            priorityText = '高危紧急';
            priorityClass = 'badge-red';
          } else if (item.priority === 'LOW') {
            priorityText = '低优';
            priorityClass = 'badge-gray';
          }

          let photos = [];
          try {
            const raw = typeof item.photoUrls === 'string' ? JSON.parse(item.photoUrls) : item.photoUrls;
            photos = Array.isArray(raw) ? raw.map(u => u.startsWith('http') ? u : `${config.fileBaseUrl}${u}`) : [];
          } catch (_) { photos = []; }

          return {
            ...item,
            statusText,
            statusClass,
            priorityText,
            priorityClass,
            photos,
            buildingName: item.roomId === dash.room?.id ? (dash.building?.buildingName || '') : '',
            roomNo: item.roomId === dash.room?.id ? (dash.room?.roomNo || '') : '',
            overdue: !!item.dueAt && ['PENDING', 'ASSIGNED', 'PROCESSING'].includes(item.status) && new Date(item.dueAt.replace(' ', 'T')).getTime() < Date.now(),
            dueAtText: util.formatTime(item.dueAt)
          };
        });

        const unratedCount = list.filter(o => o.status === 'COMPLETED' && !o.rating).length;
        this.setData({ orderList: list, unratedCount, roomSummary: dash.room ? `${dash.building?.buildingName || ''} ${dash.room.roomNo}室` : '' });
      }
    } catch (_) { this.setData({ loadError: true }); }
    finally {
      this.setData({ loading: false });
      if (typeof callback === 'function') callback();
    }
  },

  openRateModal(e) {
    const order = e.currentTarget.dataset.order;
    tabbar.update(this, { visible: false });
    this.setData({
      rateModalVisible: true,
      rateSheetClosing: false,
      curRateOrder: order,
      rateForm: { rating: 5, comment: '' },
      rateHintText: STAR_HINTS[5]
    });
  },

  closeRateModal() {
    if (this.data.rateSheetClosing) return;
    this.setData({ rateSheetClosing: true });
    this._rateSheetTimer = setTimeout(() => { this.setData({ rateModalVisible: false, rateSheetClosing: false, curRateOrder: null }); tabbar.update(this, { visible: true }); }, 220);
  },

  setStarRating(e) {
    const score = e.currentTarget.dataset.score;
    this.setData({
      'rateForm.rating': score,
      rateHintText: STAR_HINTS[score] || ''
    });
  },

  onInputRateComment(e) {
    this.setData({ 'rateForm.comment': e.detail.value });
  },

  async submitRating() {
    const { curRateOrder, rateForm } = this.data;
    if (!curRateOrder || this.data.submittingRate) return;

    this.setData({ submittingRate: true });
    try {
      await api.post('/repair/rate', {
        id: curRateOrder.id,
        rating: rateForm.rating,
        ratingComment: rateForm.comment.trim()
      });
      wx.showToast({ title: '评价成功，感谢反馈', icon: 'success' });
      this.closeRateModal();
      this.loadOrders();
    } catch (_) {}
    finally {
      this.setData({ submittingRate: false });
    }
  }
});
