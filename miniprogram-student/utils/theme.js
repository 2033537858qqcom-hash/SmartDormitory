const KEY = 'dorm_theme';

function current() {
  try { return wx.getStorageSync(KEY) === 'dark' ? 'dark' : 'light'; } catch (_) { return 'light'; }
}

function sync(page, value) {
  const theme = value || current();
  if (page && page.setData) page.setData({ theme });
  const bar = page && page.getTabBar && page.getTabBar();
  if (bar && bar.setData) bar.setData({ theme });
  if (typeof wx.setNavigationBarColor === 'function') {
    wx.setNavigationBarColor({ frontColor: theme === 'dark' ? '#ffffff' : '#000000', backgroundColor: theme === 'dark' ? '#141a24' : '#f5f5f7' });
  }
  if (typeof wx.setBackgroundColor === 'function') wx.setBackgroundColor({ backgroundColor: theme === 'dark' ? '#10151d' : '#f5f5f7' });
  return theme;
}

function toggle(page) {
  const next = (page.data.theme || current()) === 'dark' ? 'light' : 'dark';
  try { wx.setStorageSync(KEY, next); } catch (_) { /* keep the current page usable */ }
  sync(page, next);
}

function withTheme(definition) {
  const onShow = definition.onShow;
  definition.data = { theme: 'light', ...definition.data };
  definition.onShow = function (...args) {
    sync(this);
    if (onShow) return onShow.apply(this, args);
  };
  definition.toggleTheme = function () { toggle(this); };
  return definition;
}

module.exports = { current, sync, toggle, withTheme };
