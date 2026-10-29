const api = require('../../utils/api.js');
const util = require('../../utils/util.js');
const authNavigation = require('../../utils/auth-navigation.js');
const app = getApp();

const DIMENSIONS = [
  { key: 'scheduleScore', dim: 'schedule', title: '作息时间' },
  { key: 'sleepDepthScore', dim: 'sleep', title: '睡眠浅度' },
  { key: 'acTempScore', dim: 'ac', title: '空调偏好' },
  { key: 'cleanlinessScore', dim: 'hygiene', title: '卫生标准' },
  { key: 'atmosphereScore', dim: 'atmosphere', title: '寝室氛围' },
  { key: 'odorSensitiveScore', dim: 'smell', title: '异味敏感' }
];

Page(require('../../utils/theme').withTheme({
  data: {
    form: {
      scheduleScore: 2,
      sleepDepthScore: 3,
      acTempScore: 3,
      cleanlinessScore: 3,
      atmosphereScore: 2,
      odorSensitiveScore: 5,
      hobbyTags: ''
    },
    dimensionList: [],
    presetTags: ['考研自习', '规律早睡', '极客编程', '健身运动', '开黑社交', '摄影剪辑', '安静阅读', '音乐爱好者', '随和整洁', '电影追剧'],
    selectedTagsMap: {},
    customTagInput: '',
    saving: false,
    loading: true,
    loadError: false,
    hasRoomRadar: false,
    harmonyScoreText: ''
  },

  onShow() {
    if (!app.isLoggedIn()) {
      authNavigation.goToLogin();
      return;
    }
    this.initData();
  },

  onPullDownRefresh() {
    this.initData(() => wx.stopPullDownRefresh());
  },

  async initData(callback) {
    const studentId = app.globalData.studentId;
    this.setData({ loading: true, loadError: false });
    try {
      const res = await api.get(`/auth/student-dashboard/${studentId}`);
      if (res && res.data) {
        const dash = res.data;
        app.globalData.dashData = dash;

        const hp = dash.habitProfile || {};
        const form = {
          scheduleScore: hp.scheduleScore || 2,
          sleepDepthScore: hp.sleepDepthScore || 3,
          acTempScore: hp.acTempScore || 3,
          cleanlinessScore: hp.cleanlinessScore || 3,
          atmosphereScore: hp.atmosphereScore || 2,
          odorSensitiveScore: hp.odorSensitiveScore || 5,
          hobbyTags: hp.hobbyTags || ''
        };

        const tags = (form.hobbyTags || '').split(/[,，\s]+/).filter(Boolean);
        const map = {};
        tags.forEach(t => { map[t] = true; });

        let harmonyScoreText = '';
        if (dash.room && dash.room.harmonyScore != null) {
          harmonyScoreText = `当前室友生活契合度 ${dash.room.harmonyScore}%，可对照寝室均值调整你的习惯画像。`;
        } else if (!dash.room) {
          harmonyScoreText = '保存后，宿管中心将参考你的生活习惯安排寝室。';
        }

        this.setData({
          form,
          selectedTagsMap: map,
          hasRoomRadar: !!(dash.roomRadar?.roommates?.length),
          harmonyScoreText
        });

        this.updateDimensionList();
      }
    } catch (_) { this.setData({ loadError: true }); }
    this.setData({ loading: false }, () => { if (!this.data.loadError) this.renderRadar(); });
    if (typeof callback === 'function') callback();
  },

  updateDimensionList() {
    const list = DIMENSIONS.map(d => {
      const config = util.HABIT_CONFIG[d.dim];
      const val = this.data.form[d.key];
      const opt = config.options.find(o => o.value === Number(val)) || config.options[1];
      return {
        key: d.key,
        dim: d.dim,
        title: d.title,
        options: config.options,
        currentLabel: `${opt.label} (${val}分)`
      };
    });
    this.setData({ dimensionList: list });
  },

  onSelectDimension(e) {
    const { key, val } = e.currentTarget.dataset;
    const form = { ...this.data.form, [key]: Number(val) };
    this.setData({ form });
    this.updateDimensionList();
    this.renderRadar();
  },

  onSliderChange(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ [`form.${key}`]: Number(e.detail.value) });
    this.updateDimensionList();
    this.renderRadar();
  },

  toggleTag(e) {
    const tag = e.currentTarget.dataset.tag;
    const map = { ...this.data.selectedTagsMap };
    if (map[tag]) delete map[tag];
    else map[tag] = true;

    const tagsStr = Object.keys(map).join('，');
    this.setData({
      selectedTagsMap: map,
      'form.hobbyTags': tagsStr
    });
  },

  onInputCustomTag(e) {
    this.setData({ customTagInput: e.detail.value });
  },

  addCustomTag() {
    const tag = this.data.customTagInput.trim();
    if (!tag) return;
    const map = { ...this.data.selectedTagsMap, [tag]: true };
    const tagsStr = Object.keys(map).join('，');
    this.setData({
      selectedTagsMap: map,
      customTagInput: '',
      'form.hobbyTags': tagsStr
    });
  },

  async handleSaveProfile() {
    this.setData({ saving: true });
    try {
      await api.post('/smart-alloc/habit-profile', this.data.form);
      wx.showToast({ title: '生活习惯画像保存成功', icon: 'success' });
      const dash = app.globalData.dashData || {};
      dash.habitProfile = { ...(dash.habitProfile || {}), ...this.data.form };
      app.globalData.dashData = dash;
    } catch (_) {}
    finally {
      this.setData({ saving: false });
    }
  },

  renderRadar() {
    const query = wx.createSelectorQuery();
    query.select('#radarCanvas')
      .fields({ node: true, size: true })
      .exec((res) => {
        if (!res || !res[0] || !res[0].node) return;
        const canvas = res[0].node;
        const ctx = canvas.getContext('2d');
        const dpr = wx.getSystemInfoSync().pixelRatio || 2;
        const width = res[0].width;
        const height = res[0].height;

        canvas.width = width * dpr;
        canvas.height = height * dpr;
        ctx.scale(dpr, dpr);

        const centerX = width / 2;
        const centerY = height / 2;
        const radius = Math.min(centerX, centerY) - 34;

        ctx.clearRect(0, 0, width, height);

        const labels = ['作息', '睡眠', '空调', '卫生', '氛围', '异味'];
        const count = 6;
        const angleStep = (Math.PI * 2) / count;

        // 五级刻度与后端问卷保持一致
        for (let level = 1; level <= 5; level++) {
          const r = (radius / 5) * level;
          ctx.beginPath();
          for (let i = 0; i < count; i++) {
            const angle = i * angleStep - Math.PI / 2;
            const x = centerX + r * Math.cos(angle);
            const y = centerY + r * Math.sin(angle);
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
          }
          ctx.closePath();
          ctx.strokeStyle = this.data.theme === 'dark' ? (level === 5 ? '#627487' : '#3c4a59') : (level === 5 ? '#d2d2d7' : '#e8e8ed');
          ctx.lineWidth = 1;
          ctx.stroke();
        }

        // 2. 绘制轴线与文字标签
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';

        for (let i = 0; i < count; i++) {
          const angle = i * angleStep - Math.PI / 2;
          const x = centerX + radius * Math.cos(angle);
          const y = centerY + radius * Math.sin(angle);

          ctx.beginPath();
          ctx.moveTo(centerX, centerY);
          ctx.lineTo(x, y);
          ctx.strokeStyle = this.data.theme === 'dark' ? '#3c4a59' : '#E2E8F0';
          ctx.lineWidth = 1;
          ctx.stroke();

          // 标签文字
          const labelDist = radius + 18;
          const lx = centerX + labelDist * Math.cos(angle);
          const ly = centerY + labelDist * Math.sin(angle);
          ctx.fillStyle = this.data.theme === 'dark' ? '#c5cfdb' : '#475569';
          ctx.fillText(labels[i], lx, ly);
        }

        // 3. 绘制室友平均画像 (若存在)
        const dash = app.globalData.dashData;
        const mates = dash?.roomRadar?.roommates || [];
        if (mates.length) {
          const avgVec = Array.from({ length: 6 }, (_, i) => mates.reduce((sum, m) => sum + Number((m.vector || [3, 3, 3, 3, 3, 5])[i]), 0) / mates.length);
          ctx.beginPath();
          for (let i = 0; i < count; i++) {
            const val = Math.min(5, Math.max(1, avgVec[i]));
            const r = (radius / 5) * val;
            const angle = i * angleStep - Math.PI / 2;
            const x = centerX + r * Math.cos(angle);
            const y = centerY + r * Math.sin(angle);
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
          }
          ctx.closePath();
          ctx.fillStyle = 'rgba(16, 185, 129, 0.15)';
          ctx.fill();
          ctx.strokeStyle = '#10B981';
          ctx.lineWidth = 2;
          ctx.stroke();
        }

        // 4. 绘制学生本人画像
        const form = this.data.form;
        const myScores = [
          form.scheduleScore || 2,
          form.sleepDepthScore || 3,
          form.acTempScore || 3,
          form.cleanlinessScore || 3,
          form.atmosphereScore || 2,
          form.odorSensitiveScore || 5
        ];

        ctx.beginPath();
        for (let i = 0; i < count; i++) {
          const val = Math.min(5, Math.max(1, myScores[i]));
          const r = (radius / 5) * val;
          const angle = i * angleStep - Math.PI / 2;
          const x = centerX + r * Math.cos(angle);
          const y = centerY + r * Math.sin(angle);
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.closePath();
        ctx.fillStyle = 'rgba(37, 99, 235, 0.25)';
        ctx.fill();
        ctx.strokeStyle = '#2563EB';
        ctx.lineWidth = 2.5;
        ctx.stroke();

        // 绘制顶点实心圆点
        for (let i = 0; i < count; i++) {
          const val = Math.min(5, Math.max(1, myScores[i]));
          const r = (radius / 5) * val;
          const angle = i * angleStep - Math.PI / 2;
          const x = centerX + r * Math.cos(angle);
          const y = centerY + r * Math.sin(angle);

          ctx.beginPath();
          ctx.arc(x, y, 3.5, 0, Math.PI * 2);
          ctx.fillStyle = '#2563EB';
          ctx.fill();
        }
      });
  }
}));
