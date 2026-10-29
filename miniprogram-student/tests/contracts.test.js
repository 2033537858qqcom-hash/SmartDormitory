const test = require('node:test');
const assert = require('node:assert/strict');
const api = require('../utils/api');
const app = { globalData: { studentId: 10, dashData: null }, isLoggedIn: () => true };
global.getApp = () => app;
global.wx = { showToast() {}, showLoading() {}, hideLoading() {}, stopPullDownRefresh() {}, navigateBack() {}, redirectTo() {}, switchTab() {} };

function page(name) {
  let definition;
  global.Page = value => { definition = value; };
  const path = require.resolve(`../pages/${name}/${name}.js`);
  delete require.cache[path];
  require(path);
  definition.data = structuredClone(definition.data);
  definition.setData = function (values, callback) {
    for (const [path, value] of Object.entries(values)) {
      const parts = path.split('.'); let target = this.data;
      while (parts.length > 1) target = target[parts.shift()];
      target[parts[0]] = value;
    }
    if (callback) callback();
  };
  return definition;
}

test('habit questionnaire reads and writes backend five-point fields', async () => {
  let sent;
  api.get = async () => ({ data: { habitProfile: { scheduleScore: 1, sleepDepthScore: 4, acTempScore: 5, cleanlinessScore: 3, atmosphereScore: 2, odorSensitiveScore: 5 } } });
  api.post = async (_path, data) => { sent = data; return { code: 200 }; };
  const p = page('habit'); p.renderRadar = () => {};
  await p.initData(); await p.handleSaveProfile();
  assert.equal(p.data.form.sleepDepthScore, 4);
  assert.equal(sent.acTempScore, 5);
  assert.equal(sent.odorSensitiveScore, 5);
  assert.equal(p.data.dimensionList[0].options.length, 5);
});

test('habit load failure does not draw a default profile', async () => {
  let renders = 0;
  api.get = async () => { throw Error('offline'); };
  const p = page('habit'); p.renderRadar = () => { renders++; };
  await p.initData();
  assert.equal(p.data.loading, false); assert.equal(p.data.loadError, true); assert.equal(renders, 0);
});

test('repair list reads student dashboard and preserves structured photos and comments', async () => {
  let path, sent;
  api.get = async value => { path = value; return { data: { myRepairs: [{ id: 9, status: 'COMPLETED', photoUrls: '["/uploads/a.jpg"]' }] } }; };
  api.post = async (_path, data) => { sent = data; return { code: 200 }; };
  const p = page('repair'); await p.loadOrders();
  assert.match(path, /student-dashboard\/10/);
  assert.equal(p.data.orderList.length, 1);
  assert.match(p.data.orderList[0].photos[0], /uploads\/a.jpg$/);
  p.data.curRateOrder = { id: 9 }; p.data.rateForm = { rating: 4, comment: '很好' };
  await p.submitRating(); assert.equal(sent.ratingComment, '很好');
});

test('adjust request sends selected free bed number', async () => {
  let payload;
  api.post = async (_path, data) => { payload = data; return { code: 200 }; };
  const p = page('adjust-apply');
  p.data.buildings = [{ id: 1 }]; p.data.rooms = [{ id: 2 }]; p.data.freeBeds = [{ bedNo: 4 }];
  p.data.reason = '调宿'; p.data.bedIndex = 0;
  await p.handleSubmit();
  assert.equal(payload.targetBedNo, 4);
});

test('adjust page loads current residence before filtering available rooms', async () => {
  api.get = async path => {
    if (path.includes('student-dashboard')) return { data: { user: { gender: 1 }, room: { id: 1 }, building: { id: 4 }, bed: { bedNo: 1 } } };
    if (path === '/dorm/buildings') return { data: [{ id: 4, genderType: 1, buildingName: '一号楼' }, { id: 5, genderType: 2, buildingName: '二号楼' }] };
    return { data: [{ id: 1, roomNo: '301', beds: [{ status: 0, bedNo: 3 }] }, { id: 2, roomNo: '302', beds: [{ status: 0, bedNo: 4 }] }] };
  };
  const p = page('adjust-apply'); await p.loadCurrent();
  assert.equal(p.data.currentRoom.id, 1); assert.equal(p.data.buildings.length, 1);
  assert.equal(p.data.rooms.length, 1); assert.equal(p.data.freeBeds[0].bedNo, 4);
});

test('affair records use dashboard fields and appeal sends record id', async () => {
  let sent;
  api.get = async () => ({ data: { room: {}, myDisciplines: [{ id: 6, status: 1, recordType: 'LATE_RETURN' }], myAdjusts: [{ id: 7, status: 'APPROVED' }], myVisitors: [{ id: 8, status: 0, passCode: 'VP-123456' }] } });
  api.post = async (_path, data) => { sent = data; return { code: 200 }; };
  const p = page('affair'); await p.loadAffairData();
  assert.equal(p.data.disciplineList.length, 1); assert.equal(p.data.adjustList[0].statusText, '已同意');
  assert.equal(p.data.visitorList[0].passCode, 'VP-123456');
  p.data.curDiscRecord = { id: 6 }; p.data.appealReason = '说明'; await p.submitAppeal();
  assert.equal(sent.id, 6);
});

test('visitor registration submits supported fields and displays active pass code', async () => {
  let payload, rendered;
  api.post = async (_path, data) => { payload = data; return { code: 200 }; };
  api.get = async () => ({ data: { room: { roomNo: '301' }, myVisitors: [{ status: 0, passCode: 'VP-123456', visitorName: '访客' }] } });
  const p = page('visitor-pass'); p.renderQRCode = code => { rendered = code; };
  p.data.form = { visitorName: '访客', visitorPhone: '13800138000', relation: '家人' };
  await p.handleSubmitVisitor();
  assert.equal(payload.visitReason, '亲友探访');
  assert.equal(payload.visitorPhone, '13800138000');
  assert.equal(rendered, 'VP-123456');
  assert.equal(p.data.activePass.passCode, 'VP-123456');
});

test('visitor refresh failure hides outdated pass instead of redrawing it', async () => {
  let renders = 0; api.get = async () => { throw Error('offline'); };
  const p = page('visitor-pass'); p.data.activePass = { passCode: 'VP-123456' }; p.renderQRCode = () => { renders++; };
  await p.loadPassData(); assert.equal(p.data.loadError, true); assert.equal(renders, 0);
});

test('payment retry retains id until a successful response', async () => {
  const storage = new Map();
  wx.getStorageSync = key => storage.get(key);
  wx.setStorageSync = (key, value) => storage.set(key, value);
  wx.removeStorageSync = key => storage.delete(key);
  wx.showModal = () => {};
  const ids = [];
  api.post = async (_path, data) => { ids.push(data.requestId); if (ids.length === 1) throw Error('timeout'); return { data: {} }; };
  const p = page('utility'); p.data.room = { id: 5 }; p.loadUtilityData = () => {};
  await p.handleRecharge(); await p.handleRecharge(); await p.handleRecharge();
  assert.equal(ids[0], ids[1]);
  assert.notEqual(ids[1], ids[2]);
  assert.match(ids[0], /^[A-Za-z0-9_-]{16,30}$/);
});

test('bundled QR encoder supports pass codes without TextEncoder', () => {
  const original = global.TextEncoder; global.TextEncoder = undefined;
  try {
    const bundled = require('../utils/qrcode-core').create('VP-123456', { errorCorrectionLevel: 'M' }).modules;
    const reference = require('../../frontend-student/node_modules/qrcode/lib/core/qrcode').create('VP-123456', { errorCorrectionLevel: 'M' }).modules;
    assert.equal(bundled.size, reference.size);
    assert.deepEqual(Array.from(bundled.data), Array.from(reference.data));
  } finally { global.TextEncoder = original; }
});

test('home roommate disclosure and notice sheet preserve selected content', async () => {
  const p = page('home');
  let visible = true; p.getTabBar = () => ({ setData: value => { if ('visible' in value) visible = value.visible; } });
  p.toggleRoommates(); assert.equal(p.data.showRoommates, true);
  const notice = { id: 2, title: '公告', content: '正文' };
  p.openNotice({ currentTarget: { dataset: { notice } } });
  assert.equal(visible, false);
  assert.equal(p.data.noticeVisible, true); assert.equal(p.data.activeNotice.content, '正文');
  p.closeNotice(); assert.equal(p.data.noticeClosing, true);
  await new Promise(resolve => setTimeout(resolve, 250));
  assert.equal(p.data.noticeVisible, false); assert.equal(p.data.activeNotice, null);
  assert.equal(visible, true);
});

test('home opens the habit page through a normal navigation route', () => {
  let url; wx.navigateTo = options => { url = options.url; };
  const p = page('home'); p.navToHabit();
  assert.equal(url, '/pages/habit/habit');
  assert.equal(require('../app.json').tabBar.list.some(tab => tab.pagePath === 'pages/habit/habit'), false);
});

test('recharge sheet closes without submitting a payment', async () => {
  let calls = 0; api.post = async () => { calls++; };
  const p = page('utility'); let visible = true;
  p.getTabBar = () => ({ setData: value => { if ('visible' in value) visible = value.visible; } });
  p.openRecharge(); assert.equal(p.data.rechargeVisible, true); assert.equal(visible, false);
  p.closeRecharge(); assert.equal(p.data.rechargeClosing, true);
  await new Promise(resolve => setTimeout(resolve, 250));
  assert.equal(p.data.rechargeVisible, false); assert.equal(visible, true); assert.equal(calls, 0);
});

test('repair form hides the navigation and cannot close during submission', async () => {
  const p = page('repair'); let visible = true;
  p.getTabBar = () => ({ setData: value => { if ('visible' in value) visible = value.visible; } });
  p.switchTab({ currentTarget: { dataset: { tab: 'submit' } } });
  assert.equal(p.data.activeTab, 'submit'); assert.equal(visible, false);
  p.data.submitting = true;
  p.switchTab({ currentTarget: { dataset: { tab: 'list' } } });
  assert.equal(p.data.formSheetClosing, false);
  p.data.submitting = false;
  p.switchTab({ currentTarget: { dataset: { tab: 'list' } } });
  await new Promise(resolve => setTimeout(resolve, 250));
  assert.equal(p.data.activeTab, 'list'); assert.equal(visible, true);
});

test('custom navigation switches tabs without repeating the active route', () => {
  let component; global.Component = value => { component = value; };
  const path = require.resolve('../custom-tab-bar/index.js'); delete require.cache[path]; require(path);
  let url; wx.switchTab = options => { url = options.url; };
  component.methods.switchTab.call(component, { currentTarget: { dataset: { index: 0 } } });
  assert.equal(url, undefined);
  component.methods.switchTab.call(component, { currentTarget: { dataset: { index: 2 } } });
  assert.equal(url, '/pages/utility/utility');
});

test('expired session clears in-memory state and returns to login', async () => {
  let cleared = false, route = '';
  app.clearSession = () => { cleared = true; app.globalData.studentId = null; };
  wx.getStorageSync = () => 'expired-token';
  wx.request = options => options.success({ statusCode: 401 });
  wx.reLaunch = options => { route = options.url; if (options.complete) options.complete(); };
  await assert.rejects(api.request({ url: '/auth/student-dashboard/10' }));
  assert.equal(cleared, true);
  assert.equal(app.globalData.studentId, null);
  assert.equal(route, '/pages/login/login');
});

test('mini program does not create an alipay order it cannot pay', async () => {
  let calls = 0, message = '';
  api.post = async () => { calls++; return { data: { outTradeNo: 'ALI-1' } }; };
  wx.showToast = value => { message = value.title; };
  const p = page('utility'); p.data.room = { id: 1 }; p.data.payChannel = 'ALIPAY';
  await p.handleRecharge();
  assert.equal(calls, 0);
  assert.match(message, /H5/);
});

test('mini program lets students select an available recommended bed', async () => {
  api.get = async path => {
    if (path.includes('student-dashboard')) return { data: { user: { gender: 1 }, room: { id: 1 }, building: { id: 4 }, bed: { bedNo: 1 }, recommendedRooms: [{ buildingId: 4, roomId: 2, recommendedBedNo: 4, predictedHarmony: 90 }] } };
    if (path === '/dorm/buildings') return { data: [{ id: 4, genderType: 1, buildingName: '一号楼' }] };
    return { data: [{ id: 2, roomNo: '302', beds: [{ status: 0, bedNo: 4 }] }] };
  };
  const p = page('adjust-apply'); await p.loadCurrent();
  assert.equal(p.data.recommendedRooms.length, 1);
  await p.selectRecommendation({ currentTarget: { dataset: { index: 0 } } });
  assert.equal(p.data.rooms[p.data.roomIndex].id, 2);
  assert.equal(p.data.freeBeds[p.data.bedIndex].bedNo, 4);
});

test('mini program disables simulated recharge when backend demo mode is off', async () => {
  api.get = async path => {
    assert.equal(path, '/utility/pay/config');
    return { data: { demoSupported: false } };
  };
  let calls = 0;
  api.post = async () => { calls++; };
  const p = page('utility'); p.data.room = { id: 1 };
  await p.loadPaymentConfig();
  await p.handleRecharge();
  assert.equal(p.data.demoSupported, false);
  assert.equal(calls, 0);
});
