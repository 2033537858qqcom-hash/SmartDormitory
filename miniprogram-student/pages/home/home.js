const api = require('../../utils/api.js');
const util = require('../../utils/util.js');
const tabbar = require('../../utils/tabbar.js');
const authNavigation = require('../../utils/auth-navigation.js');
const app = getApp();

Page({
  data: {
    loading: true,
    dash: null,
    loadError: false,
    currentDate: '',
    showRoommates: false,
    activeNotice: null,
    noticeVisible: false,
    noticeClosing: false,
    userInitial: '同',
    electricBalanceText: '¥ 0.00',
    waterBalanceText: '¥ 0.00',
    unratedCount: 0
  },

  onShow() {
    if (!app.isLoggedIn()) {
      authNavigation.goToLogin();
      return;
    }
    tabbar.update(this, { selected: 0, visible: !this.data.noticeVisible });
    this.loadDashboard();
  },

  onPullDownRefresh() {
    this.loadDashboard(() => {
      wx.stopPullDownRefresh();
    });
  },

  async loadDashboard(callback) {
    const studentId = app.globalData.studentId;
    if (!studentId) return;

    const now = new Date();
    const weekdays = ['日', '一', '二', '三', '四', '五', '六'];
    this.setData({ loading: true, loadError: false, currentDate: `${now.getMonth() + 1}月${now.getDate()}日 星期${weekdays[now.getDay()]}` });

    try {
      const res = await api.get(`/auth/student-dashboard/${studentId}`);
      if (res && res.data) {
        const dash = res.data;
        app.globalData.dashData = dash;

        const realName = dash.user?.realName || '同学';
        const userInitial = realName.charAt(0);

        const electricBalanceText = dash.room?.electricBalance == null ? '暂无数据' : util.formatMoney(dash.room.electricBalance);
        const waterBalanceText = dash.room?.waterBalance == null ? '暂无数据' : util.formatMoney(dash.room.waterBalance);
        const unratedCount = (dash.myRepairs || []).filter(order => order.status === 'COMPLETED' && !order.rating).length;

        // 格式化室友作息标签
        if (dash.roomRadar && dash.roomRadar.roommates) {
          dash.roomRadar.roommates.forEach(m => {
            const score = m.profile?.scheduleScore;
            m.scheduleLabel = score == null ? '尚未填写' : (score <= 2 ? '偏早作息' : (score >= 4 ? '偏晚作息' : '规律作息'));
          });
        }

        // 格式化公告与消息时间
        if (dash.messages) {
          dash.messages.forEach(msg => {
            msg.createTimeText = util.formatTime(msg.createTime);
          });
        }
        if (dash.notices) {
          dash.notices.forEach(n => {
            n.publishTimeText = util.formatDate(n.createTime);
          });
        }

        this.setData({
          dash,
          userInitial,
          electricBalanceText,
          waterBalanceText,
          unratedCount,
          loading: false
        });
      }
    } catch (e) {
      this.setData({ loading: false, loadError: true });
    } finally {
      if (typeof callback === 'function') callback();
    }
  },

  handleLogout() {
    wx.showModal({
      title: '确认退出',
      content: '是否确认退出当前登录状态？',
      confirmColor: '#EF4444',
      success: async (res) => {
        if (res.confirm) {
          try {
            await api.post('/auth/logout');
          } catch (_) {}
          app.clearSession();
          authNavigation.goToLogin();
        }
      }
    });
  },

  async markMsgRead(e) {
    const id = e.currentTarget.dataset.id;
    try {
      await api.post('/messages/read', { id });
      wx.showToast({ title: '已标为已读', icon: 'success' });
      this.loadDashboard();
    } catch (_) {}
  },

  toggleRoommates() {
    this.setData({ showRoommates: !this.data.showRoommates });
  },

  openNotice(e) {
    tabbar.update(this, { visible: false });
    this.setData({ activeNotice: e.currentTarget.dataset.notice, noticeVisible: true, noticeClosing: false });
  },

  closeNotice() {
    if (this.data.noticeClosing) return;
    this.setData({ noticeClosing: true });
    this._noticeTimer = setTimeout(() => { this.setData({ noticeVisible: false, noticeClosing: false, activeNotice: null }); tabbar.update(this, { visible: true }); }, 220);
  },

  onUnload() { if (this._noticeTimer) clearTimeout(this._noticeTimer); },
  stopPropagation() {},

  handleQuickNav(e) {
    const type = e.currentTarget.dataset.type;
    switch (type) {
      case 'adjust':
        if (this.data.dash?.room) {
          wx.navigateTo({ url: '/pages/adjust-apply/adjust-apply' });
        } else {
          wx.navigateTo({ url: '/pages/habit/habit' });
        }
        break;
      case 'repair':
        wx.switchTab({ url: '/pages/repair/repair' });
        break;
      case 'utility':
        wx.switchTab({ url: '/pages/utility/utility' });
        break;
      case 'visitor':
        wx.navigateTo({ url: '/pages/visitor-pass/visitor-pass' });
        break;
      case 'residence':
        wx.navigateTo({ url: '/pages/residence/residence' });
        break;
      case 'discipline':
        wx.switchTab({ url: '/pages/affair/affair' });
        break;
    }
  },

  navToHabit() {
    wx.navigateTo({ url: '/pages/habit/habit' });
  },

  navToRepair() {
    wx.switchTab({ url: '/pages/repair/repair' });
  },

  navToUtility() {
    wx.switchTab({ url: '/pages/utility/utility' });
  },

  navToAdjust() {
    wx.navigateTo({ url: '/pages/adjust-apply/adjust-apply' });
  }
});
