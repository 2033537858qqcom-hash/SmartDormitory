App({
  globalData: {
    token: '',
    studentId: null,
    userInfo: null,
    dashData: null
  },

  onLaunch() {
    const token = wx.getStorageSync('dorm_student_token');
    const studentId = wx.getStorageSync('dorm_student_id');
    const userInfo = wx.getStorageSync('dorm_student_info');

    if (token && studentId) {
      this.globalData.token = token;
      this.globalData.studentId = studentId;
      this.globalData.userInfo = userInfo || null;
    }
  },

  setLoginSession(token, user) {
    this.globalData.token = token;
    this.globalData.studentId = user.id;
    this.globalData.userInfo = user;
    wx.setStorageSync('dorm_student_token', token);
    wx.setStorageSync('dorm_student_id', user.id);
    wx.setStorageSync('dorm_student_info', user);
  },

  clearSession() {
    this.globalData.token = '';
    this.globalData.studentId = null;
    this.globalData.userInfo = null;
    this.globalData.dashData = null;
    wx.removeStorageSync('dorm_student_token');
    wx.removeStorageSync('dorm_student_id');
    wx.removeStorageSync('dorm_student_info');
  },

  isLoggedIn() {
    return !!(this.globalData.token && this.globalData.studentId);
  }
});
