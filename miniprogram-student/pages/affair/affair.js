const api = require('../../utils/api.js');
const util = require('../../utils/util.js');
const tabbar = require('../../utils/tabbar.js');
const authNavigation = require('../../utils/auth-navigation.js');
const app = getApp();

Page(require('../../utils/theme').withTheme({
  data: {
    activeTab: 'overview',
    dash: null,
    loading: true,
    loadError: false,
    starIcons: '★★★★★',
    harmonyScoreText: '--%',
    reportReasons: [
      '科研实验 / 毕业课题攻关',
      '图书馆 / 考研期末晚自习',
      '社团活动 / 学科竞赛集训',
      '勤工助学 / 晚班兼职返校',
      '突发急事 / 校医院就医'
    ],
    reasonIndex: 0,
    expectedTime: '23:30',
    reportRemark: '',
    reporting: false,

    disciplineList: [],
    adjustList: [],
    visitorList: [],

    // 申诉弹窗
    appealModalVisible: false,
    appealSheetClosing: false,
    curDiscRecord: null,
    appealReason: '',
    submittingAppeal: false
  },

  onShow() {
    if (!app.isLoggedIn()) {
      authNavigation.goToLogin();
      return;
    }
    tabbar.update(this, { selected: 3, visible: !this.data.appealModalVisible });
    this.loadAffairData();
  },

  onPullDownRefresh() {
    this.loadAffairData(() => wx.stopPullDownRefresh());
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ activeTab: tab });
  },

  onReasonChange(e) {
    this.setData({ reasonIndex: Number(e.detail.value) });
  },

  onTimeChange(e) {
    this.setData({ expectedTime: e.detail.value });
  },

  onInputRemark(e) {
    this.setData({ reportRemark: e.detail.value });
  },

  async loadAffairData(callback) {
    const studentId = app.globalData.studentId;
    if (!studentId) return;

    this.setData({ loading: true, loadError: false });
    try {
      const res = await api.get(`/auth/student-dashboard/${studentId}`);
      if (res && res.data) {
        const dash = res.data;
        app.globalData.dashData = dash;

        const starCount = Math.max(0, Math.min(5, Number(dash.room?.starLevel) || 0));
        const starIcons = '★'.repeat(starCount) + '☆'.repeat(5 - starCount);

        const harmonyScoreText = dash.room?.harmonyScore != null ? `${dash.room.harmonyScore}%` : '--%';

        // 违纪记录列表格式化
        const disciplineList = (dash.myDisciplines || []).map(d => {
          let typeName = '晚归未报备';
          let typeClass = 'badge-red';
          if ((d.description || '').includes('[学生报备]')) {
            typeName = '晚归报备';
            typeClass = 'badge-blue';
          } else if (d.recordType === 'HYGIENE') {
            typeName = '内务不达标';
            typeClass = 'badge-amber';
          } else if (d.recordType === 'ILLEGAL_APPLIANCE') {
            typeName = '大功率用电违纪';
            typeClass = 'badge-red';
          }
          return {
            ...d,
            typeName,
            typeClass,
            recordTimeText: util.formatTime(d.occurTime || d.createTime)
          };
        });

        // 调宿记录列表格式化
        const adjustList = (dash.myAdjusts || []).map(a => {
          let statusText = '审核中';
          let statusClass = 'badge-amber';
          if (a.status === 'APPROVED') {
            statusText = '已同意';
            statusClass = 'badge-green';
          } else if (a.status === 'REJECTED') {
            statusText = '已驳回';
            statusClass = 'badge-red';
          }
          return {
            ...a,
            statusText,
            statusClass,
            createTimeText: util.formatTime(a.createTime)
          };
        });

        // 访客记录格式化
        const visitorList = (dash.myVisitors || []).map(v => {
          let statusText = '在访中';
          let statusClass = 'badge-green';
          if (v.status === 1) {
            statusText = '已离楼';
            statusClass = 'badge-gray';
          }
          return {
            ...v,
            statusText,
            statusClass,
            visitDateText: util.formatTime(v.enterTime)
          };
        });

        this.setData({
          dash,
          starIcons,
          harmonyScoreText,
          disciplineList,
          adjustList,
          visitorList
        });
      }
    } catch (_) { this.setData({ loadError: true }); }
    finally {
      this.setData({ loading: false });
      if (typeof callback === 'function') callback();
    }
  },

  async handleSubmitReport() {
    if (this.data.reporting) return;
    const { reportReasons, reasonIndex, expectedTime, reportRemark } = this.data;
    const reason = `${reportReasons[reasonIndex]}${reportRemark ? '（' + reportRemark + '）' : ''}`;

    this.setData({ reporting: true });
    try {
      await api.post('/affair/late-report', {
        reason,
        expectedTime
      });
      wx.showToast({ title: '晚归报备已提交', icon: 'success' });
      this.setData({ reportRemark: '' });
      this.loadAffairData();
    } catch (_) {}
    finally {
      this.setData({ reporting: false });
    }
  },

  openAppealModal(e) {
    const record = e.currentTarget.dataset.record;
    tabbar.update(this, { visible: false });
    this.setData({
      appealModalVisible: true,
      appealSheetClosing: false,
      curDiscRecord: record,
      appealReason: ''
    });
  },

  closeAppealModal() {
    if (this.data.appealSheetClosing) return;
    this.setData({ appealSheetClosing: true });
    this._appealSheetTimer = setTimeout(() => { this.setData({ appealModalVisible: false, appealSheetClosing: false, curDiscRecord: null }); tabbar.update(this, { visible: true }); }, 220);
  },

  stopPropagation() {},
  onUnload() { clearTimeout(this._appealSheetTimer); },

  onInputAppealReason(e) {
    this.setData({ appealReason: e.detail.value });
  },

  async submitAppeal() {
    if (this.data.submittingAppeal || !this.data.curDiscRecord) return;
    const { curDiscRecord, appealReason } = this.data;
    if (!appealReason.trim()) {
      wx.showToast({ title: '请详细阐述申诉事由', icon: 'none' });
      return;
    }

    this.setData({ submittingAppeal: true });
    try {
      await api.post('/affair/discipline-appeal', {
        id: curDiscRecord.id,
        appealReason: appealReason.trim(),
        appealProofType: 'OTHER'
      });
      wx.showToast({ title: '申诉已提交，宿管审核中', icon: 'success' });
      this.closeAppealModal();
      this.loadAffairData();
    } catch (_) {}
    finally {
      this.setData({ submittingAppeal: false });
    }
  },

  navToVisitor() {
    wx.navigateTo({ url: '/pages/visitor-pass/visitor-pass' });
  },

  navToAdjust() {
    wx.navigateTo({ url: '/pages/adjust-apply/adjust-apply' });
  },

  navToResidence() {
    wx.navigateTo({ url: '/pages/residence/residence' });
  }
}));
