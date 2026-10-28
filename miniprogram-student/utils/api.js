const config = require('./config.js');
const authNavigation = require('./auth-navigation.js');

/**
 * 封装通用微信小程序网络请求
 */
function request(options) {
  const token = options.skipAuth ? '' : wx.getStorageSync('dorm_student_token');
  const headers = {
    'Content-Type': 'application/json',
    ...(options.header || {})
  };
  if (token) {
    headers['Authorization'] = token;
  }

  const url = options.url.startsWith('http') ? options.url : `${config.baseUrl}${options.url}`;

  return new Promise((resolve, reject) => {
    wx.request({
      url,
      method: options.method || 'GET',
      data: options.data || {},
      header: headers,
      timeout: 15000,
      success(res) {
        if (res.statusCode === 401) {
          if (options.skipAuth) {
            const message = res.data?.message || '账号或密码错误';
            if (!options.silent) wx.showToast({ title: message, icon: 'none', duration: 2500 });
            reject(new Error(message));
            return;
          }
          const currentToken = wx.getStorageSync('dorm_student_token');
          if (token && token === currentToken) {
            const app = getApp();
            if (app && app.clearSession) app.clearSession();
            authNavigation.goToLogin(true);
          } else if (!token && !currentToken) {
            const app = getApp();
            if (app && app.clearSession) app.clearSession();
            authNavigation.goToLogin(true);
          }
          reject(new Error('未登录或登录已失效'));
          return;
        }

        const data = res.data;
        if (data && typeof data === 'object') {
          if (data.code === 200) {
            resolve(data);
          } else {
            const errMsg = data.message || '请求处理失败';
            if (!options.silent) {
              wx.showToast({ title: errMsg, icon: 'none', duration: 2500 });
            }
            reject(new Error(errMsg));
          }
        } else {
          resolve(data);
        }
      },
      fail(err) {
        const msg = err.errMsg && err.errMsg.includes('fail') ? '网络连接异常，请检查后端服务' : (err.errMsg || '请求超时');
        if (!options.silent) {
          wx.showToast({ title: msg, icon: 'none', duration: 2500 });
        }
        reject(err);
      }
    });
  });
}

/**
 * 上传图片文件封装
 */
function uploadImage(filePath) {
  const token = wx.getStorageSync('dorm_student_token');
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: config.uploadUrl,
      filePath,
      name: 'file',
      header: {
        'Authorization': token || ''
      },
      success(res) {
        try {
          const data = JSON.parse(res.data);
          if (data.code === 200) {
            // 返回相对路径或拼接全路径
            const url = data.data?.url || data.data;
            resolve(url);
          } else {
            wx.showToast({ title: data.message || '上传失败', icon: 'none' });
            reject(new Error(data.message));
          }
        } catch (e) {
          reject(e);
        }
      },
      fail(err) {
        wx.showToast({ title: '图片上传失败', icon: 'none' });
        reject(err);
      }
    });
  });
}

module.exports = {
  request,
  get: (url, data, options = {}) => request({ url, method: 'GET', data, ...options }),
  post: (url, data, options = {}) => request({ url, method: 'POST', data, ...options }),
  uploadImage
};
