<template>
  <div v-loading="loading" class="page-stack">
    <div v-if="loadError" class="error-banner" role="alert">
      <span>水电账单加载失败，请重试。</span>
      <el-button type="danger" plain size="small" @click="loadBills">重试</el-button>
    </div>

    <section class="surface-card workspace-panel">
      <div class="workspace-heading">
        <div><h2>水电账单与余额联动</h2><p>查看每月阶梯用量、账户余额抵扣与需要核查的能耗异常。</p></div>
        <div class="workspace-actions">
          <el-popover placement="bottom-start" :width="340" trigger="click"><template #reference><el-button text size="small">计费规则</el-button></template><div class="rule-popover-content"><h4>阶梯计价与异常监测</h4><ul><li>电费：每月免额 20 度，20–100 度每度 0.60 元，超出 100 度每度 0.85 元。</li><li>水费：每月免额 4 吨，4–15 吨每吨 3 元，超出 15 吨每吨 4.50 元。</li><li>用量超过楼栋均值 1.5 倍或环比激增时，系统会标记异常；缴纳账单时自动联动抵扣寝室水电余额。</li></ul></div></el-popover>
          <el-button plain icon="Download" @click="exportUtilityCsv">导出账单</el-button><el-button type="primary" icon="Plus" @click="entryVisible = true">录入抄表</el-button>
        </div>
      </div>
      <div class="bill-filters"><el-checkbox v-model="onlyAbnormal" @change="loadBills">仅看能耗异常账单</el-checkbox><span>{{ bills.length }} 条账单</span></div>
      <div v-if="!loading && !bills.length" class="workspace-empty"><el-icon :size="22"><Document /></el-icon><p>暂无符合条件的账单</p><span>可切换筛选条件或录入新的抄表记录。</span></div>
      <ul v-else class="bill-list" aria-label="水电账单列表">
        <li v-for="bill in bills" :key="bill.id" class="bill-row">
          <div class="bill-heading">
            <span class="bill-icon"><el-icon><Odometer /></el-icon></span>
            <div class="bill-room">
              <strong>{{ bill.buildingName }} · {{ bill.roomNo }}室</strong>
              <small class="tabular-nums">
                {{ bill.billMonth }} 月账单
                <span v-if="bill.abnormalFlag === 1" class="abnormal-dot">· 用量突增预警</span>
                <span v-if="bill.lowBalanceFlag === 1" class="low-bal-dot">· 余额不足预警(电¥{{ bill.roomElectricBalance }})</span>
              </small>
            </div>
          </div>
          <div class="bill-usage"><div><span>用电</span><strong>{{ bill.electricUsed }} 度</strong><small>{{ formatMoney(bill.electricCost) }}</small></div><div><span>用水</span><strong>{{ bill.waterUsed }} 吨</strong><small>{{ formatMoney(bill.waterCost) }}</small></div></div>
          <div class="bill-amount"><span>账单总额</span><strong>{{ formatMoney(bill.totalCost) }}</strong><small>人均 {{ formatMoney(bill.perCapitaCost) }}</small></div>
          <div class="bill-action"><span :class="bill.payStatus === 1 ? 'settled' : 'unsettled'">{{ bill.payStatus === 1 ? '已缴清' : '待缴纳' }}</span><el-button v-if="bill.payStatus === 0" type="primary" plain size="small" @click="handlePayBill(bill)">扣余额核销</el-button><span v-else class="settled-note">已核销</span></div>
          <details v-if="bill.abnormalFlag === 1" class="bill-warning"><summary>查看异常原因</summary><p>{{ bill.abnormalReason }}</p></details>
        </li>
      </ul>
    </section>

    <!-- 抄表录入弹窗 -->
    <el-dialog v-model="entryVisible" title="月度水电抄表录入" width="440px">
      <el-form :model="entryForm" label-width="110px">
        <el-form-item label="账单月份">
          <el-input v-model="entryForm.billMonth" placeholder="如 2026-10" />
        </el-form-item>
        <el-form-item label="选择房间">
          <el-select v-model="entryForm.roomId" class="full-width">
            <el-option
              v-for="r in rooms"
              :key="r.id"
              :label="`${r.buildingName} - ${r.roomNo}室`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="本月用电(度)">
          <el-input-number v-model="entryForm.electricUsed" :min="0" :max="1000" :step="10" class="full-width" />
          <div class="field-tip">输入大于 145 度将触发能耗异常预警</div>
        </el-form-item>
        <el-form-item label="本月用水(吨)">
          <el-input-number v-model="entryForm.waterUsed" :min="0" :max="200" :step="2" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="entryVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEntry">核算并保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const formatMoney = (val) => '¥' + Number(val || 0).toFixed(2)

const route = useRoute()
const loading = ref(false)
const loadError = ref(false)
const submitting = ref(false)
const bills = ref([])
const rooms = ref([])
const onlyAbnormal = ref(route.query.abnormal === '1')
const entryVisible = ref(false)

const entryForm = reactive({
  billMonth: '2026-10',
  roomId: 2,
  electricUsed: 185,
  waterUsed: 13
})

const loadBills = async () => {
  loading.value = true
  loadError.value = false
  try {
    const params = onlyAbnormal.value ? { abnormalFlag: 1 } : {}
    const res = await request.get('/utility/bills', { params })
    bills.value = res.data || []
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const loadRooms = async () => {
  const res = await request.get('/dorm/rooms')
  rooms.value = res.data || []
}

const submitEntry = async () => {
  submitting.value = true
  try {
    const res = await request.post('/utility/meter-entry', entryForm)
    ElMessage.success(res.message || '账单已核算保存')
    entryVisible.value = false
    await loadBills()
  } finally {
    submitting.value = false
  }
}

const handlePayBill = async (row) => {
  await request.post('/utility/pay-or-recharge', {
    roomId: row.roomId,
    billId: row.id
  })
  ElMessage.success('账单已联动抵扣寝室水电余额并完成核销')
  loadBills()
}

const exportUtilityCsv = () => {
  window.open('/api/export/utility-bills', '_blank')
}

onMounted(() => {
  loadBills()
  loadRooms()
})
</script>

<style scoped>
.workspace-panel { padding: 28px 30px 18px; }.workspace-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }.workspace-heading h2 { font-size: 20px; font-weight: 600; letter-spacing: -.5px; }.workspace-heading p { margin-top: 5px; font-size: 13px; color: var(--text-muted); }.workspace-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.bill-filters { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 23px 0 10px; color: var(--text-muted); font-size: 12px; }.bill-list { list-style: none; }
.bill-row { display: grid; grid-template-columns: minmax(190px, 1.1fr) minmax(210px, 1fr) minmax(125px, .65fr) auto; align-items: center; gap: 20px; padding: 20px 0; border-top: 1px solid var(--border-subtle); }
.bill-heading { display: flex; align-items: center; gap: 13px; min-width: 0; }.bill-icon { display: grid; place-items: center; flex: 0 0 auto; width: 40px; height: 40px; border-radius: 13px; color: var(--text-secondary); background: var(--bg-canvas); font-size: 18px; }.bill-room { min-width: 0; }.bill-room strong { display: block; font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }.bill-room small { display: block; margin-top: 5px; color: var(--text-muted); font-size: 12px; }.abnormal-dot { color: var(--status-danger); }.low-bal-dot { color: var(--status-warning); }
.bill-usage { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }.bill-usage span, .bill-amount span { display: block; font-size: 11px; color: var(--text-muted); }.bill-usage strong, .bill-amount strong { display: block; font-size: 14px; font-weight: 500; margin-top: 4px; font-variant-numeric: tabular-nums; }.bill-usage small, .bill-amount small { display: block; margin-top: 3px; font-size: 11px; color: var(--text-muted); }.bill-amount strong { font-size: 17px; letter-spacing: -.3px; }
.bill-action { display: flex; align-items: flex-end; flex-direction: column; gap: 7px; font-size: 12px; }.settled { color: var(--status-success); }.unsettled { color: var(--status-warning); }.settled-note { font-size: 12px; color: var(--text-muted); }.bill-warning { grid-column: 1 / -1; font-size: 12px; color: var(--text-muted); }.bill-warning summary { color: var(--status-danger); cursor: pointer; width: fit-content; }.bill-warning p { color: var(--text-secondary); line-height: 1.7; padding-top: 8px; }
.workspace-empty { padding: 44px 20px; text-align: center; color: var(--text-muted); }.workspace-empty p { margin: 8px 0 3px; font-size: 14px; color: var(--text-secondary); }.workspace-empty span { font-size: 12px; }.field-tip { font-size: 12px; color: var(--text-muted); margin-top: 4px; }.full-width { width: 100%; }
@media (max-width: 1050px) { .bill-row { grid-template-columns: minmax(0, 1fr) minmax(150px, .8fr) auto; }.bill-heading { grid-column: 1; }.bill-usage { grid-column: 1; padding-left: 53px; }.bill-amount { grid-column: 2; grid-row: 1 / span 2; }.bill-action { grid-column: 3; grid-row: 1 / span 2; } }
@media (max-width: 600px) { .workspace-panel { padding: 22px 18px 15px; }.workspace-heading { flex-wrap: wrap; }.workspace-actions { width: 100%; }.bill-row { grid-template-columns: minmax(0, 1fr) auto; gap: 15px 10px; }.bill-heading { grid-column: 1 / -1; }.bill-usage { grid-column: 1; padding-left: 0; }.bill-amount { grid-column: 2; grid-row: 2; text-align: right; }.bill-action { grid-column: 1 / -1; grid-row: auto; flex-direction: row; justify-content: space-between; align-items: center; }.bill-warning { grid-column: 1 / -1; } }
</style>
