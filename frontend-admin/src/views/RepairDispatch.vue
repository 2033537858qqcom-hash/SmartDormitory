<template>
  <div v-loading="loading" class="page-stack">
    <div v-if="loadError" class="error-banner" role="alert">
      <span>工单列表加载失败，请重试。</span>
      <el-button type="danger" plain size="small" @click="loadOrders">重试</el-button>
    </div>

    <!-- 维修工程师专属工作台横幅（当以 REPAIRMAN 角色登录时自动凸显） -->
    <section v-if="currentUser.role === 'REPAIRMAN'" class="surface-card worker-workbench">
      <div class="wb-left">
        <span class="wb-badge">专职接单工作台</span>
        <h3>{{ currentUser.realName }} · {{ typeName(currentUser.repairSpecialty || 'ELECTRIC') }}班组</h3>
        <p>系统已按工种与最小待办负载为您智能派单，高危紧急工单已自动置顶。</p>
      </div>
      <div class="wb-metrics tabular-nums">
        <div class="wb-stat">
          <span>我的待办</span>
          <strong class="urgent-num">{{ myWorkerStats.pending }}</strong>
        </div>
        <div class="wb-stat">
          <span>累计完工</span>
          <strong>{{ myWorkerStats.completed }}</strong>
        </div>
        <div class="wb-stat">
          <span>服务满意度</span>
          <strong class="star-num">{{ myWorkerStats.avgRating }} ★</strong>
        </div>
      </div>
    </section>

    <section class="surface-card workspace-panel">
      <div class="workspace-heading">
        <div><h2>工单记录</h2><p>查看报修进度，及时回应需要帮助的同学。</p></div>
        <div class="workspace-actions">
          <el-checkbox v-if="currentUser.role === 'REPAIRMAN'" v-model="onlyMyOrders" border size="small">
            仅看指派给我的工单
          </el-checkbox>
          <el-popover placement="bottom-start" :width="340" trigger="click">
            <template #reference><el-button text size="small">派单规则</el-button></template>
            <div class="rule-popover-content"><h4>紧急识别与自动派单</h4><ul><li>标题或描述中出现高危关键词时，工单将标记为紧急。</li><li>系统根据故障分类和维修师傅的待办情况安排工单。</li></ul></div>
          </el-popover>
          <el-button v-if="currentUser.role !== 'REPAIRMAN'" type="primary" icon="Plus" @click="mockSubmitVisible = true">新增报修</el-button>
        </div>
      </div>
      <div class="workspace-filters" aria-label="筛选工单状态">
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button label="">全部 {{ statusCounts.total }}</el-radio-button>
          <el-radio-button label="PENDING_ALL">待处理 {{ statusCounts.pendingAll }}</el-radio-button>
          <el-radio-button label="ASSIGNED">待维修 {{ statusCounts.assigned }}</el-radio-button>
          <el-radio-button label="PROCESSING">维修中 {{ statusCounts.processing }}</el-radio-button>
          <el-radio-button label="COMPLETED">待评价 {{ statusCounts.completed }}</el-radio-button>
          <el-radio-button label="RATED">已完结 {{ statusCounts.rated }}</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="!loading && !orders.length" class="workspace-empty"><el-icon :size="22"><Document /></el-icon><p>暂无符合条件的报修记录</p><span>可以切换筛选条件，或新增报修工单。</span></div>
      <ul v-else class="record-list" aria-label="报修工单列表">
        <li v-for="row in orders" :key="row.id" class="record-row">
          <button type="button" class="record-main" @click="openDetail(row)">
            <span class="record-icon" :class="{ urgent: row.priority === 'HIGH' }"><el-icon><Tools /></el-icon></span>
            <span class="record-copy"><span class="record-title">{{ row.title }}<span v-if="row.priority === 'HIGH'" class="urgent-label">紧急</span></span><span class="record-meta">{{ row.buildingName }} · {{ row.roomNo }}室 <span>·</span> {{ row.studentName }}</span><span class="record-meta record-id">{{ row.orderNo }} <span>·</span> {{ typeName(row.repairType) }} <span>·</span> {{ formatDateTime(row.createTime) }}</span></span>
          </button>
          <div class="record-side"><span class="record-worker">{{ row.workerName || '待指派' }}</span><span class="record-state" :class="{ urgent: row.priority === 'HIGH', done: row.status === 'RATED' }">{{ statusLabel(row.status) }}</span></div>
          <div class="record-actions"><button type="button" class="text-action" :aria-label="`查看${row.title}详情`" @click="openDetail(row)">详情 <span aria-hidden="true">›</span></button><button v-if="row.status === 'ASSIGNED' || row.status === 'PROCESSING'" type="button" class="quiet-action" :aria-label="`处理${row.title}`" @click="openProcess(row)">处理</button></div>
        </li>
      </ul>
    </section>

    <!-- 右侧详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="报修工单详情"
      size="440px"
      destroy-on-close
    >
      <div v-if="currentOrder" class="drawer-body">
        <div class="drawer-status-bar">
          <div>
            <div class="order-no tabular-nums">工单号：{{ currentOrder.orderNo }}</div>
            <div class="order-time tabular-nums">报修时间：{{ formatDateTime(currentOrder.createTime) }}</div>
          </div>
          <div class="toolbar-actions">
            <el-tag v-if="currentOrder.priority === 'HIGH'" type="danger" effect="dark" size="small">高危紧急</el-tag>
            <el-tag :type="statusType(currentOrder.status)" size="small">{{ statusLabel(currentOrder.status) }}</el-tag>
          </div>
        </div>

        <div class="detail-section">
          <div class="sec-label">报修寝室与联系人</div>
          <div class="info-grid">
            <div><span class="k">所在寝室：</span><b>{{ currentOrder.buildingName }} · {{ currentOrder.roomNo }}室</b></div>
            <div><span class="k">报修学生：</span>{{ currentOrder.studentName }}</div>
            <div><span class="k">联系电话：</span><span class="tabular-nums">{{ currentOrder.studentPhone || '-' }}</span></div>
            <div><span class="k">故障类别：</span>{{ typeName(currentOrder.repairType) }}</div>
          </div>
        </div>

        <div class="detail-section">
          <div class="sec-label">故障标题与详细描述</div>
          <div class="detail-box">
            <div class="box-title">{{ currentOrder.title }}</div>
            <p class="box-desc body-pretty">{{ currentOrder.description || '无补充说明' }}</p>
          </div>
        </div>

        <div class="detail-section">
          <div class="sec-label">派单与调度记录</div>
          <div class="dispatch-box">
            <div>指派维修人：<b>{{ currentOrder.workerName || '待指派' }}</b></div>
            <div class="dispatch-reason">{{ currentOrder.dispatchRemark || '按工种自动指派' }}</div>
          </div>
        </div>

        <div class="detail-section">
          <div class="sec-label">维修结果与学生评价</div>
          <div class="detail-box">
            <div v-if="currentOrder.repairResult">
              <span class="k">处理结果：</span>{{ currentOrder.repairResult }}
            </div>
            <div v-else class="muted-placeholder">暂未填写维修结果</div>

            <div v-if="currentOrder.rating" class="rating-block">
              <div class="rating-stars">{{ '★'.repeat(currentOrder.rating) }} ({{ currentOrder.rating }}星)</div>
              <div class="rating-comment">{{ currentOrder.ratingComment || '学生未填写文字评价' }}</div>
            </div>
          </div>
        </div>

        <div
          v-if="currentOrder.status === 'ASSIGNED' || currentOrder.status === 'PROCESSING'"
          class="drawer-footer-actions"
        >
          <el-button type="primary" class="full-width" @click="openProcess(currentOrder)">
            更新维修进度 / 完工结单
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 维修处理弹窗 -->
    <el-dialog v-model="processVisible" title="更新工单进度" width="440px">
      <el-form :model="processForm" label-width="88px">
        <el-form-item label="当前进度">
          <el-radio-group v-model="processForm.status">
            <el-radio label="PROCESSING">维修中</el-radio>
            <el-radio label="COMPLETED">已修复完工</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input
            v-model="processForm.repairResult"
            type="textarea"
            :rows="3"
            placeholder="请填写检修情况及更换配件说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitProcess">确认保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增/模拟报修弹窗 -->
    <el-dialog v-model="mockSubmitVisible" title="新增报修工单" width="480px">
      <el-form :model="mockForm" label-width="88px">
        <el-form-item label="故障分类">
          <el-select v-model="mockForm.repairType" class="full-width">
            <el-option label="电力照明" value="ELECTRIC" />
            <el-option label="水暖卫浴" value="WATER" />
            <el-option label="门窗床桌" value="WOOD" />
            <el-option label="网络空调" value="NETWORK" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障标题">
          <el-input v-model="mockForm.title" placeholder="简要描述故障，如：书桌插座跳闸" />
        </el-form-item>
        <el-form-item label="详细描述">
          <el-input v-model="mockForm.description" type="textarea" :rows="3" placeholder="请补充具体故障现象..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mockSubmitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMockRepair">提交并自动派单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const route = useRoute()
const currentUser = ref(JSON.parse(localStorage.getItem('dorm_admin_user') || '{}'))
const onlyMyOrders = ref(currentUser.value.role === 'REPAIRMAN')
const loading = ref(false)
const loadError = ref(false)
const submitting = ref(false)
const allOrdersList = ref([])
const statusFilter = ref(route.query.status || '')

const drawerVisible = ref(false)
const currentOrder = ref(null)

const processVisible = ref(false)
const processForm = reactive({
  id: null,
  status: 'COMPLETED',
  repairResult: '已现场检修并更换损坏部件，恢复正常使用'
})

const mockSubmitVisible = ref(false)
const mockForm = reactive({
  studentId: 10,
  buildingId: 1,
  roomId: 1,
  repairType: 'ELECTRIC',
  title: '书桌插座短路跳闸冒火花',
  description: '插上电源时突然跳闸，请求电工师傅排查'
})

const formatDateTime = (val) => {
  if (!val) return '今日'
  return String(val).replace('T', ' ').substring(0, 16)
}

const scopedOrders = computed(() => {
  if (onlyMyOrders.value && currentUser.value.id) {
    return allOrdersList.value.filter(o => o.assignedWorkerId === currentUser.value.id)
  }
  return allOrdersList.value
})

const myWorkerStats = computed(() => {
  const mine = allOrdersList.value.filter(o => o.assignedWorkerId === currentUser.value.id)
  const pending = mine.filter(o => ['ASSIGNED', 'PROCESSING'].includes(o.status)).length
  const completed = mine.filter(o => ['COMPLETED', 'RATED'].includes(o.status)).length
  const ratedList = mine.filter(o => o.rating != null)
  const avgRating = ratedList.length
    ? (ratedList.reduce((s, r) => s + r.rating, 0) / ratedList.length).toFixed(1)
    : '5.0'
  return { pending, completed, avgRating }
})

const statusCounts = computed(() => {
  const list = scopedOrders.value
  return {
    total: list.length,
    pendingAll: list.filter(o => ['PENDING', 'ASSIGNED', 'PROCESSING'].includes(o.status)).length,
    assigned: list.filter(o => o.status === 'ASSIGNED').length,
    processing: list.filter(o => o.status === 'PROCESSING').length,
    completed: list.filter(o => o.status === 'COMPLETED').length,
    rated: list.filter(o => o.status === 'RATED').length
  }
})

const orders = computed(() => {
  const list = scopedOrders.value
  if (!statusFilter.value) return list
  if (statusFilter.value === 'PENDING_ALL') {
    return list.filter(o => ['PENDING', 'ASSIGNED', 'PROCESSING'].includes(o.status))
  }
  return list.filter(o => o.status === statusFilter.value)
})

const typeName = (t) => {
  const m = { ELECTRIC: '电力照明', WATER: '水暖卫浴', WOOD: '门窗床桌', NETWORK: '网络空调' }
  return m[t] || t
}

const statusLabel = (s) => {
  const m = { PENDING: '待派单', ASSIGNED: '待维修', PROCESSING: '维修中', COMPLETED: '待评价', RATED: '已完结' }
  return m[s] || s
}

const statusType = (s) => {
  if (s === 'ASSIGNED') return 'warning'
  if (s === 'PROCESSING') return 'primary'
  if (s === 'COMPLETED' || s === 'RATED') return 'success'
  return 'info'
}

const loadOrders = async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await request.get('/repair/list')
    allOrdersList.value = res.data || []
    if (currentOrder.value) {
      const updated = allOrdersList.value.find(o => o.id === currentOrder.value.id)
      if (updated) currentOrder.value = updated
    }
    if (route.query.orderId) {
      const matched = allOrdersList.value.find(o => String(o.id) === String(route.query.orderId))
      if (matched) {
        openDetail(matched)
      }
    }
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const openDetail = (row) => {
  currentOrder.value = row
  drawerVisible.value = true
}

const openProcess = (row) => {
  processForm.id = row.id
  processForm.status = 'COMPLETED'
  processForm.repairResult = row.repairResult || '已现场检修完毕并测试正常'
  processVisible.value = true
}

const submitProcess = async () => {
  submitting.value = true
  try {
    await request.post('/repair/process', processForm)
    ElMessage.success('工单状态已更新')
    processVisible.value = false
    await loadOrders()
  } finally {
    submitting.value = false
  }
}

const submitMockRepair = async () => {
  if (!mockForm.title.trim()) {
    ElMessage.warning('请填写故障标题')
    return
  }
  submitting.value = true
  try {
    const res = await request.post('/repair/submit', mockForm)
    ElMessage.success(res.message || '报修已提交并完成派单')
    mockSubmitVisible.value = false
    await loadOrders()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.worker-workbench { padding: 22px 28px; display: flex; align-items: center; justify-content: space-between; gap: 24px; border-left: 4px solid var(--accent-primary); }
.wb-badge { display: inline-block; font-size: 11px; font-weight: 600; color: var(--accent-primary); background: var(--accent-subtle); padding: 2px 8px; border-radius: 6px; margin-bottom: 6px; }
.wb-left h3 { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.wb-left p { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
.wb-metrics { display: flex; gap: 28px; flex-shrink: 0; }
.wb-stat span { display: block; font-size: 12px; color: var(--text-muted); }
.wb-stat strong { display: block; font-size: 24px; font-weight: 600; margin-top: 4px; color: var(--text-primary); }
.wb-stat strong.urgent-num { color: var(--accent-primary); }
.wb-stat strong.star-num { color: var(--status-warning); }

.workspace-panel { padding: 28px 30px 18px; }
.workspace-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.workspace-heading h2 { font-size: 20px; font-weight: 600; letter-spacing: -.5px; }
.workspace-heading p { margin-top: 5px; font-size: 13px; color: var(--text-muted); }
.workspace-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.workspace-filters { margin: 23px 0 9px; }
.workspace-filters :deep(.el-radio-group) { max-width: 100%; }
.record-list { list-style: none; }
.record-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(120px, .27fr) auto; align-items: center; gap: 22px; padding: 19px 0; border-top: 1px solid var(--border-subtle); }
.record-main { min-width: 0; display: flex; align-items: flex-start; gap: 15px; border: 0; background: transparent; color: var(--text-primary); padding: 0; text-align: left; cursor: pointer; }
.record-main:hover .record-title { color: var(--accent-primary); }
.record-icon { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 13px; background: var(--bg-canvas); color: var(--text-secondary); flex: 0 0 auto; font-size: 18px; }
.record-icon.urgent { color: var(--status-danger); background: var(--status-danger-bg); }
.record-copy { min-width: 0; display: flex; flex-direction: column; gap: 5px; }
.record-title { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; font-size: 15px; line-height: 1.5; font-weight: 600; transition: color 160ms ease; overflow-wrap: anywhere; }
.urgent-label { color: var(--status-danger); background: var(--status-danger-bg); padding: 1px 7px; border-radius: 7px; font-size: 11px; font-weight: 500; }
.record-meta { color: var(--text-muted); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.record-meta span { padding: 0 3px; }
.record-side { display: flex; flex-direction: column; gap: 5px; align-items: flex-start; font-size: 12px; color: var(--text-secondary); overflow-wrap: anywhere; }
.record-state { color: var(--text-muted); }.record-state.urgent { color: var(--status-danger); }.record-state.done { color: var(--status-success); }
.record-actions { display: flex; gap: 7px; align-items: center; }
.text-action, .quiet-action { border: 0; background: transparent; color: var(--accent-primary); font-size: 13px; padding: 9px; white-space: nowrap; cursor: pointer; }
.text-action span { padding-left: 4px; }.quiet-action { color: var(--text-secondary); background: var(--bg-canvas); border-radius: 10px; padding: 8px 12px; }
.workspace-empty { padding: 44px 20px; text-align: center; color: var(--text-muted); }.workspace-empty p { margin: 8px 0 3px; font-size: 14px; color: var(--text-secondary); }.workspace-empty span { font-size: 12px; }
.drawer-body { display: flex; flex-direction: column; gap: 22px; }
.drawer-status-bar { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; padding-bottom: 20px; border-bottom: 1px solid var(--border-subtle); flex-wrap: wrap; }
.order-no { font-weight: 600; font-size: 15px; }.order-time { font-size: 12px; color: var(--text-muted); margin-top: 5px; }
.detail-section { display: flex; flex-direction: column; gap: 10px; }.sec-label { font-size: 12px; color: var(--text-muted); }
.info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; font-size: 13px; overflow-wrap: anywhere; }.k { color: var(--text-muted); }
.detail-box, .dispatch-box { font-size: 13px; line-height: 1.75; color: var(--text-secondary); }.box-title { font-weight: 600; color: var(--text-primary); margin-bottom: 5px; }.box-desc { line-height: 1.75; }
.dispatch-reason { margin-top: 5px; font-size: 12px; color: var(--text-muted); }.rating-block { margin-top: 10px; }.rating-stars { color: var(--status-warning); }.rating-comment { margin-top: 4px; }.muted-placeholder { color: var(--text-muted); }
.drawer-footer-actions { margin-top: 4px; }.full-width { width: 100%; }
@media (max-width: 900px) { .worker-workbench { flex-direction: column; align-items: flex-start; } .record-row { grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }.record-side { grid-column: 1; margin-left: 55px; flex-direction: row; flex-wrap: wrap; }.record-actions { grid-column: 2; grid-row: 1 / span 2; } }
@media (max-width: 600px) { .workspace-panel { padding: 22px 18px 15px; }.workspace-heading { flex-wrap: wrap; }.workspace-actions { width: 100%; justify-content: space-between; }.record-row { gap: 8px; }.record-icon { width: 36px; height: 36px; }.record-main { gap: 11px; }.record-side { margin-left: 47px; }.record-id { display: none; }.record-actions { flex-direction: column; gap: 2px; }.info-grid { grid-template-columns: 1fr; } }
</style>
