Component({
  data: {
    selected: 0,
    visible: true,
    tabs: [
      { pagePath: '/pages/home/home', text: '首页', icon: 'home' },
      { pagePath: '/pages/repair/repair', text: '报修', icon: 'repair' },
      { pagePath: '/pages/utility/utility', text: '水电', icon: 'utility' },
      { pagePath: '/pages/affair/affair', text: '宿务', icon: 'affair' }
    ]
  },
  methods: {
    switchTab(e) {
      const index = Number(e.currentTarget.dataset.index);
      if (index === this.data.selected || !this.data.tabs[index]) return;
      wx.switchTab({ url: this.data.tabs[index].pagePath });
    }
  }
});
