const api = require('../../utils/api.js');
const authNavigation = require('../../utils/auth-navigation.js');
const app = getApp();

Page(require('../../utils/theme').withTheme({
  data: {
    username: '20230101',
    password: '123456',
    loading: false,
    checkingSession: true,
    demoAccounts: [
      { username: '20230101', name: '陈嘉豪', desc: '已入住学生' },
      { username: '20230105', name: '刘子墨', desc: '已入住学生' },
      { username: '20260101', name: '陆景行', desc: '待分寝 男 2026级新生' },
      { username: '20230201', name: '苏婉清', desc: '已入住学生' },
      { username: 'dm01', name: '楼栋宿管', desc: '宿管演示账号' }
    ]
  },

  onLoad() {
    authNavigation.loginPageReady();
    if (app.isLoggedIn()) {
      this.openHome();
    } else {
      this.setData({ checkingSession: false });
    }
  },

  openHome() {
    wx.reLaunch({
      url: app.globalData.userInfo?.role === 'DORM_MANAGER' ? '/pages/manager-visitor/manager-visitor' : '/pages/home/home',
      fail: () => {
        this.setData({ loading: false, checkingSession: false });
        wx.showToast({ title: '打开首页失败，请重试', icon: 'none' });
      }
    });
  },

  onInputUsername(e) {
    this.setData({ username: e.detail.value });
  },

  onInputPassword(e) {
    this.setData({ password: e.detail.value });
  },

  selectDemoAccount(e) {
    const account = e.currentTarget.dataset.account;
    this.setData({
      username: account.username,
      password: '123456'
    });
  },

  async handleLogin() {
    if (this.data.loading) return;
    const { username, password } = this.data;
    if (!username.trim()) {
      wx.showToast({ title: '请输入账号', icon: 'none' });
      return;
    }
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }

    this.setData({ loading: true });

    let navigating = false;
    try {
      const res = await api.post('/auth/login', {
        username: username.trim(),
        password
      }, { skipAuth: true });

      if (res && res.data) {
        const { token, user } = res.data;
        if (!user || !token || !['STUDENT', 'DORM_MANAGER'].includes(user.role)) {
          wx.showToast({ title: '请使用学生或宿管账号登录', icon: 'none' });
          this.setData({ loading: false });
          return;
        }

        app.setLoginSession(token, user);
        navigating = true;
        this.openHome();
      }
    } catch (err) {
      // 错误信息已由 api.js 弹出 toast
    } finally {
      if (!navigating) this.setData({ loading: false });
    }
  }
}));
