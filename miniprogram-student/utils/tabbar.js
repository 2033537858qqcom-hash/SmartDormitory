function update(page, values) {
  const bar = page.getTabBar && page.getTabBar();
  if (bar) bar.setData(values);
}

module.exports = { update };
