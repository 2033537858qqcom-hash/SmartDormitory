let navigatingToLogin = false;

function goToLogin(showExpiredMessage = false) {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
  const currentPage = pages[pages.length - 1];
  if (navigatingToLogin || (currentPage && currentPage.route === 'pages/login/login')) return;

  navigatingToLogin = true;
  if (showExpiredMessage) wx.showToast({ title: '登录已失效，请重新登录', icon: 'none' });
  wx.reLaunch({
    url: '/pages/login/login',
    fail() {
      navigatingToLogin = false;
      wx.showToast({ title: '无法打开登录页，请重试', icon: 'none' });
    }
  });
}

function loginPageReady() {
  navigatingToLogin = false;
}

module.exports = { goToLogin, loginPageReady };
