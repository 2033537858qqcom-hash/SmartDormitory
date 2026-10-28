<template>
  <div class="tab-utility">
    <header class="mobile-page-heading">
      <h1>水电缴费</h1>
      <p>余额、账单与充值，都在这里。</p>
      <div class="demo-banner" role="note">演示模拟：充值/缴费仅本地余额核销，<b>非微信支付</b>、非校园卡实扣。</div>
    </header>

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
        <button type="button" class="act-btn secondary spring-btn" @click="$emit('go-tab', 'affair')">
          <span class="act-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h15m-4-4 4 4-4 4M20 17H5m4-4-4 4 4 4" /></svg></span><span>去办宿务</span>
        </button>
      </div>
    </div>

    <div class="bill-section" role="tabpanel">
      <div class="sec-head-inline"><h3 class="sec-title">月度水电账单</h3><span class="tabular-nums">{{ (dash.bills || []).length }} 份</span></div>
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
          >余额抵扣缴纳</button>
        </div>
      </div>
    </div>

    <BottomSheet v-model="rechargeSheet" title="寝室水电费充值（演示模拟）">
      <p class="demo-sheet-note">本操作为演示环境模拟扣款，将生成 DEMO 流水号；不会调用微信支付或任何真实支付网关。</p>
      <div class="form-row">
        <label>充值账户</label>
        <div class="type-grid">
          <button type="button" class="type-chip spring-btn" :class="{ active: rechargeTarget === 'ELECTRIC' }" @click="rechargeTarget = 'ELECTRIC'">电费账户</button>
          <button type="button" class="type-chip spring-btn" :class="{ active: rechargeTarget === 'WATER' }" @click="rechargeTarget = 'WATER'">水费账户</button>
        </div>
      </div>
      <div class="form-row">
        <label for="recharge-amt">充值金额（元）</label>
        <input id="recharge-amt" v-model.number="rechargeAmount" type="number" min="10" step="10" class="inp tabular-nums" />
      </div>
      <button type="button" class="sheet-submit spring-btn" :disabled="submitting" @click="submitRecharge">
        {{ submitting ? '正在充值...' : '确认充值（演示模拟扣款）' }}
      </button>
    </BottomSheet>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import api from '../api'
import { submitDemoPayment } from '../payment'
import BottomSheet from './BottomSheet.vue'

const props = defineProps({
  dash: { type: Object, default: () => ({}) },
  initialAction: { type: String, default: '' }
})
const emit = defineEmits(['refresh', 'toast', 'clear-action', 'go-tab'])

const isAllocated = computed(() => Boolean(props.dash.room && props.dash.bed))
const isLowBalance = computed(() => {
  if (!props.dash.room) return false
  return Number(props.dash.room.electricBalance || 0) < 30 || Number(props.dash.room.waterBalance || 0) < 15
})

const submitting = ref(false)
const rechargeSheet = ref(false)
const rechargeTarget = ref('ELECTRIC')
const rechargeAmount = ref(50)

const formatMoney = (val) => `¥${Number(val || 0).toFixed(2)}`

const openSheetWithCheck = (kind) => {
  if (!isAllocated.value) {
    emit('toast', { msg: '分配寝室后即可使用水电充值', type: 'info' })
    return
  }
  if (kind === 'recharge') rechargeSheet.value = true
}

watch(() => props.initialAction, (action) => {
  if (!action) return
  if (action === 'bills' || action === 'recharge') {
    openSheetWithCheck(action === 'bills' ? 'recharge' : action)
  } else if (['visitor', 'adjust', 'late', 'appeal'].includes(action)) {
    emit('go-tab', { tab: 'affair', action })
  }
  emit('clear-action')
}, { immediate: true })

const submitRecharge = async () => {
  if (!rechargeAmount.value || rechargeAmount.value < 10) {
    emit('toast', { msg: '请输入不少于 10 元的充值金额', type: 'warning' })
    return
  }
  submitting.value = true
  try {
    const isElec = rechargeTarget.value === 'ELECTRIC'
    const res = await submitDemoPayment(api, sessionStorage, crypto, {
      roomId: props.dash.room?.id || 1,
      electricRecharge: isElec ? rechargeAmount.value : 0,
      waterRecharge: isElec ? 0 : rechargeAmount.value
    })
    rechargeSheet.value = false
    const txn = res.data?.txnNo || res.data?.data?.txnNo
    emit('toast', { msg: `【演示模拟】已充值${isElec ? '电费' : '水费'} ¥${rechargeAmount.value}.00${txn ? ' · 流水 ' + txn : ''}`, type: 'success' })
    emit('refresh')
  } catch (e) {
    emit('toast', { msg: e.response?.data?.message || e.message || '充值失败', type: 'error' })
  } finally {
    submitting.value = false
  }
}

const payBill = async (b) => {
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await submitDemoPayment(api, sessionStorage, crypto, {
      roomId: props.dash.room?.id || 1,
      billId: b.id
    })
    const txn = res.data?.txnNo || res.data?.data?.txnNo
    emit('toast', { msg: `【演示模拟】${b.billMonth} 账单已核销${txn ? ' · 流水 ' + txn : ''}`, type: 'success' })
    emit('refresh')
  } catch (e) {
    emit('toast', { msg: e.response?.data?.message || e.message || '缴费失败', type: 'error' })
  } finally {
    submitting.value = false
  }
}
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

.demo-banner { margin-top: 12px; padding: 10px 12px; border-radius: 12px; background: #fff6e5; color: #9a5b00; font-size: 12px; line-height: 1.5; }
.demo-sheet-note { margin: 0 0 14px; padding: 10px 12px; border-radius: 12px; background: #fff6e5; color: #9a5b00; font-size: 12px; line-height: 1.55; }
</style>
