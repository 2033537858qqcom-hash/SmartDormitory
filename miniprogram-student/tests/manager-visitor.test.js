const test = require('node:test');
const assert = require('node:assert/strict');

let currentRoute = 'pages/login/login';
const navigations = [];
const app = {
  globalData: { token: '', studentId: null, userInfo: null },
  isLoggedIn() { return !!this.globalData.token; },
  setLoginSession(token, user) { this.globalData.token = token; this.globalData.studentId = user.id; this.globalData.userInfo = user; },
  clearSession() { this.globalData.token = ''; this.globalData.userInfo = null; }
};
global.getApp = () => app;
global.getCurrentPages = () => [{ route: currentRoute }];
global.wx = { showToast() {}, reLaunch(options) { navigations.push(options.url); }, showModal() {} };
const api = require('../utils/api');
function page(name) {
  let p; global.Page = definition => { p = definition; };
  const file = require.resolve(`../pages/${name}/${name}.js`); delete require.cache[file]; require(file);
  p.data = structuredClone(p.data); p.setData = values => Object.assign(p.data, values); return p;
}

test('manager login and restored session go to the manager workspace', async () => {
  api.post = async () => ({ data: { token: 'manager-token', user: { id: 4, role: 'DORM_MANAGER' } } });
  const login = page('login'); await login.handleLogin();
  assert.equal(navigations.at(-1), '/pages/manager-visitor/manager-visitor');
  const restored = page('login'); restored.onLoad();
  assert.equal(navigations.at(-1), '/pages/manager-visitor/manager-visitor');
});

test('scan previews visitor and confirmation signs out the verified code', async () => {
  currentRoute = 'pages/manager-visitor/manager-visitor';
  const calls = [];
  api.post = async (url, data) => {
    calls.push({ url, data });
    return { message: data.checkOut ? '已签离' : '核验通过', data: { passCode: data.passCode, visitorName: '访客', status: data.checkOut ? 1 : 0, passOk: !data.checkOut } };
  };
  wx.scanCode = options => options.success({ result: 'VP-123456' });
  const p = page('manager-visitor');
  await p.scanPass();
  assert.deepEqual(calls[0].data, { passCode: 'VP-123456', checkOut: false });
  assert.equal(p.data.result.passOk, true);
  wx.showModal = options => options.success({ confirm: true });
  await p.confirmCheckOut();
  assert.deepEqual(calls[1].data, { passCode: 'VP-123456', checkOut: true });
  assert.equal(p.data.result.status, 1);
});

test('cancelled scans, invalid codes, and failed verifications cannot sign out a previous visitor', async () => {
  let calls = 0; api.post = async () => { calls++; throw Error('failed'); };
  const p = page('manager-visitor'); p.setData({ result: { passCode: 'VP-123456', passOk: true } });
  wx.scanCode = options => options.fail({ errMsg: 'scanCode:fail cancel' });
  await p.scanPass();
  assert.equal(p.data.result, null);
  await p.verifyCode('not a pass');
  assert.equal(calls, 0);
  await p.verifyCode('VP-123456');
  assert.equal(calls, 1); assert.equal(p.data.result, null);
});

test('an already signed-out pass cannot be signed out again', async () => {
  let calls = 0;
  api.post = async () => { calls++; return { message: '该通行码已签离', data: { passCode: 'VP-123456', status: 1, passOk: false } }; };
  const p = page('manager-visitor');
  await p.verifyCode('VP-123456'); await p.confirmCheckOut();
  assert.equal(calls, 1);
  assert.equal(p.data.result.status, 1);
});

test('cancelling checkout does not send a checkout request', async () => {
  let calls = 0; api.post = async () => { calls++; };
  wx.showModal = options => options.success({ confirm: false });
  const p = page('manager-visitor'); p.setData({ result: { passCode: 'VP-123456', visitorName: '访客', passOk: true } });
  await p.confirmCheckOut();
  assert.equal(calls, 0); assert.equal(p.data.confirming, false);
});

test('student session cannot open manager workspace', () => {
  app.globalData.userInfo = { id: 10, role: 'STUDENT' }; app.globalData.token = 'student-token';
  const p = page('manager-visitor'); p.onShow();
  assert.equal(navigations.at(-1), '/pages/home/home');
});
