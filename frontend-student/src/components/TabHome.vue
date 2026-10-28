<template>
  <div class="tab-home">
    <section v-if="isAllocated" class="residence-card">
      <button type="button" class="room-heading spring-btn" @click="$emit('go-tab', 'habit')">
        <span class="room-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 21V7h7v14M11 3h7v18M2 21h20M7 10v1m0 3v1m7-8v1m0 3v1m0 3v1" /></svg></span>
        <span class="room-heading-copy"><h2>{{ dash.room.roomNo }} 室 · {{ dash.bed?.bedNo }} 号床</h2><span>{{ dash.building?.buildingName }}</span></span>
        <span class="room-chevron" aria-hidden="true">›</span>
      </button>
      <div class="home-balances">
        <button type="button" class="balance-item spring-btn" @click="handleUtilityClick('bills')"><span>电费余额</span><strong class="tabular-nums">{{ dash.room?.electricBalance != null ? formatMoney(dash.room.electricBalance) : '暂无数据' }}</strong></button>
        <button type="button" class="balance-item spring-btn" @click="handleUtilityClick('bills')"><span>水费余额</span><strong class="tabular-nums">{{ dash.room?.waterBalance != null ? formatMoney(dash.room.waterBalance) : '暂无数据' }}</strong></button>
      </div>
    </section>
    <section v-else class="unalloc-card">
      <div class="unalloc-top"><span>新生入宿</span><span>{{ hasProfile ? '问卷已提交' : '待填写问卷' }}</span></div>
      <h2>{{ hasProfile ? '正在等待分寝' : '从了解你的习惯开始' }}</h2>
      <p>{{ hasProfile ? '宿管中心将根据生活习惯，为你安排合适的寝室与室友。' : '填写一份生活习惯问卷，帮助我们为你找到作息相近的室友。' }}</p>
      <div class="progress-steps"><div :class="{ done: hasProfile, active: !hasProfile }"><span>{{ hasProfile ? '✓' : '1' }}</span>填写问卷</div><i :class="{ done: hasProfile }"></i><div :class="{ active: hasProfile }"><span>2</span>等待分寝</div><i></i><div><span>3</span>入住</div></div>
      <button type="button" class="unalloc-btn spring-btn" @click="$emit('go-tab', 'habit')">{{ hasProfile ? '查看习惯问卷' : '开始填写问卷' }}<span aria-hidden="true">›</span></button>
      <div class="account-pending">水电账户待开通 · 分寝后自动启用</div>
    </section>
    <!-- 快捷入口（统一 SVG 线性图标，“访客绿码”直接进入访客功能） -->
    <div class="quick-bar">
      <button type="button" class="q-item spring-btn" @click="$emit('go-tab', 'habit')">
        <span class="q-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2l8.5 5v10L12 22l-8.5-5V7L12 2z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </span>
        <span>习惯问卷</span>
      </button>

      <button type="button" class="q-item spring-btn" @click="handleRepairClick">
        <span class="q-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
          </svg>
        </span>
        <span>故障报修</span>
      </button>

      <button type="button" class="q-item spring-btn" @click="handleUtilityClick('bills')">
        <span class="q-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
          </svg>
        </span>
        <span>水电缴费</span>
      </button>

      <button type="button" class="q-item spring-btn" @click="handleVisitorClick">
        <span class="q-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="4" width="18" height="16" rx="2" />
            <circle cx="9" cy="10" r="2" />
            <path d="M15 8h2M15 12h2M7 16h10" />
          </svg>
        </span>
        <span>访客绿码</span>
      </button>
    </div>

    <button
      v-if="pendingRateCount > 0"
      type="button"
      class="pending-rate-banner spring-btn"
      @click="$emit('go-tab', 'repair')"
    >
      您有 {{ pendingRateCount }} 单报修待评价，点击去评价 ›
    </button>

    <section v-if="isAllocated && dash.roomRadar?.roommates?.length" class="section-box">
      <div class="sec-head"><h2>住在一起</h2><span>{{ dash.roomRadar.roommates.length }} 人寝</span></div>
      <div class="group-surface">
        <button type="button" class="roommates-summary spring-btn" :aria-expanded="showRoommates" @click="showRoommates = !showRoommates"><span class="mate-avatar">{{ (dash.roomRadar.roommates.find(m => m.studentId !== dash.user?.id)?.realName || '友').slice(0, 1) }}</span><span class="summary-copy"><b>与你作息相近的室友</b><small>室友契合度 {{ dash.room.harmonyScore ?? '暂无' }}{{ dash.room.harmonyScore != null ? '%' : '' }} · 文明分 {{ Number(dash.room.civilizedScore || 0).toFixed(1) }}</small></span><span class="group-chevron" :class="{ expanded: showRoommates }" aria-hidden="true">›</span></button>
        <div v-if="showRoommates" class="mate-list">
          <div v-for="m in dash.roomRadar.roommates" :key="m.studentId" class="mate-item"><span class="bed-badge">{{ m.bedNo }} 床</span><div class="mate-info"><b>{{ m.realName }}<small v-if="m.studentId === dash.user?.id">我</small></b><p>{{ m.profile?.hobbyTags || m.className }}</p></div><span class="m-habit">{{ scheduleLabel(m.profile?.scheduleScore) }}</span></div>
          <button type="button" class="compare-link spring-btn" @click="$emit('go-tab', 'habit')">查看生活习惯对比<span aria-hidden="true">›</span></button>
        </div>
      </div>
    </section>
    <section class="section-box">
      <div class="sec-head"><h2>校园通知</h2><span>{{ (dash.notices || []).length }} 则消息</span></div>
      <div class="group-surface notice-group">
        <p v-if="!(dash.notices || []).length" class="empty-notices">暂时没有新的校园通知。</p>
        <button v-for="n in dash.notices || []" :key="n.id" type="button" class="notice-row spring-btn" @click="openNoticeDetail(n)"><span class="notice-copy"><b>{{ n.title }}</b><small>{{ n.publisherName }} · {{ formatDateTime(n.createTime).slice(0, 10) }}</small></span><span class="group-chevron" aria-hidden="true">›</span></button>
      </div>
    </section>
    <BottomSheet v-model="noticeSheetVisible" :title="activeNotice?.title || '通知详情'">
      <div v-if="activeNotice" class="notice-detail">
        <div class="nd-meta tabular-nums">
          <span>{{ activeNotice.publisherName }}</span>
          <span>{{ formatDateTime(activeNotice.createTime) }}</span>
        </div>
        <div class="nd-content">{{ activeNotice.content }}</div>
        <button type="button" class="nd-close-btn spring-btn" @click="noticeSheetVisible = false">
          我知道了
        </button>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import BottomSheet from './BottomSheet.vue'

const props = defineProps({
  dash: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['go-tab', 'refresh', 'toast'])

const isAllocated = computed(() => Boolean(props.dash.room && props.dash.bed))
const hasProfile = computed(() => Boolean(props.dash.habitProfile && props.dash.habitProfile.scheduleScore))
const pendingRateCount = computed(() => (props.dash.myRepairs || []).filter(r => r.status === 'COMPLETED').length)

const showRoommates = ref(false)
const noticeSheetVisible = ref(false)
const activeNotice = ref(null)

const formatMoney = (val) => {
  return `¥${Number(val || 0).toFixed(2)}`
}

const formatDateTime = (val) => {
  if (!val) return '近期发布'
  return String(val).replace('T', ' ').substring(0, 16)
}

const scheduleLabel = (score) => {
  if (!score) return '规律作息'
  if (score <= 2) return '早睡型'
  if (score >= 4) return '晚睡型'
  return '规律型'
}

const handleUtilityClick = (subAction = 'bills') => {
  if (!isAllocated.value) {
    emit('toast', { msg: '分配寝室后即可使用水电缴费与充值', type: 'info' })
    return
  }
  emit('go-tab', { tab: 'utility', action: subAction })
}

const handleRepairClick = () => {
  if (!isAllocated.value) {
    emit('toast', { msg: '分配寝室后可提交寝室设施报修', type: 'info' })
    return
  }
  emit('go-tab', 'repair')
}

const handleVisitorClick = () => {
  if (!isAllocated.value) {
    emit('toast', { msg: '分配寝室后可申请访客电子绿码', type: 'info' })
    return
  }
  emit('go-tab', { tab: 'affair', action: 'visitor' })
}

const openNoticeDetail = (n) => {
  activeNotice.value = n
  noticeSheetVisible.value = true
}
</script>

<style scoped>
.residence-card, .unalloc-card { background: var(--bg-surface); border-radius: var(--radius-card); padding: 22px; }
.room-heading { display: flex; align-items: center; gap: 14px; width: 100%; padding: 0; text-align: left; border: 0; background: transparent; color: var(--text-primary); }
.room-icon { width: 43px; height: 43px; display: grid; place-items: center; background: var(--accent-subtle); color: var(--accent-primary); border-radius: 13px; flex-shrink: 0; }
.room-icon svg { width: 24px; height: 24px; }
.room-heading-copy { flex: 1; min-width: 0; }
.room-heading h2 { font-size: 21px; font-weight: 600; letter-spacing: -.65px; line-height: 1.4; }
.room-heading-copy>span { font-size: 12px; color: var(--text-muted); display: block; margin-top: 4px; }
.room-chevron, .group-chevron { color: #a0a0a8; font-size: 23px; font-weight: 400; line-height: 1; }
.home-balances { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 20px; padding-top: 20px; border-top: 1px solid var(--border-subtle); }
.balance-item { display: block; padding: 0; border: 0; background: transparent; color: var(--text-primary); text-align: left; min-width: 0; }
.balance-item>span { font-size: 12px; color: var(--text-muted); }
.balance-item strong { display: block; font-size: 27px; letter-spacing: -.8px; font-weight: 500; margin-top: 5px; line-height: 1.35; overflow-wrap: anywhere; }
.unalloc-top { display: flex; justify-content: space-between; gap: 12px; color: var(--text-muted); font-size: 12px; }
.unalloc-card h2 { font-size: 23px; font-weight: 600; letter-spacing: -.6px; margin-top: 15px; }
.unalloc-card>p { font-size: 13px; color: var(--text-muted); margin-top: 8px; line-height: 1.8; }
.progress-steps { display: flex; align-items: center; gap: 8px; margin: 23px 0; }
.progress-steps>div { display: flex; align-items: center; gap: 5px; font-size: 11px; white-space: nowrap; color: var(--text-muted); }
.progress-steps>div>span { display: grid; place-items: center; width: 18px; height: 18px; border-radius: 50%; font-size: 10px; background: var(--bg-muted); }
.progress-steps>div.active { color: var(--accent-primary); }.progress-steps>div.active>span { color: white; background: var(--accent-primary); }.progress-steps>div.done>span { color: var(--status-success); background: var(--status-success-bg); }
.progress-steps>i { height: 1px; flex: 1; background: var(--border-subtle); }.progress-steps>i.done { background: #bad8c5; }
.unalloc-btn { border: 0; border-radius: 12px; width: 100%; background: var(--accent-primary); color: white; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; font-size: 14px; font-weight: 500; }
.account-pending { color: var(--text-muted); font-size: 11px; text-align: center; margin-top: 13px; }
.quick-bar { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 25px 0 27px; }
.q-item { border: 0; background: transparent; color: var(--text-primary); display: flex; flex-direction: column; align-items: center; gap: 9px; font-size: 12px; min-height: 65px; }
.q-icon { width: 45px; height: 45px; border-radius: 15px; background: var(--bg-surface); color: var(--accent-primary); display: grid; place-items: center; }
.q-icon svg { width: 21px; height: 21px; stroke-width: 1.7; }
.section-box { margin-bottom: 25px; }
.sec-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; padding: 0 2px; margin-bottom: 10px; }
.sec-head h2 { font-size: 18px; font-weight: 600; letter-spacing: -.4px; }.sec-head>span { font-size: 12px; color: var(--text-muted); }
.group-surface { background: var(--bg-surface); border-radius: 22px; padding: 0 18px; }
.roommates-summary { width: 100%; display: flex; align-items: center; gap: 12px; padding: 17px 0; border: 0; background: transparent; text-align: left; color: var(--text-primary); }
.mate-avatar { display: grid; place-items: center; width: 34px; height: 34px; background: var(--bg-canvas); border-radius: 50%; font-size: 13px; color: var(--text-muted); flex-shrink: 0; }
.summary-copy { flex: 1; min-width: 0; }.summary-copy b { font-size: 14px; font-weight: 500; }.summary-copy small { display: block; margin-top: 4px; font-size: 11px; color: var(--text-muted); }
.group-chevron { transition: transform 180ms ease; flex-shrink: 0; }.group-chevron.expanded { transform: rotate(90deg); }
.mate-list { padding-bottom: 7px; border-top: 1px solid var(--border-subtle); }
.mate-item { display: flex; align-items: center; gap: 10px; padding: 13px 0; border-bottom: 1px solid var(--border-subtle); }
.bed-badge { color: var(--text-muted); font-size: 11px; white-space: nowrap; }.mate-info { flex: 1; min-width: 0; }.mate-info b { font-size: 13px; font-weight: 500; }.mate-info b small { margin-left: 7px; font-size: 10px; color: var(--text-muted); }.mate-info p { font-size: 11px; color: var(--text-muted); margin-top: 3px; overflow-wrap: anywhere; }.m-habit { color: var(--text-muted); font-size: 11px; white-space: nowrap; }
.compare-link { width: 100%; border: 0; background: transparent; color: var(--accent-primary); padding: 14px 0 10px; font-size: 12px; display: flex; align-items: center; justify-content: space-between; }
.notice-row { display: flex; align-items: center; gap: 14px; width: 100%; border: 0; border-bottom: 1px solid var(--border-subtle); padding: 17px 0; background: transparent; color: var(--text-primary); text-align: left; }.notice-row:last-child { border: 0; }
.notice-copy { flex: 1; min-width: 0; }.notice-copy b { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 13px; font-weight: 500; line-height: 1.65; }.notice-copy small { display: block; margin-top: 5px; font-size: 11px; color: var(--text-muted); }.empty-notices { padding: 20px 0; color: var(--text-muted); font-size: 13px; }
.nd-meta { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; font-size: 12px; color: var(--text-muted); margin-bottom: 20px; }.nd-content { font-size: 15px; line-height: 1.9; color: var(--text-secondary); white-space: pre-wrap; overflow-wrap: anywhere; }.nd-close-btn { margin-top: 25px; width: 100%; padding: 13px; color: white; border: 0; border-radius: 12px; background: var(--accent-primary); }
@media (max-width: 360px) { .residence-card, .unalloc-card { padding: 19px 17px; }.room-heading { gap: 10px; }.room-heading h2 { font-size: 19px; }.balance-item strong { font-size: 24px; }.q-item { font-size: 11px; }.progress-steps { gap: 5px; }.progress-steps>div { gap: 4px; } }
.pending-rate-banner { width: 100%; margin: -8px 0 22px; padding: 12px 14px; border: 0; border-radius: 14px; background: #fff7e8; color: #9a6700; font-size: 13px; text-align: left; font-weight: 500; }
</style>
