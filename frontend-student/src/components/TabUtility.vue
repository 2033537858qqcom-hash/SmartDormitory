<template>
  <div class="tab-utility">
    <header class="mobile-page-heading">
      <h1>水电与宿务</h1>
      <p>账户、账单与住宿服务，都在这里。</p>
    </header>

    <!-- 实时水电余额双卡 + 低额预警提醒 + 快捷宿务按钮 -->
    <div class="balance-card">
      <div class="b-row">
        <div>
          <div class="b-label">寝室电费余额</div>
          <div v-if="isAllocated" class="b-val tabular-nums" :class="{ 'low-text': Number(dash.room?.electricBalance || 0) < 30 }">
            {{ dash.room?.electricBalance != null ? formatMoney(dash.room.electricBalance) : '暂无数据' }}
          </div>
          <div v-else class="b-val pending-val">待开通</div>
        </div>
        <div>
          <div class="b-label">寝室水费余额</div>
          <div v-if="isAllocated" class="b-val tabular-nums" :class="{ 'low-text': Number(dash.room?.waterBalance || 0) < 15 }">
            {{ dash.room?.waterBalance != null ? formatMoney(dash.room.waterBalance) : '暂无数据' }}
          </div>
          <div v-else class="b-val pending-val">待开通</div>
        </div>
      </div>

      <div v-if="isAllocated && isLowBalance" class="low-balance-banner">
        <span>⚠️ 当前水电账户余额偏低，建议及时充值以免影响正常供电供水。</span>
        <button type="button" class="banner-btn spring-btn" @click="openSheetWithCheck('recharge')">去充值</button>
      </div>

      <div class="btn-group">
        <button type="button" class="act-btn primary spring-btn" @click="openSheetWithCheck('recharge')">
          <span class="act-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M13 2 4 14h7l-1 8 10-12h-8z" /></svg></span><span>水电充值</span>
          <span v-if="!isAllocated" class="btn-sub-tag">需分寝</span>
        </button>
        <button type="button" class="act-btn secondary spring-btn" @click="openSheetWithCheck('adjust')">
          <span class="act-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h15m-4-4 4 4-4 4M20 17H5m4-4-4 4 4 4" /></svg></span><span>调宿/退宿</span>
          <span v-if="!isAllocated" class="btn-sub-tag">需分寝</span>
        </button>
        <button type="button" class="act-btn secondary spring-btn" @click="openSheetWithCheck('visitor')">
          <span class="act-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="2" /><path d="M15 8h2m-2 4h2M7 16h10" /></svg></span><span>预约访客码</span>
          <span v-if="!isAllocated" class="btn-sub-tag">需分寝</span>
        </button>
        <button type="button" class="act-btn secondary spring-btn" @click="openSheetWithCheck('late')">
          <span class="act-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg></span><span>晚归报备</span>
          <span v-if="!isAllocated" class="btn-sub-tag">需分寝</span>
        </button>
      </div>
    </div>

    <!-- 子栏目切换胶囊 -->
    <div class="sub-tabs" role="tablist">
      <button
        type="button"
        class="sub-pill spring-btn tabular-nums"
        role="tab"
        id="utility-tab-bills"
        aria-controls="utility-panel-bills"
        :aria-selected="subTab === 'bills'"
        :tabindex="subTab === 'bills' ? 0 : -1"
        :class="{ active: subTab === 'bills' }"
        @click="subTab = 'bills'"
        @keydown.right.prevent="focusSubTab('affair')"
        @keydown.left.prevent="focusSubTab('affair')"
      >
        水电账单 ({{ (dash.bills || []).length }})
      </button>
      <button
        type="button"
        class="sub-pill spring-btn"
        role="tab"
        id="utility-tab-affair"
        aria-controls="utility-panel-affair"
        :aria-selected="subTab === 'affair'"
        :tabindex="subTab === 'affair' ? 0 : -1"
        :class="{ active: subTab === 'affair' }"
        @click="subTab = 'affair'"
        @keydown.right.prevent="focusSubTab('bills')"
        @keydown.left.prevent="focusSubTab('bills')"
      >
        访客绿码与宿务
      </button>
    </div>

    <!-- 子面板 1：月度阶梯水电账单 -->
    <div v-if="subTab === 'bills'" id="utility-panel-bills" class="bill-section" role="tabpanel" aria-labelledby="utility-tab-bills">
      <div v-if="!(dash.bills && dash.bills.length)" class="empty-tip">
        <p>{{ isAllocated ? '当前暂无待出账水电单' : '分配寝室后即可查看月度水电账单' }}</p>
        <button v-if="isAllocated" type="button" class="empty-action spring-btn" @click="rechargeSheet = true">
          预先充值水电费
        </button>
      </div>

      <div v-for="b in dash.bills || []" :key="b.id" class="bill-card">
        <div class="bill-head">
          <span class="month tabular-nums">{{ b.billMonth }} 月度账单</span>
          <span :class="b.payStatus === 1 ? 'paid' : 'unpaid'">
            {{ b.payStatus === 1 ? '✓ 已缴清' : '待缴费' }}
          </span>
        </div>

        <div v-if="b.abnormalFlag === 1" class="abnormal-banner">
          <b>能耗异常提醒：</b>{{ b.abnormalReason }}
        </div>

        <div class="bill-grid tabular-nums">
          <div>用电：<b>{{ b.electricUsed }}度</b> ({{ formatMoney(b.electricCost) }})</div>
          <div>用水：<b>{{ b.waterUsed }}吨</b> ({{ formatMoney(b.waterCost) }})</div>
        </div>

        <div class="bill-foot tabular-nums">
          <span>账单总额：<b class="price">{{ formatMoney(b.totalCost) }}</b> (人均 {{ b.perCapitaCost == null ? '暂无数据' : formatMoney(b.perCapitaCost) }})</span>
          <button
            v-if="b.payStatus === 0"
            type="button"
            class="pay-btn spring-btn"
            :disabled="submitting"
            @click="payBill(b)"
          >
            余额抵扣缴纳
          </button>
        </div>
      </div>
    </div>

    <!-- 子面板 2：访客电子通行码、调宿进度、晚归报备与在线申诉 -->
    <div v-else id="utility-panel-affair" class="affair-section" role="tabpanel" aria-labelledby="utility-tab-affair">
      <div class="sec-bar">
        <h3 class="sec-title">我的访客电子通行码</h3>
        <button type="button" class="mini-add-btn spring-btn" @click="openSheetWithCheck('visitor')">+ 预约访客</button>
      </div>

      <div v-if="!(dash.myVisitors && dash.myVisitors.length)" class="empty-tip">
        <p>暂无访客预约记录</p>
        <button type="button" class="empty-action spring-btn" @click="openSheetWithCheck('visitor')">+ 预约访客通行绿码</button>
      </div>
      <button
        v-for="v in dash.myVisitors || []"
        :key="v.id"
        type="button"
        class="pass-card spring-btn"
        @click="openQrDetail(v)"
      >
        <div class="pass-left">
          <div class="p-name">
            {{ v.visitorName }}
            <span class="rel">{{ v.relation }}</span>
            <span class="code-tag tabular-nums">{{ v.passCode || `VP-80${v.id}` }}</span>
          </div>
          <div class="p-meta">到访寝室：{{ v.roomNo }}室 · {{ v.visitReason }}</div>
          <div class="p-time tabular-nums">登记时间：{{ formatDateTime(v.enterTime) }}</div>
        </div>
        <div class="pass-right">
          <svg
            class="mini-qr"
            :class="{ expired: v.status !== 0 }"
            :viewBox="getRealQrSpec(v).viewBox"
            shape-rendering="crispEdges"
            aria-hidden="true"
          >
            <rect width="100%" height="100%" fill="#ffffff" />
            <path :d="getRealQrSpec(v).path" />
          </svg>
          <span class="pass-status" :class="{ active: v.status === 0 }">
            {{ v.status === 0 ? '● 绿码亮码 ›' : '已离楼' }}
          </span>
        </div>
      </button>

      <div class="sec-bar section-gap">
        <h3 class="sec-title">调宿与退宿申请进度</h3>
        <button type="button" class="mini-add-btn spring-btn" @click="openSheetWithCheck('adjust')">+ 申请调宿/退宿</button>
      </div>

      <div v-if="!(dash.myAdjusts && dash.myAdjusts.length)" class="empty-tip">
        <p>暂无调宿或退宿申请记录</p>
      </div>
      <div v-for="a in dash.myAdjusts || []" :key="a.id" class="adjust-item">
        <div class="a-top tabular-nums">
          <b>{{ a.applyType === 'CHECKOUT' ? '申请退宿离校' : `调入目标：${formatRoomDisplay(a.targetRoomId, a.targetBedNo)}` }}</b>
          <span class="a-badge" :class="a.status">
            {{ a.status === 'PENDING' ? '审批中' : (a.status === 'APPROVED' ? '已批准' : '已驳回') }}
          </span>
        </div>
        <div class="a-reason">申请原因：{{ a.reason }}</div>
        <div v-if="a.reviewComment" class="a-reply">审批意见：{{ a.reviewComment }}</div>
      </div>

      <div class="sec-bar section-gap">
        <h3 class="sec-title">晚归报备与违纪申诉</h3>
        <button type="button" class="mini-add-btn spring-btn" @click="openSheetWithCheck('late')">+ 晚归报备</button>
      </div>

      <div v-if="!(dash.myDisciplines && dash.myDisciplines.length)" class="empty-tip">
        <p>作息良好，暂无晚归或违纪记录</p>
      </div>
      <div v-for="d in dash.myDisciplines || []" :key="d.id" class="adjust-item">
        <div class="a-top tabular-nums">
          <b>{{ d.recordType === 'LATE_RETURN' ? '晚归记录 / 报备' : '安全违纪记录' }}</b>
          <span v-if="d.status === 2" class="a-badge APPROVED">✓ 申诉已核销 · 免扣分</span>
          <span v-else-if="d.status === 3" class="a-badge PENDING">● 申诉待审</span>
          <span v-else class="deduct">-{{ d.deductScore }}分</span>
        </div>
        <div class="a-reason tabular-nums">{{ d.description }} ({{ formatDateTime(d.occurTime) }})</div>
        <div v-if="d.status === 1 && !String(d.description || '').includes('[学生报备]')" class="appeal-bar">
          <button type="button" class="appeal-btn spring-btn" @click="openAppealSheet(d)">
            有异议？提交证明申诉核销 ›
          </button>
        </div>
      </div>
    </div>

    <!-- 1. 水电充值半屏抽屉（支持切换电费/水费账户） -->
    <BottomSheet v-model="rechargeSheet" title="寝室水电费充值">
      <div class="type-switch">
        <button
          type="button"
          class="type-pill spring-btn"
          :class="{ active: rechargeTarget === 'ELECTRIC' }"
          @click="rechargeTarget = 'ELECTRIC'"
        >
          ⚡ 充值电费
        </button>
        <button
          type="button"
          class="type-pill spring-btn"
          :class="{ active: rechargeTarget === 'WATER' }"
          @click="rechargeTarget = 'WATER'"
        >
          💧 充值水费
        </button>
      </div>
      <div class="amount-grid">
        <button
          v-for="amt in [30, 50, 100, 200]"
          :key="amt"
          type="button"
          class="amt-chip spring-btn tabular-nums"
          :class="{ active: rechargeAmount === amt }"
          :aria-pressed="rechargeAmount === amt"
          @click="rechargeAmount = amt"
        >
          ¥{{ amt }}
        </button>
      </div>
      <button type="button" class="sheet-submit spring-btn tabular-nums" :disabled="submitting" @click="submitRecharge">
        {{ submitting ? '正在充值...' : `确认充值${rechargeTarget === 'ELECTRIC' ? '电费' : '水费'} ¥${rechargeAmount}.00` }}
      </button>
    </BottomSheet>

    <!-- 2. 调宿与退宿申请半屏抽屉（内置六维画像智能推荐空床位） -->
    <BottomSheet v-model="adjustSheet" title="调换宿舍与退宿办理">
      <div class="type-switch">
        <button
          type="button"
          class="type-pill spring-btn"
          :class="{ active: adjustForm.applyType === 'ADJUST' }"
          @click="adjustForm.applyType = 'ADJUST'"
        >
          申请调换宿舍
        </button>
        <button
          type="button"
          class="type-pill spring-btn"
          :class="{ active: adjustForm.applyType === 'CHECKOUT' }"
          @click="adjustForm.applyType = 'CHECKOUT'"
        >
          办理退宿离校
        </button>
      </div>

      <template v-if="adjustForm.applyType === 'ADJUST'">
        <!-- 智能推荐契合寝室列表 -->
        <div v-if="recommendedRooms.length" class="rec-box">
          <div class="rec-header">✨ 基于您的六维画像智能推荐空床：</div>
          <div class="rec-list">
            <button
              v-for="rec in recommendedRooms.slice(0, 3)"
              :key="rec.roomId"
              type="button"
              class="rec-card spring-btn"
              :class="{ active: adjustForm.targetRoomId === rec.roomId }"
              @click="applyRecommendedRoom(rec)"
            >
              <div class="rec-top tabular-nums">
                <b>{{ rec.buildingName }} · {{ rec.roomNo }}室 ({{ rec.recommendedBedNo }}号床)</b>
                <span class="rec-score">契合度 {{ rec.matchScore }}%</span>
              </div>
              <div class="rec-sub">{{ rec.styleTag }} · 调入后预计寝室和谐度 {{ rec.predictedHarmonyScore }}%</div>
            </button>
          </div>
        </div>

        <div class="form-row">
          <label for="adj-building">1. 选择目标楼栋</label>
          <select id="adj-building" v-model="selectedBuildingId" class="inp" @change="onBuildingSelectChange">
            <option v-for="b in buildings" :key="b.id" :value="b.id">
              {{ b.buildingName }} ({{ b.genderType === 1 ? '男寝' : '女寝' }})
            </option>
          </select>
        </div>

        <div class="form-row">
          <label for="adj-room">2. 选择有空床的房间</label>
          <select id="adj-room" v-model="adjustForm.targetRoomId" class="inp" @change="onRoomSelectChange">
            <option v-if="!availableRoomsInBuilding.length" :value="null">该楼栋暂无可选房间</option>
            <option v-for="r in availableRoomsInBuilding" :key="r.id" :value="r.id">
              {{ r.roomNo }}室（空余 {{ Math.max(0, r.capacity - r.occupiedCount) }} 床）
            </option>
          </select>
        </div>

        <div class="form-row">
          <label>3. 选择空闲床位</label>
          <div v-if="freeBedsInSelectedRoom.length" class="bed-pick-grid">
            <button
              v-for="bed in freeBedsInSelectedRoom"
              :key="bed.id"
              type="button"
              class="bed-chip spring-btn tabular-nums"
              :class="{ active: adjustForm.targetBedNo === bed.bedNo }"
              :aria-pressed="adjustForm.targetBedNo === bed.bedNo"
              @click="adjustForm.targetBedNo = bed.bedNo"
            >
              {{ bed.bedNo }}号床 (空闲)
            </button>
          </div>
          <div v-else class="no-bed-tip">该房间暂无空闲床位，请切换其他房间</div>
        </div>
      </template>

      <div class="form-row">
        <label for="adj-reason">{{ adjustForm.applyType === 'CHECKOUT' ? '退宿离校事由（如实习、休学、毕业）' : '4. 申请原因说明' }}</label>
        <textarea id="adj-reason" v-model="adjustForm.reason" rows="2" class="inp" placeholder="请说明原因..."></textarea>
      </div>

      <button
        type="button"
        class="sheet-submit spring-btn"
        :disabled="submitting || (adjustForm.applyType === 'ADJUST' && !freeBedsInSelectedRoom.length)"
        @click="submitAdjust"
      >
        {{ submitting ? '正在提交...' : (adjustForm.applyType === 'CHECKOUT' ? '提交退宿离校申请' : '提交调宿申请') }}
      </button>
    </BottomSheet>

    <!-- 3. 访客电子通行码预约抽屉 -->
    <BottomSheet v-model="visitorSheet" title="预约访客电子通行码">
      <div class="form-row">
        <label for="vis-name">访客姓名</label>
        <input id="vis-name" v-model="visitorForm.visitorName" class="inp" placeholder="如：林国强" />
      </div>
      <div class="form-row">
        <label for="vis-phone">访客手机号</label>
        <input id="vis-phone" v-model="visitorForm.visitorPhone" class="inp tabular-nums" placeholder="请输入联系电话" />
      </div>
      <div class="form-row">
        <label for="vis-rel">与学生关系</label>
        <input id="vis-rel" v-model="visitorForm.relation" class="inp" placeholder="如：家长 / 同学" />
      </div>
      <div class="form-row">
        <label for="vis-reason">来访事由</label>
        <input id="vis-reason" v-model="visitorForm.visitReason" class="inp" placeholder="如：周末探望送生活用品" />
      </div>
      <button type="button" class="sheet-submit spring-btn" :disabled="submitting" @click="submitVisitor">
        {{ submitting ? '正在生成...' : '生成访客动态绿码' }}
      </button>
    </BottomSheet>

    <!-- 4. 晚归请假报备抽屉 -->
    <BottomSheet v-model="lateSheet" title="晚归请假报备">
      <div class="form-row">
        <label for="late-reason">晚归事由说明（23:00门禁稽核将自动比对报备记录免予违纪扣分）</label>
        <textarea id="late-reason" v-model="lateReason" rows="3" class="inp"></textarea>
      </div>
      <button type="button" class="sheet-submit spring-btn" :disabled="submitting" @click="submitLateReport">
        {{ submitting ? '正在提交...' : '提交晚归报备' }}
      </button>
    </BottomSheet>

    <!-- 5. 违纪/晚归在线申诉抽屉 -->
    <BottomSheet v-model="appealSheet" title="晚归/违纪扣分申诉">
      <div class="form-row">
        <label for="appeal-reason">申诉理由与证明说明（宿管核销后自动恢复寝室文明分）</label>
        <textarea id="appeal-reason" v-model="appealReason" rows="3" class="inp" placeholder="请说明具体情况，如导师实验室科研、校医院急诊等..."></textarea>
      </div>
      <button type="button" class="sheet-submit spring-btn" :disabled="submitting" @click="submitAppeal">
        {{ submitting ? '正在提交...' : '提交申诉审核' }}
      </button>
    </BottomSheet>

    <!-- 6. 访客动态电子绿码大屏亮码凭证（真实可扫描 ISO/IEC 18004 二维码） -->
    <BottomSheet v-model="qrDetailSheet" title="访客电子通行绿码凭证">
      <div v-if="activeVisitor" class="qr-pass-modal">
        <div class="qr-pass-badge" :class="{ expired: activeVisitor.status !== 0 }">
          {{ activeVisitor.status === 0 ? '● 绿码有效 · 准许通行' : '已签离失效' }}
        </div>
        <div class="qr-canvas-wrap" :class="{ expired: activeVisitor.status !== 0 }">
          <svg
            class="large-qr"
            :viewBox="getRealQrSpec(activeVisitor).viewBox"
            shape-rendering="crispEdges"
            role="img"
            aria-label="访客电子通行真实二维码"
          >
            <rect width="100%" height="100%" fill="#ffffff" />
            <path :d="getRealQrSpec(activeVisitor).path" />
          </svg>
        </div>
        <div class="qr-scan-tip">支持使用手机相机或微信「扫一扫」直接识别核验</div>
        <div class="qr-code-num tabular-nums">{{ activeVisitor.passCode || `VP-80${activeVisitor.id}` }}</div>
        <div class="qr-info-list tabular-nums">
          <div><span>访客姓名：</span><b>{{ activeVisitor.visitorName }} ({{ activeVisitor.relation }})</b></div>
          <div><span>访问寝室：</span><b>{{ dash.building?.buildingName || '公寓' }} · {{ activeVisitor.roomNo }}室</b></div>
          <div><span>来访事由：</span><b>{{ activeVisitor.visitReason }}</b></div>
          <div><span>生效时间：</span><b>{{ formatDateTime(activeVisitor.enterTime) }}</b></div>
        </div>
        <button type="button" class="sheet-submit spring-btn" @click="qrDetailSheet = false">
          关闭凭证
        </button>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import QRCode from 'qrcode'
import api from '../api'
import BottomSheet from './BottomSheet.vue'

const props = defineProps({
  dash: { type: Object, default: () => ({}) },
  initialAction: { type: String, default: '' }
})
const emit = defineEmits(['refresh', 'toast', 'clear-action'])

const isAllocated = computed(() => Boolean(props.dash.room && props.dash.bed))
const isLowBalance = computed(() => {
  if (!props.dash.room) return false
  return Number(props.dash.room.electricBalance || 0) < 30 || Number(props.dash.room.waterBalance || 0) < 15
})
const recommendedRooms = computed(() => props.dash.recommendedRooms || [])

const subTab = ref('bills')
const focusSubTab = (tab) => {
  subTab.value = tab
  nextTick(() => document.getElementById(`utility-tab-${tab}`)?.focus())
}
const submitting = ref(false)

const buildings = ref([])
const allRooms = ref([])
const selectedBuildingId = ref(1)

const rechargeSheet = ref(false)
const rechargeTarget = ref('ELECTRIC')
const rechargeAmount = ref(50)

const adjustSheet = ref(false)
const adjustForm = reactive({
  applyType: 'ADJUST',
  targetRoomId: 3,
  targetBedNo: 1,
  reason: '备战考研需要早睡安静作息，申请调入作息相近的寝室'
})

const visitorSheet = ref(false)
const visitorForm = reactive({
  visitorName: '林国强',
  visitorPhone: '13812349876',
  idCardMask: '330102********2819',
  relation: '家长',
  visitReason: '周末父母来校探望送换季被褥'
})

const lateSheet = ref(false)
const lateReason = ref('在学院实验室赶竞赛集训，预计23:35返回寝室，特此报备')

const appealSheet = ref(false)
const appealRecordId = ref(null)
const appealReason = ref('当晚在计算机学院实验室参加科研攻关，附导师证明申请核销晚归扣分')

const qrDetailSheet = ref(false)
const activeVisitor = ref(null)

const formatMoney = (val) => {
  return `¥${Number(val || 0).toFixed(2)}`
}

const formatDateTime = (val) => {
  if (!val) return '-'
  return String(val).replace('T', ' ').substring(0, 16)
}

// 生成真实可被手机相机/微信扫描识别的 ISO/IEC 18004 标准二维码矢量矩阵
const qrCache = new Map()
const getRealQrSpec = (v) => {
  if (!v) return { viewBox: '0 0 29 29', path: '' }
  const code = v.passCode || `VP-80${v.id || 1}`
  const statusStr = v.status === 0 ? '绿码有效(准予通行)' : '已签离失效'
  const building = props.dash.building?.buildingName || v.buildingName || '学生公寓'
  const student = props.dash.user?.realName || v.visitStudentName || '在校生'
  const payload = [
    '【智慧寓管·电子通行证】',
    `通行码:${code}`,
    `状态:${statusStr}`,
    `访客:${v.visitorName || '-'}(${v.relation || '亲友'})`,
    `受访:${building}${v.roomNo || ''}室 ${student}`,
    `事由:${v.visitReason || '-'}`,
    `登记:${formatDateTime(v.enterTime)}`
  ].join('\n')

  if (qrCache.has(payload)) {
    return qrCache.get(payload)
  }

  const qr = QRCode.create(payload, { errorCorrectionLevel: 'M' })
  const size = qr.modules.size
  const data = qr.modules.data
  const margin = 2
  const totalSize = size + margin * 2
  let path = ''
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (data[y * size + x]) {
        path += `M${x + margin},${y + margin}h1v1h-1z`
      }
    }
  }
  const spec = {
    viewBox: `0 0 ${totalSize} ${totalSize}`,
    path
  }
  qrCache.set(payload, spec)
  return spec
}

const openQrDetail = (v) => {
  activeVisitor.value = v
  qrDetailSheet.value = true
}

const openAppealSheet = (d) => {
  appealRecordId.value = d.id
  appealSheet.value = true
}

const submitAppeal = async () => {
  if (!appealReason.value.trim()) {
    emit('toast', { msg: '请填写申诉原因说明', type: 'warning' })
    return
  }
  submitting.value = true
  try {
    await api.post('/affair/discipline-appeal', {
      id: appealRecordId.value,
      appealReason: appealReason.value
    })
    appealSheet.value = false
    emit('toast', { msg: '申诉已提交宿管中心审核', type: 'success' })
    emit('refresh')
  } finally {
    submitting.value = false
  }
}

const applyRecommendedRoom = (rec) => {
  selectedBuildingId.value = rec.buildingId
  adjustForm.targetRoomId = rec.roomId
  adjustForm.targetBedNo = rec.recommendedBedNo
  adjustForm.reason = `系统六维算法推荐【${rec.buildingName}${rec.roomNo}室】（契合度${rec.matchScore}%，${rec.styleTag}），申请调入该寝室`
}

const availableRoomsInBuilding = computed(() => {
  return allRooms.value.filter(
    r => r.buildingId === selectedBuildingId.value && (r.capacity - r.occupiedCount) > 0 && r.id !== props.dash.room?.id
  )
})

const freeBedsInSelectedRoom = computed(() => {
  const room = allRooms.value.find(r => r.id === adjustForm.targetRoomId)
  if (!room || !room.beds) return []
  return room.beds.filter(b => b.status === 0)
})

const formatRoomDisplay = (roomId, bedNo) => {
  const r = allRooms.value.find(rm => rm.id === roomId)
  if (r) {
    return `${r.buildingName} ${r.roomNo}室 (${bedNo}号床)`
  }
  return `目标房间#${roomId} (${bedNo}号床)`
}

const loadDormOptions = async () => {
  const [bRes, rRes] = await Promise.all([
    api.get('/dorm/buildings'),
    api.get('/dorm/rooms')
  ])
  buildings.value = bRes.data || []
  allRooms.value = rRes.data || []
  syncAdjustSelection()
}

const syncAdjustSelection = () => {
  if (recommendedRooms.value.length) {
    applyRecommendedRoom(recommendedRooms.value[0])
    return
  }
  if (props.dash.building?.id) {
    selectedBuildingId.value = props.dash.building.id
  } else if (buildings.value.length) {
    selectedBuildingId.value = buildings.value[0].id
  }
  onBuildingSelectChange()
}

const onBuildingSelectChange = () => {
  const rooms = availableRoomsInBuilding.value
  adjustForm.targetRoomId = rooms.length ? rooms[0].id : null
  onRoomSelectChange()
}

const onRoomSelectChange = () => {
  const beds = freeBedsInSelectedRoom.value
  adjustForm.targetBedNo = beds.length ? beds[0].bedNo : null
}

const openSheetWithCheck = (type) => {
  if (!isAllocated.value) {
    emit('toast', { msg: '分配寝室后方可办理该项业务', type: 'info' })
    return
  }
  if (type === 'recharge') rechargeSheet.value = true
  if (type === 'adjust') {
    adjustForm.applyType = 'ADJUST'
    syncAdjustSelection()
    adjustSheet.value = true
  }
  if (type === 'visitor') visitorSheet.value = true
  if (type === 'late') lateSheet.value = true
}

watch(
  () => props.initialAction,
  (act) => {
    if (act === 'visitor') {
      subTab.value = 'affair'
      emit('clear-action')
    } else if (act === 'bills') {
      subTab.value = 'bills'
      emit('clear-action')
    }
  },
  { immediate: true }
)

const payBill = async (bill) => {
  submitting.value = true
  try {
    await api.post('/utility/pay-or-recharge', {
      roomId: props.dash.room?.id || 1,
      billId: bill.id
    })
    emit('toast', { msg: `${bill.billMonth} 账单已抵扣余额缴清`, type: 'success' })
    emit('refresh')
  } finally {
    submitting.value = false
  }
}

const submitRecharge = async () => {
  submitting.value = true
  try {
    const isElec = rechargeTarget.value === 'ELECTRIC'
    await api.post('/utility/pay-or-recharge', {
      roomId: props.dash.room?.id || 1,
      electricRecharge: isElec ? rechargeAmount.value : 0,
      waterRecharge: isElec ? 0 : rechargeAmount.value
    })
    rechargeSheet.value = false
    emit('toast', { msg: `已成功充值${isElec ? '电费' : '水费'} ¥${rechargeAmount.value}.00`, type: 'success' })
    emit('refresh')
  } finally {
    submitting.value = false
  }
}

const submitAdjust = async () => {
  submitting.value = true
  try {
    const isCheckout = adjustForm.applyType === 'CHECKOUT'
    await api.post('/affair/adjust-apply', {
      studentId: props.dash.user?.id || 10,
      applyType: adjustForm.applyType,
      oldRoomId: props.dash.room?.id || 1,
      oldBedNo: props.dash.bed?.bedNo || 1,
      targetRoomId: isCheckout ? null : adjustForm.targetRoomId,
      targetBedNo: isCheckout ? null : adjustForm.targetBedNo,
      reason: adjustForm.reason
    })
    adjustSheet.value = false
    subTab.value = 'affair'
    emit('toast', { msg: isCheckout ? '退宿申请已提交宿管审批' : '调宿申请已提交宿管审批', type: 'success' })
    emit('refresh')
  } finally {
    submitting.value = false
  }
}

const submitVisitor = async () => {
  if (!visitorForm.visitorName.trim()) {
    emit('toast', { msg: '请填写访客姓名', type: 'warning' })
    return
  }
  submitting.value = true
  try {
    await api.post('/affair/visitor', {
      visitStudentId: props.dash.user?.id || 10,
      buildingId: props.dash.building?.id || 1,
      roomNo: props.dash.room?.roomNo || '301',
      ...visitorForm
    })
    visitorSheet.value = false
    subTab.value = 'affair'
    emit('toast', { msg: '访客动态电子通行绿码已生成', type: 'success' })
    emit('refresh')
  } finally {
    submitting.value = false
  }
}

const submitLateReport = async () => {
  submitting.value = true
  try {
    await api.post('/affair/discipline', {
      studentId: props.dash.user?.id || 10,
      roomId: props.dash.room?.id || 1,
      recordType: 'LATE_RETURN',
      deductScore: 2,
      description: `[学生报备] ${lateReason.value}`
    })
    lateSheet.value = false
    subTab.value = 'affair'
    emit('toast', { msg: '晚归报备已录入，23:00门禁将自动豁免违规扣分', type: 'success' })
    emit('refresh')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadDormOptions()
})
</script>

<style scoped>
.balance-card { background: var(--bg-surface); color: var(--text-primary); border-radius: var(--radius-card); padding: 23px; margin-bottom: 22px; }
.b-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding-bottom: 21px;
  margin-bottom: 19px;
  border-bottom: 1px solid var(--border-subtle);
}
.b-label { color: var(--text-muted); font-size: 12px; }
.b-val { font-size: 28px; line-height: 1.4; color: var(--text-primary); font-weight: 500; letter-spacing: -.8px; margin-top: 5px; }
.b-val.low-text { color: var(--status-danger); }
.b-val.pending-val {
  font-size: 18px;
  color: var(--text-muted);
}
.low-balance-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  background: var(--status-warning-bg);
  color: var(--status-warning);
  padding: 9px 12px;
  border-radius: 12px;
  font-size: 12px;
  margin-bottom: 16px;
}
.banner-btn {
  border: 0;
  background: var(--status-warning);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 99px;
  flex-shrink: 0;
}
.btn-group {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.act-btn {
  border: none;
  padding: 0;
  border-radius: 14px;
  font-size: 12px;
  font-weight: 500;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: 0;
  min-height: 72px;
}
.act-icon { display: grid; place-items: center; width: 43px; height: 43px; flex: 0 0 auto; border-radius: 14px; color: var(--accent-primary); background: var(--bg-canvas); }
.act-icon svg { width: 20px; height: 20px; }
.btn-sub-tag {
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.2);
  font-weight: 500;
}
.act-btn.primary {
  background: transparent;
  color: var(--text-primary);
}
.act-btn.secondary { background: transparent; color: var(--text-primary); }

.sub-tabs { display: flex; gap: 3px; padding: 4px; background: var(--bg-muted); border-radius: 12px; margin-bottom: 19px; }
.sub-pill {
  flex: 1;
  border: none;
  background: transparent;
  text-align: center;
  padding: 8px 0;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
}
.sub-pill.active {
  background: var(--bg-surface);
  color: var(--accent-primary);
}

.sec-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.sec-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.mini-add-btn {
  border: 1px solid var(--border-subtle);
  background: var(--accent-subtle);
  color: var(--accent-primary);
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 99px;
}
.section-gap {
  margin-top: 16px;
}
.empty-tip {
  background: var(--bg-surface);
  border: 0;
  padding: 18px;
  border-radius: var(--radius-card);
  font-size: 14px;
  color: var(--text-muted);
  text-align: center;
}
.empty-action {
  margin-top: 10px;
  border: 1px solid var(--border-subtle);
  background: var(--accent-subtle);
  color: var(--accent-primary);
  padding: 6px 14px;
  border-radius: 99px;
  font-size: 13px;
  font-weight: 500;
}

.pass-card {
  width: 100%;
  text-align: left;
  background: var(--bg-surface);
  border: 0;
  border-radius: 22px;
  padding: 18px 20px;
  margin-bottom: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  color: var(--text-primary);
}
.pass-left { min-width: 0; overflow-wrap: anywhere; }
.p-name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.rel {
  background: var(--accent-subtle);
  color: var(--accent-primary);
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}
.code-tag {
  font-size: 11px;
  color: #15803d;
  background: var(--status-success-bg);
  padding: 1px 6px;
  border-radius: 4px;
}
.p-meta,
.p-time {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 4px;
}
.pass-right {
  text-align: center;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}
.mini-qr {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  border: 1.5px solid #bbf7d0;
  fill: #15803d;
  overflow: hidden;
}
.mini-qr.expired {
  border-color: var(--border-strong);
  fill: #64748b;
}
.pass-status {
  font-size: 11px;
  color: var(--text-muted);
}
.pass-status.active {
  color: var(--status-success);
  font-weight: 600;
}

.adjust-item {
  background: var(--bg-surface);
  border: 0;
  border-radius: var(--radius-card);
  padding: 18px;
  margin-bottom: 10px;
  font-size: 14px;
  overflow-wrap: anywhere;
}
.a-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.a-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--status-warning-bg);
  color: var(--status-warning);
  font-weight: 500;
}
.a-badge.APPROVED {
  background: var(--status-success-bg);
  color: #15803d;
}
.a-badge.REJECTED { background: var(--status-danger-bg); color: var(--status-danger); }
.a-reason {
  color: var(--text-secondary);
  font-size: 13px;
}
.a-reply {
  color: var(--accent-primary);
  margin-top: 4px;
  font-size: 13px;
  font-weight: 600;
}
.deduct {
  color: var(--status-danger);
  font-weight: 500;
}
.appeal-bar {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--border-subtle);
}
.appeal-btn {
  border: 0;
  background: transparent;
  color: var(--accent-primary);
  font-size: 12px;
  font-weight: 500;
  padding: 0;
}

.bill-card { background: var(--bg-surface); border: 0; border-radius: var(--radius-card); padding: 21px; margin-bottom: 17px; }
.bill-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.month {
  font-size: 15px;
  font-weight: 600;
}
.paid {
  color: var(--status-success);
  font-size: 13px;
  font-weight: 500;
}
.unpaid {
  color: var(--status-danger);
  font-size: 13px;
  font-weight: 500;
}
.abnormal-banner {
  background: var(--status-danger-bg);
  border: 1px solid #fecaca;
  color: var(--status-danger);
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 13px;
  margin-bottom: 10px;
  line-height: 1.45;
}
.bill-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  font-size: 13px;
  color: var(--text-secondary);
  gap: 8px;
  padding: 15px 0;
  border-top: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
  margin: 14px 0;
}
.bill-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
}
.price {
  color: var(--text-primary);
  font-size: 16px;
}
.pay-btn {
  background: var(--accent-subtle);
  color: var(--accent-primary);
  border: none;
  padding: 7px 15px;
  border-radius: 99px;
  font-size: 13px;
  font-weight: 500;
}

.type-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  background: var(--bg-muted);
  padding: 4px;
  border-radius: 12px;
  margin-bottom: 16px;
}
.type-pill {
  border: 0;
  background: transparent;
  padding: 8px 0;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}
.type-pill.active {
  background: var(--bg-surface);
  color: var(--accent-primary);
  font-weight: 600;
}

.rec-box {
  margin-bottom: 14px;
}
.rec-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--accent-primary);
  margin-bottom: 6px;
}
.rec-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.rec-card {
  width: 100%;
  text-align: left;
  border: 1px solid var(--border-subtle);
  background: var(--bg-canvas);
  border-radius: 12px;
  padding: 10px 12px;
  color: var(--text-primary);
}
.rec-card.active {
  border-color: var(--accent-primary);
  background: var(--accent-subtle);
}
.rec-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.rec-score {
  color: var(--status-success);
  font-weight: 600;
  font-size: 12px;
}
.rec-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 3px;
}

.qr-pass-modal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.qr-pass-badge {
  font-size: 13px;
  font-weight: 600;
  color: #15803d;
  background: var(--status-success-bg);
  padding: 5px 14px;
  border-radius: 99px;
}
.qr-pass-badge.expired {
  color: var(--text-muted);
  background: var(--bg-muted);
}
.qr-canvas-wrap {
  padding: 12px;
  border-radius: 20px;
  background: #ffffff;
  border: 3px solid #22c55e;
  box-shadow: 0 8px 24px rgba(21, 128, 61, 0.12);
}
.qr-canvas-wrap.expired {
  border-color: var(--border-strong);
  box-shadow: none;
}
.large-qr {
  width: 188px;
  height: 188px;
  fill: #15803d;
  display: block;
}
.qr-canvas-wrap.expired .large-qr {
  fill: #64748b;
}
.qr-scan-tip {
  font-size: 11px;
  color: var(--text-muted);
}
.qr-code-num {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--text-primary);
}
.qr-info-list {
  width: 100%;
  background: var(--bg-canvas);
  border-radius: 14px;
  padding: 12px 16px;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 6px;
}
.qr-info-list div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
.qr-info-list span {
  color: var(--text-muted);
}

.amount-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}
.amt-chip {
  border: 1px solid var(--border-strong);
  background: var(--bg-surface);
  border-radius: 10px;
  padding: 12px 0;
  text-align: center;
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary);
}
.amt-chip.active {
  background: var(--accent-subtle);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}

.bed-pick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}
.bed-chip {
  border: 1px solid var(--border-strong);
  background: var(--bg-surface);
  border-radius: 10px;
  padding: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}
.bed-chip.active {
  background: var(--accent-subtle);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
  font-weight: 500;
}
.no-bed-tip {
  font-size: 13px;
  color: var(--status-danger);
  padding: 8px 0;
}

.form-row {
  margin-bottom: 12px;
}
.form-row label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 5px;
}
.inp {
  width: 100%;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  background: var(--bg-surface);
}
.sheet-submit {
  width: 100%;
  background: var(--accent-primary);
  color: #ffffff;
  border: none;
  padding: 13px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
}
.sheet-submit:disabled {
  opacity: 0.6;
}
@media (max-width: 360px) { .balance-card { padding: 20px 17px; }.btn-group { gap: 3px; }.act-btn { font-size: 11px; }.b-val { font-size: 25px; } }
</style>
