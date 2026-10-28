<template>
  <div ref="containerRef" v-loading="loading" class="page-stack overview-page">
    <div v-if="loadError" class="error-banner" role="alert"><span>概览暂时无法加载，请稍后重试。</span><el-button type="danger" plain size="small" @click="loadOverview">重新加载</el-button></div>
    <div class="overview-metrics">
      <button type="button" class="overview-metric" @click="goTo('/repair-dispatch', { status: 'PENDING_ALL' })"><span class="metric-label">待处理报修</span><span class="metric-number tabular-nums">{{ overview.pendingRepairs || 0 }}<small>单</small></span><span class="metric-note"><i v-if="overview.highRiskRepairs" class="urgent-dot"></i>{{ overview.highRiskRepairs ? `${overview.highRiskRepairs} 单紧急` : '暂无紧急工单' }}</span></button>
      <button type="button" class="overview-metric" @click="goTo('/civilized-affair', { tab: 'adjust' })"><span class="metric-label">待审调宿</span><span class="metric-number tabular-nums">{{ pendingAdjusts.length }}<small>项</small></span><span class="metric-note">等待宿管审批</span></button>
      <button type="button" class="overview-metric" @click="goTo('/smart-alloc', { filter: 'UNALLOCATED' })"><span class="metric-label">待分寝新生</span><span class="metric-number tabular-nums">{{ overview.unallocatedStudents || 0 }}<small>人</small></span><span class="metric-note">查看生活习惯与分配状态</span></button>
      <button type="button" class="overview-metric" @click="goTo('/dorm-visual')"><span class="metric-label">已入住床位</span><span class="metric-number tabular-nums">{{ overview.occupiedBeds || 0 }}<small>/ {{ overview.totalBeds || 0 }}</small></span><span class="metric-note">入住率 {{ overview.occupancyRate || 0 }}%</span></button>
    </div>
    <div class="work-grid">
      <section class="work-panel surface-card">
        <div class="panel-heading"><h2>待办事项</h2><span>{{ combinedTodoList.length }} 项待处理</span></div>
        <div class="todo-filters" aria-label="待办类别"><button v-for="filter in todoFilters" :key="filter.value" type="button" :aria-pressed="todoFilter === filter.value" @click="todoFilter = filter.value">{{ filter.label }}</button></div>
        <div v-if="!filteredTodoList.length" class="empty-todo">{{ combinedTodoList.length ? '这个分类暂时没有待办。' : '当前待办已处理完毕。' }}</div>
        <div v-for="row in filteredTodoList" :key="`${row.todoType}-${row.id}`" class="todo-row">
          <span class="todo-symbol" :class="{ urgent: row.priority === 'HIGH' }"><el-icon><Tools v-if="row.todoType === 'REPAIR'" /><Switch v-else /></el-icon></span>
          <div class="todo-content"><h3>{{ row.summary }}</h3><p>{{ row.location }}<span class="meta-separator">·</span>{{ row.statusText }}</p><span class="todo-assignee">{{ row.assignee }}</span></div>
          <button type="button" class="row-action" :aria-label="`查看${row.summary}`" @click="row.todoType === 'REPAIR' ? goTo('/repair-dispatch', { orderId: row.id }) : goTo('/civilized-affair', { tab: 'adjust' })">查看<el-icon><ArrowRight /></el-icon></button>
        </div>
      </section>
      <aside class="work-panel surface-card residence-overview">
        <div class="panel-heading"><h2>住宿概况</h2><el-button link type="primary" size="small" @click="goTo('/dorm-visual')">查看房态</el-button></div>
        <div class="occupancy-number tabular-nums">{{ overview.occupiedBeds || 0 }}<span>/ {{ overview.totalBeds || 0 }} 床已入住</span></div>
        <el-progress :percentage="Number(overview.occupancyRate || 0)" :show-text="false" :stroke-width="5" />
        <p class="occupancy-caption">入住率 {{ overview.occupancyRate || 0 }}% · {{ overview.unallocatedStudents || 0 }} 人待分寝</p>
        <div class="energy-heading"><h3>能耗提醒</h3><span>{{ (overview.abnormalAlerts || []).length }} 份异常账单</span></div>
        <p v-if="!(overview.abnormalAlerts || []).length" class="quiet-note">当前没有需要核查的异常账单。</p>
        <button v-for="alert in overview.abnormalAlerts || []" :key="`${alert.roomName}-${alert.billMonth}`" type="button" class="energy-alert" @click="goTo('/utility-manage', { abnormal: '1' })"><span class="alert-title"><i class="urgent-dot"></i>{{ alert.roomName }}<el-icon><ArrowRight /></el-icon></span><span class="alert-description">{{ alert.reason }}</span><span class="alert-meta tabular-nums">{{ alert.billMonth }} · {{ formatMoney(alert.totalCost) }}</span></button>
      </aside>
    </div>
    <!-- 第三层：图表区（横向楼栋条形图 + 报修分类数量展示 + 水电分单位独立趋势图） -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="12" :lg="8" class="col-gap">
        <el-card shadow="never" class="surface-card">
          <template #header>
            <div class="section-header">
              <span class="section-title">各楼栋床位入住分布</span>
              <span class="unit-note">单位：床</span>
            </div>
          </template>
          <div ref="buildingChartRef" class="echarts-box"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="8" class="col-gap">
        <el-card shadow="never" class="surface-card">
          <template #header>
            <div class="section-header">
              <span class="section-title">报修工单分类数量</span>
              <span class="unit-note">单位：单</span>
            </div>
          </template>
          <div ref="repairChartRef" class="echarts-box"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="24" :lg="8" class="col-gap">
        <el-card shadow="never" class="surface-card">
          <template #header>
            <div class="section-header">
              <span class="section-title">月度能耗趋势</span>
              <el-radio-group v-model="utilityChartMode" size="small" @change="renderUtilityChart">
                <el-radio-button label="ELECTRIC">用电 (度)</el-radio-button>
                <el-radio-button label="WATER">用水 (吨)</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="utilityChartRef" class="echarts-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第四层：文明寝室量化排名 -->
    <el-card shadow="never" class="surface-card">
      <template #header>
        <div class="section-header">
          <div class="section-title-group">
            <span class="section-title">文明寝室评分排名</span>
            <el-popover placement="right" :width="300" trigger="click">
              <template #reference>
                <el-button size="small" text type="primary">规则说明</el-button>
              </template>
              <div class="rule-popover-content">
                <h4>文明寝室评分规则</h4>
                <ul>
                  <li><b>卫生检查 (50%)</b>：每周四项内务检查均分</li>
                  <li><b>纪律表现 (30%)</b>：满分100分，按晚归或违规记录扣减</li>
                  <li><b>水电节能 (20%)</b>：无能耗异常得满分，触发异常扣减</li>
                </ul>
              </div>
            </el-popover>
          </div>
          <el-button link type="primary" size="small" @click="goTo('/civilized-affair', { tab: 'eval' })">
            查看完整榜单 →
          </el-button>
        </div>
      </template>
      <el-table :data="overview.roomRankings || []" size="small" stripe>
        <el-table-column type="index" label="排名" width="70" />
        <el-table-column prop="roomName" label="寝室号" min-width="150" />
        <el-table-column prop="harmonyScore" label="室友契合度" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="primary" effect="plain">{{ row.harmonyScore }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="civilizedScore" label="文明总分" width="130">
          <template #default="{ row }">
            <b class="score-text tabular-nums">{{ Number(row.civilizedScore || 0).toFixed(1) }} 分</b>
          </template>
        </el-table-column>
        <el-table-column prop="starLevel" label="星级评定" width="150">
          <template #default="{ row }">
            <span class="star-text">{{ '★'.repeat(row.starLevel || 4) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/api/request'

const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const overview = ref({})
const pendingRepairsList = ref([])
const pendingAdjusts = ref([])
const utilityChartMode = ref('ELECTRIC')
const todoFilter = ref('ALL')
const todoFilters = [{ label: '全部', value: 'ALL' }, { label: '报修', value: 'REPAIR' }, { label: '审批', value: 'ADJUST' }]
const filteredTodoList = computed(() => todoFilter.value === 'ALL' ? combinedTodoList.value : combinedTodoList.value.filter(item => item.todoType === todoFilter.value))

const containerRef = ref(null)
const buildingChartRef = ref(null)
const repairChartRef = ref(null)
const utilityChartRef = ref(null)

let chart1 = null
let chart2 = null
let chart3 = null
let resizeObserver = null

const formatMoney = (val) => {
  return `¥${Number(val || 0).toFixed(2)}`
}

const repairStatusLabel = (s) => {
  const m = { PENDING: '待派单', ASSIGNED: '待上门', PROCESSING: '维修中', COMPLETED: '待评价', RATED: '已完结' }
  return m[s] || s
}

const combinedTodoList = computed(() => {
  const repairItems = pendingRepairsList.value.map(r => ({
    id: r.id,
    todoType: 'REPAIR',
    priority: r.priority,
    status: r.status,
    statusText: repairStatusLabel(r.status),
    location: `${r.buildingName}-${r.roomNo}室 (${r.studentName})`,
    summary: r.title,
    assignee: r.workerName ? `${r.workerName} 师傅` : '待指派'
  }))
  const adjustItems = pendingAdjusts.value.map(a => ({
    id: a.id,
    todoType: 'ADJUST',
    priority: 'NORMAL',
    status: a.status,
    statusText: '待宿管审批',
    location: `${a.studentName} (${a.studentNo})`,
    summary: `申请调入 ${a.targetRoomName}`,
    assignee: '后勤中心'
  }))
  return [...repairItems, ...adjustItems]
})

const goTo = (path, query = {}) => {
  router.push({ path, query })
}

const loadOverview = async () => {
  loading.value = true
  loadError.value = false
  try {
    const [ovRes, repRes, adjRes] = await Promise.all([
      request.get('/dashboard/overview'),
      request.get('/repair/list'),
      request.get('/affair/adjust-list')
    ])
    overview.value = ovRes.data || {}
    pendingRepairsList.value = (repRes.data || []).filter(
      r => ['PENDING', 'ASSIGNED', 'PROCESSING'].includes(r.status)
    )
    pendingAdjusts.value = (adjRes.data || []).filter(a => a.status === 'PENDING')
    await nextTick()
    initCharts()
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const renderUtilityChart = () => {
  if (!utilityChartRef.value) return
  if (!chart3) {
    chart3 = echarts.init(utilityChartRef.value)
  }
  const isElec = utilityChartMode.value === 'ELECTRIC'
  const seriesData = isElec ? (overview.value.electricTrend || []) : (overview.value.waterTrend || [])
  const unit = isElec ? '度 (kWh)' : '吨 (m³)'
  const lineColor = isElec ? '#006edb' : '#687e9e'

  chart3.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.name}<br/>${p.marker}${p.seriesName}：<b>${p.value} ${isElec ? '度' : '吨'}</b>`
      }
    },
    grid: { top: 34, left: 48, right: 20, bottom: 28 },
    xAxis: {
      type: 'category',
      data: overview.value.months || []
    },
    yAxis: {
      type: 'value',
      name: `单位：${unit}`,
      nameTextStyle: { color: '#86868b', fontSize: 12, align: 'left' }
    },
    series: [
      {
        name: isElec ? '月度总用电量' : '月度总用水量',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: seriesData,
        label: {
          show: true,
          position: 'top',
          formatter: `{c}${isElec ? '度' : '吨'}`,
          fontSize: 11,
          color: '#6e6e73'
        },
        itemStyle: { color: lineColor },
        areaStyle: {
          color: isElec ? 'rgba(0, 110, 219, 0.06)' : 'rgba(13, 148, 136, 0.12)'
        }
      }
    ]
  }, true)
}

const initCharts = () => {
  // 1. 楼栋床位分布：横向条形图
  if (buildingChartRef.value) {
    if (chart1) chart1.dispose()
    chart1 = echarts.init(buildingChartRef.value)
    const stats = overview.value.buildingStats || []
    chart1.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
      grid: { top: 12, left: 10, right: 28, bottom: 32, containLabel: true },
      xAxis: { type: 'value', name: '床' },
      yAxis: {
        type: 'category',
        data: stats.map(s => s.buildingName),
        axisLabel: { color: '#6e6e73', fontSize: 12 }
      },
      series: [
        {
          name: '已入住',
          type: 'bar',
          stack: 'total',
          barWidth: 18,
          label: { show: true, position: 'inside', formatter: '{c}' },
          data: stats.map(s => s.occupied),
          itemStyle: { color: '#006edb', borderRadius: [4, 0, 0, 4] }
        },
        {
          name: '空余床位',
          type: 'bar',
          stack: 'total',
          barWidth: 18,
          label: { show: true, position: 'inside', color: '#6e6e73', formatter: '{c}' },
          data: stats.map(s => s.free),
          itemStyle: { color: '#e8e8ed', borderRadius: [0, 4, 4, 0] }
        }
      ]
    })
  }

  // 2. 报修分类：直接展示数量，防止标签截断
  if (repairChartRef.value) {
    if (chart2) chart2.dispose()
    chart2 = echarts.init(repairChartRef.value)
    const data = overview.value.repairTypeChart || []
    chart2.setOption({
      color: ['#006edb', '#0ea5e9', '#38bdf8', '#86868b'],
      tooltip: { trigger: 'item', formatter: '{b}：{c} 单 ({d}%)' },
      legend: {
        bottom: 0,
        itemWidth: 10,
        itemHeight: 10,
        formatter: (name) => {
          const found = data.find(d => d.name === name)
          return `${name} (${found ? found.value : 0}单)`
        }
      },
      series: [
        {
          name: '报修分类',
          type: 'pie',
          center: ['50%', '43%'],
          radius: ['36%', '60%'],
          itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
          label: {
            show: true,
            formatter: '{b}\n{c}单',
            fontSize: 12,
            color: '#6e6e73'
          },
          data
        }
      ]
    })
  }

  // 3. 水电趋势：按单位独立展示
  renderUtilityChart()
}

onMounted(() => {
  loadOverview()
  if (window.ResizeObserver && containerRef.value) {
    resizeObserver = new ResizeObserver(() => {
      chart1 && chart1.resize()
      chart2 && chart2.resize()
      chart3 && chart3.resize()
    })
    resizeObserver.observe(containerRef.value)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
  chart1 && chart1.dispose()
  chart2 && chart2.dispose()
  chart3 && chart3.dispose()
})
</script>

<style scoped>
.overview-page { gap: 26px; }
.overview-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0 0 4px; }
.overview-metric { min-width: 0; display: flex; align-items: flex-start; flex-direction: column; gap: 6px; border: 0; border-left: 1px solid var(--border-subtle); padding: 0 24px; background: transparent; text-align: left; color: var(--text-primary); cursor: pointer; border-radius: 2px; }
.overview-metric:first-child { padding-left: 0; border-left: 0; }
.overview-metric:hover .metric-label { color: var(--accent-primary); }
.metric-label { color: var(--text-muted); font-size: 13px; }
.metric-number { font-size: 38px; line-height: 1.25; font-weight: 500; letter-spacing: -1.5px; }
.metric-number small { color: var(--text-muted); font-size: 14px; font-weight: 400; letter-spacing: 0; margin-left: 8px; }
.metric-note { color: var(--text-muted); font-size: 12px; }
.urgent-dot { display: inline-block; width: 6px; height: 6px; background: var(--status-danger); border-radius: 50%; margin-right: 6px; flex-shrink: 0; }
.work-grid { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(290px, 1fr); gap: 22px; align-items: start; }
.work-panel { padding: 25px 26px; min-width: 0; }
.panel-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 18px; }
.panel-heading h2 { font-size: 18px; font-weight: 600; letter-spacing: -.5px; }
.panel-heading>span { color: var(--text-muted); font-size: 12px; }
.todo-filters { display: inline-flex; padding: 3px; gap: 2px; background: var(--bg-muted); border-radius: 10px; margin-bottom: 7px; }
.todo-filters button { background: transparent; border: 0; color: var(--text-muted); font-size: 12px; padding: 5px 13px; border-radius: 8px; cursor: pointer; }
.todo-filters button[aria-pressed=true] { background: white; color: var(--text-primary); box-shadow: 0 1px 4px rgb(29 29 31 / 7%); }
.todo-row { display: flex; align-items: center; gap: 14px; padding: 19px 0; border-bottom: 1px solid var(--border-subtle); }
.todo-row:last-child { border-bottom: 0; padding-bottom: 2px; }
.todo-symbol { width: 33px; height: 33px; flex-shrink: 0; display: grid; place-items: center; border-radius: 10px; background: var(--bg-canvas); color: var(--text-muted); }
.todo-symbol.urgent { background: var(--status-danger-bg); color: var(--status-danger); }
.todo-content { min-width: 0; flex: 1; }
.todo-content h3 { font-size: 14px; line-height: 1.6; font-weight: 500; }
.todo-content p { color: var(--text-muted); font-size: 12px; margin-top: 5px; overflow-wrap: anywhere; }
.meta-separator { margin: 0 7px; }
.todo-assignee { color: var(--text-muted); font-size: 11px; }
.row-action { display: flex; align-items: center; gap: 5px; color: var(--accent-primary); background: transparent; border: 0; padding: 10px 0 10px 8px; font-size: 12px; flex-shrink: 0; cursor: pointer; }
.row-action .el-icon { font-size: 11px; }
.empty-todo { padding: 30px 0; color: var(--text-muted); font-size: 13px; }
.occupancy-number { font-size: 32px; font-weight: 500; letter-spacing: -1px; margin: 4px 0 15px; }
.occupancy-number span { color: var(--text-muted); font-size: 13px; font-weight: 400; margin-left: 9px; letter-spacing: 0; }
.occupancy-caption, .quiet-note { color: var(--text-muted); font-size: 12px; margin-top: 10px; }
.energy-heading { border-top: 1px solid var(--border-subtle); padding-top: 20px; margin-top: 23px; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.energy-heading h3 { font-size: 14px; font-weight: 600; }
.energy-heading>span { font-size: 11px; color: var(--text-muted); }
.energy-alert { border: 0; background: transparent; display: flex; flex-direction: column; width: 100%; text-align: left; padding: 15px 0; border-bottom: 1px solid var(--border-subtle); cursor: pointer; }
.energy-alert:last-child { padding-bottom: 0; border: 0; }
.alert-title { display: flex; align-items: center; gap: 3px; color: var(--text-primary); font-size: 13px; font-weight: 500; width: 100%; }
.alert-title .el-icon { margin-left: auto; color: var(--text-muted); font-size: 11px; }
.alert-description { margin-top: 7px; color: var(--text-muted); font-size: 12px; line-height: 1.7; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.alert-meta { margin-top: 8px; font-size: 11px; color: var(--text-muted); }
.col-gap { margin-bottom: 8px; }
.echarts-box { height: 240px; width: 100%; }
.unit-note { font-size: 12px; color: var(--text-muted); }
.score-text { color: var(--text-primary); }
.star-text { color: var(--text-muted); }
@media (max-width: 1180px) { .work-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 760px) { .overview-metrics { grid-template-columns: 1fr 1fr; gap: 22px 0; } .overview-metric:nth-child(3) { border: 0; padding-left: 0; } .metric-number { font-size: 34px; } .overview-metric { padding: 0 16px; } .residence-overview { display: block; } .energy-heading { border-top: 1px solid var(--border-subtle); padding-top: 20px; margin-top: 23px; } .work-panel { padding: 22px; } }
@media (max-width: 420px) { .work-panel { padding: 20px 17px; } .todo-row { gap: 9px; } .todo-symbol { display: none; } .metric-note { font-size: 11px; } }
</style>
