<template>
  <div v-loading="loading" class="page-stack">
    <section v-if="precheckResult" class="surface-card precheck-panel">
      <div class="workspace-heading">
        <div>
          <h2>分寝前校验清单</h2>
          <p>未填画像 {{ precheckResult.missingProfileCount }} · 待分 {{ precheckResult.unallocatedCount }} · 男空床 {{ precheckResult.maleEmptyBeds }} / 女空床 {{ precheckResult.femaleEmptyBeds }}</p>
        </div>
        <el-tag :type="precheckResult.ready ? 'success' : 'warning'">{{ precheckResult.ready ? '可执行分寝' : '存在风险项' }}</el-tag>
      </div>
      <ul v-if="(precheckResult.warnings || []).length" class="precheck-warns">
        <li v-for="(w, i) in precheckResult.warnings" :key="i">{{ w }}</li>
      </ul>
    </section>

    <div v-if="loadError" class="error-banner" role="alert">
      <span>分寝数据加载失败，请重试。</span>
      <el-button type="danger" plain size="small" @click="loadData">重新加载</el-button>
    </div>

    <!-- 分寝主控台卡片 -->
    <el-card shadow="never" class="surface-card alloc-main-card">
      <!-- 顶部操作条：左侧标题与沙盘开关，右侧辅助管理工具 -->
      <div class="alloc-top-bar">
        <div class="section-title-group">
          <h3 class="section-title heading-balance">新生智能分寝</h3>
          <el-tag size="small" type="primary" effect="plain" class="algo-badge">六维加权欧氏聚类</el-tag>
          <el-popover placement="bottom-start" :width="360" trigger="click">
            <template #reference>
              <el-button size="small" text type="primary" icon="QuestionFilled">分寝规则说明</el-button>
            </template>
            <div class="rule-popover-content">
              <h4>分寝匹配规则说明</h4>
              <ul>
                <li><b>问卷维度</b>：采集作息时间、睡眠深浅、空调温度、卫生习惯、寝室氛围、异味敏感度 6 项指标（1~5分）。</li>
                <li><b>匹配方式</b>：按加权欧氏距离与余弦相似度将作息与生活习惯相近的同性别新生聚入同一寝室，并支持开启同班级优先聚拢约束。</li>
                <li><b>半空寝室兼顾</b>：自动提取房间内已有在住室友画像参与候选人贪心吸纳计算。</li>
              </ul>
            </div>
          </el-popover>
          <el-button size="small" text type="primary" @click="showSandbox = !showSandbox">
            {{ showSandbox ? '收起算法权重沙盘 ▲' : '展开算法权重沙盘 ▼' }}
          </el-button>
        </div>

        <div class="aux-actions">
          <el-button plain size="small" icon="Upload" type="success" @click="importVisible = true">新生 CSV 导入</el-button>
          <el-button plain size="small" icon="CircleCheck" @click="loadPrecheck">分寝前校验</el-button>
          <el-button plain size="small" icon="Document" @click="openAllocationLogs">分配记录</el-button>
          <el-button v-if="demoEnabled && isAdmin" plain size="small" icon="RefreshLeft" type="warning" :loading="resetting" @click="handleResetDemo">重置演示新生</el-button>
        </div>
      </div>

      <!-- 核心分寝操作栏（独立高亮展示，避免拥挤折行） -->
      <div class="primary-workflow-bar">
        <div class="workflow-left">
          <span class="field-label">目标楼栋：</span>
          <el-select
            v-model="selectedBuildingId"
            placeholder="请选择目标楼栋"
            class="building-select"
            @change="onBuildingChange"
          >
            <el-option
              v-for="b in buildings"
              :key="b.id"
              :label="`${b.buildingName} (${b.genderType === 1 ? '男寝' : '女寝'})`"
              :value="b.id"
            />
          </el-select>
        </div>
        <div class="workflow-right">
          <el-button
            :type="canCommit ? 'default' : 'primary'"
            :plain="canCommit"
            icon="View"
            :loading="running"
            class="action-btn-main"
            @click="handleRun(false)"
          >
            {{ allocResult ? '重新生成预览' : '生成预览方案' }}
          </el-button>
          <el-button
            :type="canCommit ? 'primary' : 'info'"
            icon="Check"
            :loading="running"
            :disabled="!canCommit"
            class="action-btn-main"
            @click="confirmAndCommit"
          >
            确认执行分配
          </el-button>
        </div>
      </div>

      <!-- 算法权重与混合约束沙盘调优面板 -->
      <transition name="el-zoom-in-top">
        <div v-if="showSandbox" class="sandbox-panel">
          <div class="sandbox-top">
            <div class="sandbox-strategy">
              <span class="field-label">分簇约束策略：</span>
              <el-radio-group v-model="strategyMode" size="small" @change="allocResult = null">
                <el-radio-button label="HABIT_FIRST">纯生活习惯聚类</el-radio-button>
                <el-radio-button label="CLASS_MIX">同班级优先 + 习惯聚类</el-radio-button>
              </el-radio-group>
            </div>
            <div class="sandbox-presets">
              <span class="preset-label">快捷预设：</span>
              <el-button-group size="small">
                <el-button size="small" text @click="applyWeightPreset('default')">默认推荐</el-button>
                <el-button size="small" text @click="applyWeightPreset('schedule')">作息敏感</el-button>
                <el-button size="small" text @click="applyWeightPreset('clean')">卫生整洁</el-button>
                <el-button size="small" text @click="applyWeightPreset('equal')">均等分配</el-button>
              </el-button-group>
              <el-tag :type="totalWeight === 100 ? 'success' : 'warning'" size="small" class="weight-total-tag">
                权重合计: {{ totalWeight }}% {{ totalWeight === 100 ? '✓' : '(建议100%)' }}
              </el-tag>
              <el-button size="small" link type="primary" @click="resetWeights">重置</el-button>
            </div>
          </div>
          <div class="weights-grid tabular-nums">
            <div v-for="(item, idx) in weightConfigs" :key="item.key" class="weight-card">
              <div class="w-head">
                <span class="w-title">{{ item.label }}</span>
                <b class="w-val">{{ customWeights[idx] }}%</b>
              </div>
              <el-slider v-model="customWeights[idx]" :min="5" :max="60" :step="5" size="small" />
              <div class="w-desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
      </transition>

      <!-- 流程步骤条 -->
      <ol class="alloc-steps" aria-label="分寝进度">
        <li v-for="(step, index) in allocationSteps" :key="step" :class="{ active: currentStep === index, complete: currentStep > index }" :aria-current="currentStep === index ? 'step' : undefined">
          <span class="step-marker">{{ currentStep > index ? '✓' : index + 1 }}</span><span>{{ step }}</span>
        </li>
      </ol>

      <!-- 步骤 1：当前楼栋分寝前置条件检查条（根据真实数据动态计算） -->
      <div class="condition-bar tabular-nums">
        <div class="cond-item">
          <span class="k">所选楼栋：</span>
          <b>{{ currentBuilding?.buildingName || '-' }} ({{ currentBuilding?.genderType === 1 ? '男寝' : '女寝' }})</b>
        </div>
        <div class="cond-item">
          <span class="k">符合性别的待分寝新生：</span>
          <b :class="buildingEligibleCount > 0 ? 'text-accent' : 'text-muted'">{{ buildingEligibleCount }} 人</b>
        </div>
        <div class="cond-item">
          <span class="k">该楼栋空余床位：</span>
          <b>{{ buildingFreeBeds }} 床</b>
        </div>
        <div class="cond-item">
          <span class="k">问卷填写率：</span>
          <el-tag size="small" :type="eligibleSurveyStats.rate >= 80 ? 'success' : 'warning'">
            {{ eligibleSurveyStats.rate }}% ({{ eligibleSurveyStats.filled }}/{{ eligibleSurveyStats.total }} 已填)
          </el-tag>
        </div>
      </div>
    </el-card>

    <!-- 步骤 2/3/4：预览方案或分配结果面板 -->
    <el-card v-if="allocResult" shadow="never" class="surface-card">
      <template #header>
        <div class="section-header">
          <div class="section-title-group">
            <span class="section-title tabular-nums">
              {{ allocResult.committed ? '分寝完成结果' : '分寝预览方案' }}（{{ allocResult.buildingName }} · {{ allocResult.strategyLabel || '加权贪心聚类' }}）
            </span>
            <el-tag :type="allocResult.committed ? 'success' : 'warning'" effect="light" size="small">
              {{ allocResult.committed ? '已正式分配床位' : '方案预览态（请核对后确认分配）' }}
            </el-tag>
          </div>
          <div class="toolbar-actions">
            <el-button size="small" plain icon="Download" @click="exportAllocationReport">导出方案报告</el-button>
            <el-button
              v-if="!allocResult.committed"
              type="primary"
              size="small"
              :loading="running"
              @click="confirmAndCommit"
            >
              确认按此方案分配
            </el-button>
            <el-button
              v-else
              type="success"
              plain
              size="small"
              @click="$router.push('/dorm-visual')"
            >
              前往房态管理查看 →
            </el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :xs="12" :sm="12" :md="6" :lg="6" class="col-gap">
          <div class="metric-box">
            <div class="label">按学号顺序分寝契合度</div>
            <div class="val muted tabular-nums">{{ allocResult.traditionalAvgHarmonyScore }}%</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6" class="col-gap">
          <div class="metric-box highlight">
            <div class="label">按习惯匹配分寝契合度</div>
            <div class="val success tabular-nums">{{ allocResult.smartAvgHarmonyScore }}%</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6" class="col-gap">
          <div class="metric-box">
            <div class="label">契合度提升</div>
            <div class="val accent tabular-nums">+{{ allocResult.scoreImprovement }}%</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6" class="col-gap">
          <div class="metric-box">
            <div class="label">本次分配人数 / 寝室数</div>
            <div class="val tabular-nums">{{ allocResult.allocatedStudentCount }} 人 / {{ allocResult.allocatedRoomCount }} 间</div>
          </div>
        </el-col>
      </el-row>

      <el-alert :title="allocResult.scoreNote" type="info" :closable="false" show-icon />
      <p v-if="allocResult.unallocatedStudents?.length" class="field-tip">未分配 {{ allocResult.unallocatedStudents.length }} 人：{{ allocResult.unallocatedStudents.map(s => `${s.realName}（${s.reason}）`).join('、') }}</p>

      <el-divider content-position="left">各寝室分配名单</el-divider>

      <el-row :gutter="16">
        <el-col
          v-for="room in allocResult.roomAllocations"
          :key="room.roomId"
          :xs="24"
          :sm="24"
          :md="12"
          :lg="12"
          class="cluster-col"
        >
          <div class="cluster-box">
            <div class="section-header">
              <div class="toolbar-actions">
                <b class="room-title">{{ room.buildingName }} · {{ room.roomNo }} 室</b>
                <el-tag size="small" type="info">{{ room.groupStyleTag }}</el-tag>
              </div>
              <el-tag type="success" size="small" class="tabular-nums">契合度 {{ room.harmonyScore }}%</el-tag>
            </div>
            <p class="field-tip">{{ room.explanation }}</p>
            <el-table :data="room.students" size="small" border class="cluster-table">
              <el-table-column prop="bedNo" label="床号" width="60" />
              <el-table-column prop="realName" label="姓名" width="90" />
              <el-table-column prop="studentNo" label="学号" width="105" />
              <el-table-column label="作息习惯" width="130">
                <template #default="{ row }">
                  <span>{{ scheduleText(row.scheduleScore) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="hobbyTags" label="习惯标签" show-overflow-tooltip />
            </el-table>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 学生习惯问卷与分寝状态列表（精简列宽，免横向滚动） -->
    <el-card shadow="never" class="surface-card">
      <template #header>
        <div class="section-header">
          <span class="section-title tabular-nums">学生习惯问卷与住宿状态（共 {{ filteredCandidates.length }} 人）</span>
          <div class="toolbar-actions">
            <el-radio-group v-model="filterStatus" size="small">
              <el-radio-button label="ALL">全部</el-radio-button>
              <el-radio-button label="UNALLOCATED">待分寝</el-radio-button>
              <el-radio-button label="ALLOCATED">已分寝</el-radio-button>
            </el-radio-group>
            <el-button size="small" type="success" plain icon="Download" @click="exportStudents">
              导出名单
            </el-button>
          </div>
        </div>
      </template>

      <p v-if="!filteredCandidates.length" class="list-empty">暂无符合条件的学生</p>
      <div v-else>
        <div class="person-table-head" aria-hidden="true">
          <span>学生信息</span>
          <span>生活习惯画像</span>
          <span>住宿分配状态</span>
          <span>操作</span>
        </div>
        <ul class="person-list" aria-label="学生习惯问卷与住宿状态">
        <li v-for="row in filteredCandidates" :key="row.studentId || row.studentNo" class="person-row">
          <div class="person-identity">
            <span class="person-avatar" aria-hidden="true">{{ (row.realName || '?').slice(0, 1) }}</span>
            <div><strong>{{ row.realName || '-' }}</strong><small>{{ row.studentNo }} · {{ row.gender === 1 ? '男' : '女' }} · {{ row.className || '-' }}</small></div>
          </div>
          <div class="person-details">
            <strong>{{ row.profile ? scheduleText(row.profile.scheduleScore) : '问卷未填写' }}<template v-if="row.profile"> · {{ atmosphereText(row.profile.atmosphereScore) }}</template></strong>
            <small>{{ row.profile?.hobbyTags || '暂无习惯标签' }}</small>
          </div>
          <div class="person-details"><strong>{{ row.allocated ? '已分寝' : '待分寝' }}</strong><small>{{ row.roomInfo || '-' }}</small></div>
          <div class="person-actions">
            <el-button link type="primary" @click="openEditProfile(row)">问卷详情</el-button>
            <el-button v-if="row.roomId" link type="primary" @click="openRadar(row.roomId)">契合度图</el-button>
          </div>
        </li>
      </ul>
      </div>
    </el-card>

    <!-- 室友契合度雷达图弹窗 -->
    <el-dialog v-model="radarVisible" title="寝室室友习惯契合度对比" width="600px" @closed="disposeRadar">
      <div v-if="radarData">
        <div class="radar-summary">
          <el-tag size="large" type="success" class="tabular-nums">契合度：{{ radarData.harmonyScore }}%</el-tag>
          <el-tag size="large" type="info">{{ radarData.styleTag }}</el-tag>
        </div>
        <div ref="radarChartEl" class="radar-canvas"></div>
      </div>
    </el-dialog>

    <!-- 编辑学生习惯问卷弹窗 -->
    <el-dialog v-model="editVisible" title="学生生活习惯问卷详情" width="500px">
      <el-form :model="editForm" label-width="135px">
        <el-form-item label="作息时间 (1早~5晚)">
          <el-slider v-model="editForm.scheduleScore" :min="1" :max="5" :step="1" show-stops />
        </el-form-item>
        <el-form-item label="睡眠深浅 (1深~5浅)">
          <el-slider v-model="editForm.sleepDepthScore" :min="1" :max="5" :step="1" show-stops />
        </el-form-item>
        <el-form-item label="空调偏好 (1冷~5暖)">
          <el-slider v-model="editForm.acTempScore" :min="1" :max="5" :step="1" show-stops />
        </el-form-item>
        <el-form-item label="卫生标准 (1随性~5整洁)">
          <el-slider v-model="editForm.cleanlinessScore" :min="1" :max="5" :step="1" show-stops />
        </el-form-item>
        <el-form-item label="寝室氛围 (1安静~5活跃)">
          <el-slider v-model="editForm.atmosphereScore" :min="1" :max="5" :step="1" show-stops />
        </el-form-item>
        <el-form-item label="异味敏感 (1低~5无烟)">
          <el-slider v-model="editForm.odorSensitiveScore" :min="1" :max="5" :step="1" show-stops />
        </el-form-item>
        <el-form-item label="个人习惯标签">
          <el-input v-model="editForm.hobbyTags" placeholder="逗号分隔，如：考研,早起,羽毛球" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存修改</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="importVisible" title="新生 CSV 批量导入" width="560px">
      <p class="field-tip">表头支持：学号,姓名,性别,学院,专业,班级,年级（或 username,realName,gender,college,major,className,grade）。默认密码 123456，导入后进入待填习惯问卷队列。</p>
      <el-input v-model="importCsv" type="textarea" :rows="10" placeholder="学号,姓名,性别,学院,专业,班级,年级" />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="logsVisible" title="分寝与人工床位变更记录" width="760px">
      <el-table v-loading="logsLoading" :data="allocationLogs" empty-text="暂无记录">
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column label="类型" width="120"><template #default="{ row }">{{ row.algorithmStrategy === 'MANUAL' ? '人工床位变更' : '智能分寝' }}</template></el-table-column>
        <el-table-column prop="buildingId" label="楼栋 ID" width="85" />
        <el-table-column prop="operatorId" label="操作人 ID" width="95" />
        <el-table-column label="详情" min-width="230"><template #default="{ row }">{{ allocationLogDetail(row) }}</template></el-table-column>
      </el-table>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import request, { downloadExport } from '@/api/request'
import { allocationReportCsv } from '@/allocationReport'

const route = useRoute()
const loading = ref(false)
const loadError = ref(false)
const savingProfile = ref(false)
const resetting = ref(false)
const demoEnabled = ref(false)
const isAdmin = JSON.parse(localStorage.getItem('dorm_admin_user') || '{}').role === 'ADMIN'

const buildings = ref([])
const rooms = ref([])
const selectedBuildingId = ref(1)
const candidates = ref([])
const precheckResult = ref(null)
const importVisible = ref(false)
const logsVisible = ref(false)
const logsLoading = ref(false)
const allocationLogs = ref([])
const openAllocationLogs = async () => {
  logsVisible.value = true; logsLoading.value = true
  try { const result = await request.get('/smart-alloc/logs'); allocationLogs.value = result.data || [] }
  finally { logsLoading.value = false }
}
const allocationLogDetail = row => {
  if (row.algorithmStrategy !== 'MANUAL') return `批次 ${row.batchNo}，分配 ${row.allocatedStudentCount} 人`
  try {
    const detail = JSON.parse(row.detailJson)
    return `床位 #${detail.bedId}：${detail.previousStudentId ?? '空'} → ${detail.studentId ?? '空'}`
  } catch (_) { return '记录详情不可用' }
}
const importing = ref(false)
const importCsv = ref('学号,姓名,性别,学院,专业,班级,年级\n20261001,王小明,男,计算机学院,软件工程,软工2601,2026\n20261002,李小红,女,计算机学院,软件工程,软工2602,2026')

const filterStatus = ref(route.query.filter || 'ALL')
const running = ref(false)
const allocResult = ref(null)
const exportAllocationReport = () => {
  if (!allocResult.value) return
  const url = URL.createObjectURL(new Blob([allocationReportCsv(allocResult.value)], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = '分寝方案对比报告.csv'
  link.click()
  URL.revokeObjectURL(url)
}

const showSandbox = ref(false)
const strategyMode = ref('HABIT_FIRST')
const customWeights = ref([30, 20, 10, 15, 20, 5])
const weightConfigs = [
  { key: 'schedule', label: '作息时间权重', desc: '早睡早起 ↔ 熬夜晚起' },
  { key: 'sleep', label: '睡眠浅度权重', desc: '沉睡不易醒 ↔ 浅眠易惊醒' },
  { key: 'ac', label: '空调温度权重', desc: '偏好低温 ↔ 怕冷常温' },
  { key: 'clean', label: '卫生标准权重', desc: '随性自在 ↔ 每日打扫洁癖' },
  { key: 'atmos', label: '寝室氛围权重', desc: '安静自习型 ↔ 社交游戏型' },
  { key: 'odor', label: '异味敏感权重', desc: '不敏感 ↔ 极度敏感无烟' }
]

const totalWeight = computed(() =>
  customWeights.value.reduce((sum, w) => sum + Number(w || 0), 0)
)

const applyWeightPreset = (preset) => {
  if (preset === 'default') {
    customWeights.value = [30, 20, 10, 15, 20, 5]
  } else if (preset === 'schedule') {
    customWeights.value = [40, 30, 5, 10, 10, 5]
  } else if (preset === 'clean') {
    customWeights.value = [15, 10, 10, 35, 10, 20]
  } else if (preset === 'equal') {
    customWeights.value = [17, 17, 17, 17, 16, 16]
  }
  allocResult.value = null
  ElMessage.success('已切换沙盘权重预设')
}

const resetWeights = () => {
  customWeights.value = [30, 20, 10, 15, 20, 5]
  strategyMode.value = 'HABIT_FIRST'
  allocResult.value = null
  ElMessage.info('已恢复默认六维权重与策略')
}

const radarVisible = ref(false)
const radarData = ref(null)
const radarChartEl = ref(null)
let radarInstance = null

const editVisible = ref(false)
const editForm = reactive({
  studentId: null,
  scheduleScore: 3,
  sleepDepthScore: 3,
  acTempScore: 3,
  cleanlinessScore: 3,
  atmosphereScore: 3,
  odorSensitiveScore: 5,
  hobbyTags: ''
})

const currentBuilding = computed(() =>
  buildings.value.find(b => b.id === selectedBuildingId.value)
)

const eligibleStudents = computed(() => {
  const b = currentBuilding.value
  if (!b) return []
  return candidates.value.filter(c => !c.allocated && c.gender === b.genderType)
})

const buildingEligibleCount = computed(() => eligibleStudents.value.length)

const eligibleSurveyStats = computed(() => {
  const list = eligibleStudents.value
  if (!list.length) return { rate: 100, filled: 0, total: 0 }
  const filled = list.filter(c => c.profile && c.profile.scheduleScore).length
  return {
    rate: Math.round((filled * 100) / list.length),
    filled,
    total: list.length
  }
})

const buildingFreeBeds = computed(() => {
  return rooms.value
    .filter(r => r.buildingId === selectedBuildingId.value)
    .reduce((sum, r) => sum + Math.max(0, (r.capacity || 0) - (r.occupiedCount || 0)), 0)
})

// 只有在生成有效预览方案后，才允许点击确认执行分配
const canCommit = computed(() => {
  return Boolean(
    allocResult.value &&
    !allocResult.value.committed &&
    allocResult.value.allocatedStudentCount > 0
  )
})

const allocationSteps = ['检查条件', '预览方案', '确认分配', '查看结果']
const currentStep = computed(() => {
  if (!allocResult.value) return 0 // 1. 检查条件
  if (!allocResult.value.committed) return 1 // 2. 预览方案
  return 3 // 4. 查看结果
})

const filteredCandidates = computed(() => {
  if (filterStatus.value === 'UNALLOCATED') return candidates.value.filter(c => !c.allocated)
  if (filterStatus.value === 'ALLOCATED') return candidates.value.filter(c => c.allocated)
  return candidates.value
})

const scheduleText = (v) => {
  if (!v) return '规律作息'
  if (v <= 2) return '早睡型'
  if (v >= 4) return '晚睡型'
  return '正常作息'
}

const atmosphereText = (v) => {
  if (!v) return '均衡型'
  if (v <= 2) return '安静自习'
  if (v >= 4) return '活跃社交'
  return '劳逸结合'
}

const onBuildingChange = () => {
  allocResult.value = null
}

const loadData = async () => {
  loading.value = true
  loadError.value = false
  try {
    const [bRes, rRes, cRes] = await Promise.all([
      request.get('/dorm/buildings'),
      request.get('/dorm/rooms'),
      request.get('/smart-alloc/candidates')
    ])
    buildings.value = bRes.data || []
    rooms.value = rRes.data || []
    candidates.value = cRes.data || []
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const handleRun = async (commit) => {
  running.value = true
  try {
    const normalizedWeights = customWeights.value.map(v => Number(v) / 100.0)
    const res = await request.post('/smart-alloc/run', {
      buildingId: selectedBuildingId.value,
      commit,
      strategy: strategyMode.value,
      weights: normalizedWeights
    })
    allocResult.value = res.data
    ElMessage.success(commit ? '已正式写入宿舍床位' : '已生成分寝预览方案，请核对后确认分配')
    if (commit) {
      await loadData()
    }
  } finally {
    running.value = false
  }
}

const handleResetDemo = async () => {
  resetting.value = true
  try {
    const res = await request.post('/smart-alloc/reset-demo')
    allocResult.value = null
    ElMessage.success(res.message || '2026级待分寝新生已重置为未分配状态')
    await loadData()
  } finally {
    resetting.value = false
  }
}

const confirmAndCommit = async () => {
  if (!canCommit.value) {
    ElMessage.warning('请先生成预览方案')
    return
  }
  await ElMessageBox.confirm(
    `系统将按当前数据重新计算并分配【${currentBuilding.value?.buildingName || '所选楼栋'}】的床位；若学生或床位数据变化，结果可能不同于预览。是否继续？`,
    '确认执行分寝',
    {
      confirmButtonText: '确认分配',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  await handleRun(true)
}

const disposeRadar = () => {
  if (radarInstance) {
    radarInstance.dispose()
    radarInstance = null
  }
}

const openRadar = async (roomId) => {
  const res = await request.get(`/smart-alloc/room-radar/${roomId}`)
  radarData.value = res.data
  radarVisible.value = true
  await nextTick()
  if (radarChartEl.value) {
    disposeRadar()
    radarInstance = echarts.init(radarChartEl.value)
    const roommates = radarData.value.roommates || []
    radarInstance.setOption({
      color: ['#0284c7', '#0f172a', '#16a34a', '#d97706'],
      tooltip: {},
      legend: { bottom: 0, data: roommates.map(r => `${r.bedNo}号床·${r.realName}`) },
      radar: {
        indicator: [
          { name: '作息时间', max: 5 },
          { name: '睡眠浅度', max: 5 },
          { name: '空调偏好', max: 5 },
          { name: '卫生标准', max: 5 },
          { name: '寝室氛围', max: 5 },
          { name: '无烟要求', max: 5 }
        ]
      },
      series: [
        {
          type: 'radar',
          data: roommates.map(r => ({
            name: `${r.bedNo}号床·${r.realName}`,
            value: r.vector || [3, 3, 3, 3, 3, 5],
            areaStyle: { opacity: 0.12 }
          }))
        }
      ]
    })
  }
}

const openEditProfile = (row) => {
  editForm.studentId = row.studentId
  const p = row.profile || {}
  editForm.scheduleScore = p.scheduleScore || 3
  editForm.sleepDepthScore = p.sleepDepthScore || 3
  editForm.acTempScore = p.acTempScore || 3
  editForm.cleanlinessScore = p.cleanlinessScore || 3
  editForm.atmosphereScore = p.atmosphereScore || 3
  editForm.odorSensitiveScore = p.odorSensitiveScore || 5
  editForm.hobbyTags = p.hobbyTags || ''
  editVisible.value = true
}

const saveProfile = async () => {
  savingProfile.value = true
  try {
    await request.post('/smart-alloc/habit-profile', editForm)
    ElMessage.success('问卷信息已更新')
    editVisible.value = false
    await loadData()
  } finally {
    savingProfile.value = false
  }
}

const exportStudents = () => {
  downloadExport('students', '学生住宿.csv').catch(() => ElMessage.error('导出失败，请检查登录状态'))
}

const loadPrecheck = async () => {
  const res = await request.get('/smart-alloc/precheck', { params: { buildingId: selectedBuildingId.value || undefined } })
  precheckResult.value = res.data || null
  ElMessage.success('校验完成')
}

const submitImport = async () => {
  importing.value = true
  try {
    const res = await request.post('/auth/import-students', { csv: importCsv.value })
    ElMessage.success(res.message || '导入完成')
    importVisible.value = false
    await loadData()
    await loadPrecheck()
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  request.get('/utility/pay/config').then(res => { demoEnabled.value = res.data?.demoSupported === true }).catch(() => {})
  loadData()
})

onBeforeUnmount(() => {
  disposeRadar()
})
</script>

<style scoped>
.field-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 600;
}
.alloc-main-card {
  padding-bottom: 20px;
}
.alloc-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-subtle);
}
.algo-badge {
  font-weight: 500;
  border-radius: 6px;
}
.aux-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.primary-workflow-bar {
  margin-top: 16px;
  padding: 14px 18px;
  background: var(--accent-subtle);
  border: 1px solid var(--accent-border);
  border-radius: var(--radius-md);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
}
.workflow-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.workflow-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.action-btn-main {
  min-width: 120px;
}
.building-select {
  width: 220px;
}
.sandbox-panel {
  margin-top: 16px;
  padding: 16px 20px;
  background: var(--bg-canvas);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}
.sandbox-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--border-strong);
}
.sandbox-strategy {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sandbox-presets {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.preset-label {
  font-size: 12px;
  color: var(--text-muted);
}
.weight-total-tag {
  font-weight: 600;
  font-size: 12px;
}
.weights-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 20px;
}
.weight-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  transition: all 0.2s ease;
}
.weight-card:hover {
  border-color: var(--accent-primary);
  box-shadow: 0 2px 8px rgba(0, 110, 219, 0.08);
}
.weight-card .w-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}
.w-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}
.w-val {
  font-size: 14px;
  font-weight: 600;
  color: var(--accent-primary);
}
.w-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.alloc-steps {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 24px;
  padding: 20px 0;
  border-top: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
  gap: 12px;
}
.alloc-steps li { display: flex; align-items: center; gap: 9px; min-width: 0; font-size: 13px; color: var(--text-muted); }
.alloc-steps li.active { color: var(--text-primary); font-weight: 600; }
.step-marker { display: grid; place-items: center; flex-shrink: 0; width: 26px; height: 26px; border-radius: 50%; background: var(--bg-canvas); font-size: 12px; }
.alloc-steps li.active .step-marker { background: var(--accent-primary); color: white; }
.alloc-steps li.complete .step-marker { color: var(--status-success); background: var(--status-success-bg); }
.condition-bar {
  margin-top: 22px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  font-size: 13px;
}
.cond-item .k {
  color: var(--text-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 7px;
}
.cond-item b { font-size: 14px; font-weight: 500; }
.text-accent {
  color: var(--accent-primary);
}
.text-muted {
  color: var(--text-muted);
}
.col-gap {
  margin-bottom: 8px;
}
.metric-box {
  padding: 10px 14px;
  border-left: 1px solid var(--border-subtle);
  text-align: left;
}
.metric-box.highlight {
  background: transparent;
}
.metric-box .label {
  font-size: 12px;
  color: var(--text-muted);
}
.metric-box .val {
  font-size: 22px;
  font-weight: 600;
  margin-top: 4px;
  color: var(--text-primary);
}
.val.muted { color: var(--text-muted); }
.val.success { color: var(--status-success); }
.val.accent { color: var(--accent-primary); }

.cluster-col {
  margin-bottom: 12px;
}
.cluster-box {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 14px;
  background: var(--bg-surface);
}
.cluster-table {
  margin-top: 10px;
}
.room-title {
  font-size: 15px;
  color: var(--text-primary);
}
.radar-summary {
  display: flex;
  justify-content: center;
  gap: 10px;
}
.radar-canvas {
  width: 100%;
  height: 340px;
  margin-top: 12px;
}
@media (max-width: 760px) {
  .alloc-steps, .condition-bar, .weights-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .alloc-steps { gap: 16px; }
  .building-select { max-width: 100%; }
}

.precheck-panel { padding: 18px 22px; margin-bottom: 14px; }
.precheck-warns { margin: 10px 0 0; padding-left: 18px; color: var(--status-warning); font-size: 13px; line-height: 1.7; }
</style>
