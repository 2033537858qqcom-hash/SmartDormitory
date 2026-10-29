const test = require('node:test');
const assert = require('node:assert/strict');
const theme = require('../utils/theme');

test('theme choice persists and refreshes the page, navigation and tab bar', () => {
  let saved = '', navigation;
  global.wx = {
    getStorageSync: () => saved,
    setStorageSync: (_key, value) => { saved = value; },
    setNavigationBarColor: value => { navigation = value; },
    setBackgroundColor() {}
  };
  let tabTheme;
  const page = { data: {}, setData(values) { Object.assign(this.data, values); }, getTabBar: () => ({ setData: values => { tabTheme = values.theme; } }) };
  theme.sync(page);
  assert.equal(page.data.theme, 'light');
  theme.toggle(page);
  assert.equal(saved, 'dark');
  assert.equal(page.data.theme, 'dark');
  assert.equal(navigation.frontColor, '#ffffff');
  assert.equal(tabTheme, 'dark');
  const next = { data: {}, setData(values) { Object.assign(this.data, values); } };
  theme.sync(next);
  assert.equal(next.data.theme, 'dark');
});
