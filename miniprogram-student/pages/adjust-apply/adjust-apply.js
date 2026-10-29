const api = require('../../utils/api.js');
const authNavigation = require('../../utils/auth-navigation.js');
const app = getApp();

Page({
  data: {
    currentBuilding: null,
    currentRoom: null,
    currentBed: null,
    recommendedRooms: [],
    applyType: 'ADJUST',

    buildings: [],
    buildingNames: [],
    buildingIndex: 0,

    rooms: [],
    roomsLoading: false,
    roomsError: false,
    roomNos: [],
    roomIndex: 0,

    freeBeds: [],
    bedOptions: [],
    bedIndex: 0,

    reason: '',
    submitting: false,
    loading: true,
    loadError: false
  },

  onShow() {
    if (!app.isLoggedIn()) {
      authNavigation.goToLogin();
      return;
    }

    this.loadCurrent();
  },

  async loadCurrent() {
    this.setData({ loading: true, loadError: false });
    try {
      const res = await api.get(`/auth/student-dashboard/${app.globalData.studentId}`);
      const dash = res.data;
      app.globalData.dashData = dash;
      this.setData({ currentBuilding: dash.building, currentRoom: dash.room, currentBed: dash.bed, recommendedRooms: dash.recommendedRooms || [] });
      await this.loadBuildings();
    } catch (_) { this.setData({ loadError: true }); }
    finally { this.setData({ loading: false }); }
  },

  setApplyType(e) {
    this.setData({ applyType: e.currentTarget.dataset.type });
  },

  async loadBuildings() {
      const res = await api.get('/dorm/buildings');
      if (res && res.data) {
        const gender = app.globalData.dashData?.user?.gender;
        const buildings = res.data.filter(b => !gender || b.genderType === gender);
        const buildingNames = buildings.map(b => b.buildingName);
        this.setData({
          buildings,
          buildingNames,
          buildingIndex: 0
        });

        if (buildings.length > 0) {
          await this.loadRooms(buildings[0].id);
        }
      }
  },

  async loadRooms(buildingId) {
    const requestId = this._roomLoadId = (this._roomLoadId || 0) + 1;
    this.setData({ rooms: [], roomNos: [], freeBeds: [], bedOptions: [], roomsLoading: true, roomsError: false });
    try {
      const res = await api.get(`/dorm/rooms?buildingId=${buildingId}`);
      if (requestId !== this._roomLoadId) return;
      if (res && res.data) {
        const rooms = res.data.filter(r => r.id !== this.data.currentRoom?.id && (r.beds || []).some(b => b.status === 0 && !b.studentId));
        const roomNos = rooms.map(r => `${r.roomNo}室 (${r.occupiedCount || 0}/${r.capacity || 4}人)`);
        this.setData({
          rooms,
          roomNos,
          roomIndex: 0
        });
        this.updateFreeBeds();
      }
    } catch (_) { if (requestId === this._roomLoadId) this.setData({ roomsError: true }); }
    finally { if (requestId === this._roomLoadId) this.setData({ roomsLoading: false }); }
  },

  async selectRecommendation(e) {
    const recommendation = this.data.recommendedRooms[Number(e.currentTarget.dataset.index)];
    if (!recommendation) return;
    const buildingIndex = this.data.buildings.findIndex(b => b.id === recommendation.buildingId);
    if (buildingIndex < 0) { wx.showToast({ title: '推荐楼栋不可用', icon: 'none' }); return; }
    this.setData({ buildingIndex });
    await this.loadRooms(recommendation.buildingId);
    if (this.data.roomsError) { wx.showToast({ title: '房间加载失败，请重试', icon: 'none' }); return; }
    const roomIndex = this.data.rooms.findIndex(r => r.id === recommendation.roomId);
    if (roomIndex < 0) { wx.showToast({ title: '推荐床位已不可用', icon: 'none' }); return; }
    this.setData({ roomIndex });
    this.updateFreeBeds();
    const bedIndex = this.data.freeBeds.findIndex(b => b.bedNo === recommendation.recommendedBedNo);
    if (bedIndex < 0) { wx.showToast({ title: '推荐床位已不可用', icon: 'none' }); return; }
    this.setData({ bedIndex });
    wx.showToast({ title: '已选择推荐床位', icon: 'success' });
  },

  onBuildingChange(e) {
    const idx = Number(e.detail.value);
    this.setData({ buildingIndex: idx });
    const b = this.data.buildings[idx];
    if (b) {
      this.loadRooms(b.id);
    }
  },

  onRoomChange(e) {
    this.setData({ roomIndex: Number(e.detail.value) });
    this.updateFreeBeds();
  },

  updateFreeBeds() {
    const room = this.data.rooms[this.data.roomIndex];
    const freeBeds = (room?.beds || []).filter(b => b.status === 0 && !b.studentId);
    this.setData({ freeBeds, bedOptions: freeBeds.map(b => `${b.bedNo} 号床`), bedIndex: 0 });
  },

  onBedChange(e) {
    this.setData({ bedIndex: Number(e.detail.value) });
  },

  onInputReason(e) {
    this.setData({ reason: e.detail.value });
  },

  async handleSubmit() {
    if (this.data.submitting) return;
    const { applyType, reason, buildings, buildingIndex, rooms, roomIndex, bedIndex, freeBeds } = this.data;
    if (!reason.trim()) {
      wx.showToast({ title: '请详细填写申请理由', icon: 'none' });
      return;
    }

    let payload = {
      applyType,
      reason: reason.trim()
    };

    if (applyType === 'ADJUST') {
      const b = buildings[buildingIndex];
      const r = rooms[roomIndex];
      const bed = freeBeds[bedIndex];
      if (!b || !r || !bed) {
        wx.showToast({ title: '请选择有空床的目标寝室', icon: 'none' });
        return;
      }
      payload.targetBuildingId = b.id;
      payload.targetRoomId = r.id;
      payload.targetBedNo = bed.bedNo;
    }

    this.setData({ submitting: true });

    try {
      await api.post('/affair/adjust-apply', payload);
      wx.showToast({ title: '申请提交成功，宿管审核中', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 1000);
    } catch (_) {}
    finally {
      this.setData({ submitting: false });
    }
  }
});
