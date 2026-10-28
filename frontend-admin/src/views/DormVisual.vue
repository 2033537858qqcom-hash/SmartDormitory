<template>
  <div v-loading="loading" class="page-stack">
    <div v-if="loadError" class="error-banner" role="alert">
      <span>房态数据加载失败，请重试。</span>
      <el-button type="danger" plain size="small" @click="initPage">重试</el-button>
    </div>

    <!-- 顶部楼栋筛选与新增按钮 -->
    <el-card shadow="never" class="surface-card">
      <div class="section-header">
        <div class="toolbar-actions">
          <span class="filter-label">楼栋筛选：</span>
          <el-radio-group v-model="activeBuildingId" size="small" @change="loadRooms">
            <el-radio-button :label="0">全部楼栋</el-radio-button>
            <el-radio-button v-for="b in buildings" :key="b.id" :label="b.id">
              {{ b.buildingName }} ({{ b.genderType === 1 ? '男' : '女' }}){{ b.managerId === currentUser.id ? ' · 我管辖' : '' }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="toolbar-actions">
          <el-button v-if="currentUser.role === 'ADMIN'" size="small" @click="openManagerBind">楼栋宿管绑定</el-button>
          <el-button v-if="canWrite" type="primary" size="small" icon="Plus" @click="roomDialogVisible = true">新增房间</el-button>
          <el-tag v-if="!canWrite" type="info" size="small" effect="plain">辅导员只读视图</el-tag>
        </div>
      </div>
    </el-card>

    <!-- 响应式房态卡片矩阵 -->
    <p v-if="!loading && !loadError && !rooms.length" class="list-empty surface-card">当前楼栋暂无房间</p>
    <el-row v-else :gutter="16">
      <el-col
        v-for="room in rooms"
        :key="room.id"
        :xs="24"
        :sm="12"
        :md="12"
        :lg="8"
        class="room-col"
      >
        <el-card shadow="never" class="surface-card full-card">
          <div class="section-header">
            <div class="toolbar-actions">
              <span class="room-no">{{ room.buildingName }} · {{ room.roomNo }}室</span>
              <el-tag size="small" :type="room.occupiedCount >= room.capacity ? 'success' : 'info'" class="tabular-nums">
                {{ room.occupiedCount }} / {{ room.capacity }} 人
              </el-tag>
            </div>
            <div v-if="room.starLevel != null" class="stars" :aria-label="`文明星级 ${room.starLevel} 星`">
              {{ '★'.repeat(Math.max(0, Math.min(5, Number(room.starLevel) || 0))) }}
            </div>
            <span v-else class="unrated">暂无星级</span>
          </div>

          <div class="room-metrics tabular-nums">
            <span :class="{ 'low-bal': room.electricBalance != null && room.electricBalance < 30 }">电费: <b>{{ room.electricBalance == null ? '暂无数据' : `¥${Number(room.electricBalance).toFixed(2)}` }}</b></span>
            <span :class="{ 'low-bal': room.waterBalance != null && room.waterBalance < 15 }">水费: <b>{{ room.waterBalance == null ? '暂无数据' : `¥${Number(room.waterBalance).toFixed(2)}` }}</b></span>
            <span>契合度: <b>{{ room.harmonyScore == null ? '暂无数据' : `${room.harmonyScore}%` }}</b></span>
          </div>

          <div class="bed-grid">
            <button
              v-for="bed in room.beds"
              :key="bed.id"
              type="button"
              class="bed-box interactive-lift"
              :class="{ occupied: bed.status === 1 }"
              :aria-label="`${room.buildingName} ${room.roomNo}室 ${bed.bedNo}号床，${bed.status === 1 ? `已入住 ${bed.studentName}` : '空床，点击分配'}`"
              @click="handleBedClick(room, bed)"
            >
              <div class="bed-num tabular-nums">{{ bed.bedNo }}号床</div>
              <div v-if="bed.status === 1" class="stu-info">
                <div class="stu-name">{{ bed.studentName }}</div>
                <div class="stu-class tabular-nums">{{ bed.className || bed.studentNo }}</div>
              </div>
              <div v-else class="empty-tip">{{ canWrite ? '+ 空床（点击分配）' : '空床' }}</div>
            </button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 床位手动分配/退宿弹窗 -->
    <el-dialog
      v-model="bedDialogVisible"
      :title="`床位办理：${currentRoom?.buildingName} ${currentRoom?.roomNo}室 - ${currentBed?.bedNo}号床`"
      width="420px"
    >
      <div v-if="currentBed?.status === 1" class="checkout-panel">
        <p class="checkout-info">当前入住学生：<b>{{ currentBed.studentName }} ({{ currentBed.studentNo }})</b></p>
        <el-alert title="退宿将释放该床位并同步重算寝室六维契合度。" type="warning" :closable="false" />
      </div>
      <div v-else>
        <el-form label-position="top">
          <el-form-item label="选择待入住学生">
            <el-select v-model="selectedStudentId" placeholder="请选择未分寝学生" class="full-width" filterable>
              <el-option
                v-for="s in unallocatedStudents"
                :key="s.studentId"
                :label="`${s.realName} (${s.studentNo} · ${s.className})`"
                :value="s.studentId"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="bedDialogVisible = false">取消</el-button>
        <template v-if="canWrite">
          <el-button v-if="currentBed?.status === 1" type="danger" :loading="submitting" @click="confirmReleaseBed">
            办理退宿
          </el-button>
          <el-button v-else type="primary" :loading="submitting" @click="submitBedAssign(selectedStudentId)">
            确认入住
          </el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 新增房间弹窗 -->
    <el-dialog v-model="roomDialogVisible" title="新增宿舍房间" width="440px">
      <el-form :model="newRoom" label-width="90px">
        <el-form-item label="所属楼栋">
          <el-select v-model="newRoom.buildingId" class="full-width">
            <el-option v-for="b in buildings" :key="b.id" :label="b.buildingName" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在楼层">
          <el-input-number v-model="newRoom.floorNo" :min="1" :max="12" />
        </el-form-item>
        <el-form-item label="房间号">
          <el-input v-model="newRoom.roomNo" placeholder="如 305" />
        </el-form-item>
        <el-form-item label="床位容量">
          <el-radio-group v-model="newRoom.capacity">
            <el-radio :label="4">四人间</el-radio>
            <el-radio :label="6">六人间</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createRoom">确认创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="managerBindVisible" title="楼栋 ↔ 宿管绑定" width="520px">
      <p style="margin:0 0 12px;color:#666;font-size:13px;">为每栋楼指定 managerId，宿管登录后仅能操作绑定楼栋数据。</p>
      <el-table :data="buildings" size="small">
        <el-table-column prop="buildingName" label="楼栋" min-width="120" />
        <el-table-column label="性别" width="80">
          <template #default="{ row }">{{ row.genderType === 1 ? '男生' : '女生' }}</template>
        </el-table-column>
        <el-table-column label="当前宿管" min-width="120">
          <template #default="{ row }">{{ row.managerName || '未指派' }}</template>
        </el-table-column>
        <el-table-column label="指定宿管" min-width="160">
          <template #default="{ row }">
            <el-select v-model="row._managerId" clearable placeholder="选择宿管" size="small" style="width: 140px">
              <el-option v-for="m in dormManagers" :key="m.id" :label="m.realName" :value="m.id" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="managerBindVisible = false">关闭</el-button>
        <el-button type="primary" :loading="submitting" @click="saveManagerBinds">保存绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const currentUser = ref(JSON.parse(localStorage.getItem('dorm_admin_user') || '{}'))
const canWrite = computed(() => currentUser.value.role !== 'COUNSELOR')
const loading = ref(false)
const loadError = ref(false)
const submitting = ref(false)
const buildings = ref([])
const activeBuildingId = ref(0)
const rooms = ref([])
const unallocatedStudents = ref([])

const bedDialogVisible = ref(false)
const currentRoom = ref(null)
const currentBed = ref(null)
const selectedStudentId = ref(null)

const managerBindVisible = ref(false)
const dormManagers = ref([])
const roomDialogVisible = ref(false)
const newRoom = reactive({
  buildingId: 1,
  floorNo: 3,
  roomNo: '305',
  capacity: 4
})


const openManagerBind = async () => {
  const res = await request.get('/auth/users', { params: { role: 'DORM_MANAGER' } })
  dormManagers.value = res.data || []
  buildings.value = (buildings.value || []).map(b => ({ ...b, _managerId: b.managerId }))
  managerBindVisible.value = true
}

const saveManagerBinds = async () => {
  submitting.value = true
  try {
    for (const b of buildings.value) {
      if (b._managerId === b.managerId) continue
      await request.post('/dorm/building', {
        id: b.id,
        buildingCode: b.buildingCode,
        buildingName: b.buildingName,
        genderType: b.genderType,
        totalFloors: b.totalFloors,
        managerId: b._managerId || null,
        locationDesc: b.locationDesc,
        remark: b.remark
      })
    }
    ElMessage.success('楼栋宿管绑定已更新')
    managerBindVisible.value = false
    await loadBuildings()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const loadBuildings = async () => {
  const res = await request.get('/dorm/buildings')
  buildings.value = res.data || []
  if (currentUser.value.role === 'DORM_MANAGER') {
    const mine = buildings.value.find(b => b.managerId === currentUser.value.id)
    if (mine) {
      activeBuildingId.value = mine.id
    }
  }
}

const loadRooms = async () => {
  loading.value = true
  loadError.value = false
  try {
    const params = activeBuildingId.value ? { buildingId: activeBuildingId.value } : {}
    const res = await request.get('/dorm/rooms', { params })
    rooms.value = res.data || []
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const initPage = async () => {
  await loadBuildings()
  await loadRooms()
}

const loadCandidates = async () => {
  const res = await request.get('/smart-alloc/candidates')
  unallocatedStudents.value = (res.data || []).filter(s => !s.allocated)
}

const handleBedClick = (room, bed) => {
  if (!canWrite.value) {
    if (bed.status === 1) {
      ElMessage.info(`${bed.studentName || '在住学生'}（${bed.studentNo || bed.className || '学号未知'}）· 辅导员只读`)
    }
    return
  }
  currentRoom.value = room
  currentBed.value = bed
  selectedStudentId.value = null
  loadCandidates()
  bedDialogVisible.value = true
}

const confirmReleaseBed = async () => {
  await ElMessageBox.confirm(
    `确认将学生【${currentBed.value?.studentName}】从 ${currentRoom.value?.roomNo}室-${currentBed.value?.bedNo}号床办理退宿吗？`,
    '退宿确认',
    {
      confirmButtonText: '确认退宿',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  await submitBedAssign(null)
}

const submitBedAssign = async (studentId) => {
  if (!currentBed.value?.status && !studentId) {
    ElMessage.warning('请选择待入住学生')
    return
  }
  submitting.value = true
  try {
    await request.post('/dorm/assign-bed', {
      bedId: currentBed.value.id,
      studentId
    })
    ElMessage.success('床位状态与寝室契合度已更新')
    bedDialogVisible.value = false
    await loadRooms()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '床位操作失败')
  } finally {
    submitting.value = false
  }
}

const createRoom = async () => {
  if (!newRoom.roomNo.trim()) {
    ElMessage.warning('请填写房间号')
    return
  }
  submitting.value = true
  try {
    await request.post('/dorm/room', newRoom)
    ElMessage.success('房间及床位已创建')
    roomDialogVisible.value = false
    await loadRooms()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  initPage()
})
</script>

<style scoped>
.filter-label {
  font-weight: 600;
  font-size: 13px;
  color: var(--text-secondary);
}
.room-col {
  margin-bottom: 16px;
}
.full-card {
  height: 100%;
}
.room-no {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.stars {
  color: var(--status-warning);
  font-size: 13px;
}
.unrated { color: var(--text-muted); font-size: 12px; }
.room-metrics {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 12px;
  color: var(--text-muted);
  padding: 0 0 16px;
  margin: 15px 0 0;
  border-bottom: 1px solid var(--border-subtle);
}
.room-metrics .low-bal b {
  color: var(--status-danger);
}
.bed-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-top: 16px;
}
.bed-box {
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  padding: 13px 14px;
  cursor: pointer;
  background: var(--bg-canvas);
  text-align: left;
  color: var(--text-primary);
  min-width: 0;
}
.bed-box:hover {
  background: #ededf0;
}
.bed-box.occupied {
  background: #f2f4f7;
}
.bed-num {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
}
.stu-name {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 14px;
}
.stu-class {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}
.empty-tip {
  font-size: 12px;
  color: var(--text-muted);
  padding: 8px 0;
}
.checkout-info {
  margin-bottom: 12px;
}
.full-width {
  width: 100%;
}
</style>
