const test = require('node:test');
const assert = require('node:assert/strict');

let currentRoute = 'pages/home/home';
let currentToken = '';
const relaunches = [];
let pendingRequest;
const app = {
  globalData: { token: '', studentId: null },
  isLoggedIn() { return !!(this.globalData.token && this.globalData.studentId); },
  setLoginSession(token, user) { this.globalData.token = token; this.globalData.studentId = user.id; currentToken = token; },
  clearSession() { this.globalData.token = ''; this.globalData.studentId = null; currentToken = ''; }
};
global.getApp = () => app;
global.getCurrentPages = () => [{ route: currentRoute }];
global.wx = {
  getStorageSync: () => currentToken,
  showToast() {},
  reLaunch(options) { relaunches.push(options); },
  request(options) { pendingRequest = options; }
};
const api = require('../utils/api');
function page(name) {
  let p; global.Page = value => { p = value; };
  const path = require.resolve(`../pages/${name}/${name}.js`); delete require.cache[path]; require(path);
  p.data = structuredClone(p.data);
  p.setData = values => Object.assign(p.data, values);
  return p;
}

test('launch starts at login and unauthenticated home redirects only once', () => {
  assert.equal(require('../app.json').pages[0], 'pages/login/login');
  const home = page('home'); home.onShow(); home.onShow();
  assert.equal(relaunches.length, 1);
  assert.equal(relaunches[0].url, '/pages/login/login');
});

test('login keeps its loading state until navigation finishes and avoids duplicate submissions', async () => {
  currentRoute = 'pages/login/login';
  const login = page('login'); login.onLoad();
  let submitCount = 0;
  const original = api.post;
  api.post = async () => { submitCount++; return { data: { token: 'new-token', user: { id: 10, role: 'STUDENT' } } }; };
  try {
    await login.handleLogin();
    await login.handleLogin();
    assert.equal(submitCount, 1);
    assert.equal(login.data.loading, true);
    assert.equal(relaunches.at(-1).url, '/pages/home/home');
  } finally { api.post = original; }
});

test('a stale 401 response cannot clear a newer login', async () => {
  currentRoute = 'pages/home/home';
  currentToken = 'old-token'; app.globalData.token = 'old-token'; app.globalData.studentId = 10;
  const count = relaunches.length;
  const old = api.request({ url: '/some-protected-resource' });
  app.setLoginSession('fresh-token', { id: 10 });
  pendingRequest.success({ statusCode: 401, data: { message: 'expired' } });
  await assert.rejects(old);
  assert.equal(currentToken, 'fresh-token');
  assert.equal(relaunches.length, count);
});

test('missing stored token also clears an inconsistent in-memory session', async () => {
  currentRoute = 'pages/home/home'; currentToken = '';
  app.globalData.token = 'memory-only-token'; app.globalData.studentId = 10;
  const count = relaunches.length;
  const request = api.request({ url: '/some-protected-resource' });
  pendingRequest.success({ statusCode: 401 });
  await assert.rejects(request);
  assert.equal(app.isLoggedIn(), false);
  assert.equal(relaunches.length, count + 1);
  currentRoute = 'pages/login/login'; page('login').onLoad();
});

test('simultaneous expired responses trigger only one login navigation', async () => {
  currentRoute = 'pages/home/home';
  app.setLoginSession('expired-token', { id: 10 });
  const count = relaunches.length;
  const first = api.request({ url: '/first' }); const firstResponse = pendingRequest;
  const second = api.request({ url: '/second' }); const secondResponse = pendingRequest;
  firstResponse.success({ statusCode: 401 }); secondResponse.success({ statusCode: 401 });
  await Promise.all([assert.rejects(first), assert.rejects(second)]);
  assert.equal(relaunches.length, count + 1);
  currentRoute = 'pages/login/login'; page('login').onLoad();
});

test('a login 401 displays an error without reloading the login page', async () => {
  currentRoute = 'pages/login/login';
  const count = relaunches.length; let message = '';
  wx.showToast = options => { message = options.title; };
  const login = api.request({ url: '/auth/login', method: 'POST', skipAuth: true });
  pendingRequest.success({ statusCode: 401, data: { message: '账号或密码错误' } });
  await assert.rejects(login);
  assert.equal(relaunches.length, count);
  assert.equal(message, '账号或密码错误');
});
