<template>
  <div class="tab-repair">
    <div v-if="pendingRateCount > 0" class="pending-rate-top">
      有 {{ pendingRateCount }} 单待评价，请尽快完成服务评价
    </div>
    <!-- 顶部发起报修主卡 -->
    <div class="repair-hero">
      <div>
        <h2 class="h-title">寝室故障报修</h2>
        <div class="h-sub">
          {{ dash.room ? `当前寝室：${dash.building?.buildingName} ${dash.room.roomNo}室` : '分配寝室后可在线提交报修' }}
        </div>
      </div>
      <button type="button" class="call-btn spring-btn" @click="openSubmitSheet">
        + 发起报修
      </button>
    </div>

    <!-- 我的报修工单列表 -->
    <div class="order-list">
      <div v-if="!(dash.myRepairs && dash.myRepairs.length)" class="empty-box">
        <div class="empty-title">暂无报修记录</div>
        <div class="empty-desc">寝室设施如遇故障，可随时在线报修并查看处理进度。</div>
        <button type="button" class="empty-cta spring-btn" @click="openSubmitSheet">发起报修</button>
      </div>

      <div v-for="item in dash.myRepairs || []" :key="item.id" class="order-card" :data-message-key="`REPAIR:${item.id}`">
        <div class="o-head">
          <span class="o-no tabular-nums">单号 {{ item.orderNo }}</span>
          <span v-if="item.priority === 'HIGH'" class="prio-high">紧急工单</span>
          <span v-else class="prio-norm">常规</span>
        </div>

        <div class="o-title">{{ item.title }}</div>
        <div class="o-desc">{{ item.description }}</div>

        <!-- 派单与处理信息 + 进度时间线 -->
        <div class="dispatch-trace">
          <div class="t-row">
            <span>维修师傅：<b>{{ item.workerName || '待指派' }}</b></span>
            <span class="status-tag" :class="item.status">{{ statusLabel(item.status) }}</span>
          </div>
          <ol class="progress-timeline" aria-label="报修进度">
            <li :class="{ done: true }">已提交</li>
            <li :class="{ done: ['ASSIGNED','PROCESSING','COMPLETED','RATED'].includes(item.status), current: item.status==='ASSIGNED' }">已派单</li>
            <li :class="{ done: ['PROCESSING','COMPLETED','RATED'].includes(item.status), current: item.status==='PROCESSING' }">维修中</li>
            <li :class="{ done: ['COMPLETED','RATED'].includes(item.status), current: item.status==='COMPLETED' }">待评价</li>
            <li :class="{ done: item.status==='RATED' }">已完结</li>
          </ol>
          <div v-if="item.repairResult" class="t-result">处理反馈：{{ item.repairResult }}</div>
        </div>

        <div v-if="item.status === 'COMPLETED'" class="rate-reminder">维修已完成，请及时评价以便闭环归档</div>

        <div class="o-foot">
          <span class="o-time tabular-nums">{{ formatDateTime(item.createTime) }}</span>
          <button
            v-if="item.status === 'COMPLETED'"
            type="button"
            class="rate-btn spring-btn"
            @click="openRateSheet(item)"
          >
            去评价
          </button>
          <span v-else-if="item.rating" class="stars">
            {{ '★'.repeat(item.rating) }} ({{ item.rating }}星)
          </span>
        </div>
      </div>
    </div>

    <!-- 1. 提交报修工单半屏抽屉 -->
    <BottomSheet v-model="sheetOpen" title="提交故障报修">
      <div class="form-row">
        <label>故障分类</label>
        <div class="type-grid">
          <button
            v-for="t in types"
            :key="t.val"
            type="button"
            class="type-chip spring-btn"
            :class="{ active: form.repairType === t.val }"
            :aria-pressed="form.repairType === t.val"
            @click="form.repairType = t.val"
          >
            {{ t.label }}
          </button>
        </div>
      </div>

      <div class="form-row">
        <label for="repair-title">故障标题</label>
        <input id="repair-title" v-model="form.title" class="inp" placeholder="如：浴室插座跳闸 / 水龙头漏水" />
      </div>

      <div class="form-row">
        <label for="repair-desc">详细说明</label>
        <textarea id="repair-desc" v-model="form.description" class="area" rows="3" placeholder="请描述具体故障情况..."></textarea>
      </div>

      <div class="form-row">
        <label>故障照片（最多3张，演示本地上传）</label>
        <input type="file" accept="image/*" multiple @change="onPickPhotos" />
        <div v-if="photoUrls.length" class="photo-preview">
          <img v-for="(u, i) in photoUrls" :key="i" :src="resolveUrl(u)" alt="故障图" />
        </div>
      </div>

      <p v-if="errorMsg" class="inline-error" role="alert">{{ errorMsg }}</p>

      <div v-if="isHighRiskPreview" class="ai-preview high">
        已识别为紧急安全故障，提交后将优先指派师傅上门处理
      </div>

      <button type="button" class="sheet-submit spring-btn" :disabled="submitting" @click="submitRepair">
        {{ submitting ? '正在提交...' : '提交报修' }}
      </button>
    </BottomSheet>

    <!-- 2. 自主选择星级与评价半屏抽屉 -->
    <BottomSheet v-model="rateSheetOpen" title="评价维修服务">
      <div v-if="ratingTarget" class="rate-panel">
        <div class="rate-order-title">{{ ratingTarget.title }}（维修人：{{ ratingTarget.workerName || '后勤师傅' }}）</div>

        <div class="star-picker" role="radiogroup" aria-label="选择评价星级">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            class="star-btn spring-btn"
            :class="{ active: n <= rateForm.rating }"
            :aria-label="`${n}星`"
            @click="rateForm.rating = n"
          >
            ★
          </button>
          <span class="star-desc">{{ ratingDescText }}</span>
        </div>

        <div class="quick-tags">
          <button
            v-for="tag in rateQuickTags"
            :key="tag"
            type="button"
            class="q-tag spring-btn"
            @click="appendRateTag(tag)"
          >
            + {{ tag }}
          </button>
        </div>

        <div class="form-row">
          <label for="rate-comment">评价内容</label>
          <textarea
            id="rate-comment"
            v-model="rateForm.ratingComment"
            rows="3"
            class="area"
            placeholder="请输入您对本次维修服务的评价或改进建议..."
          ></textarea>
        </div>

        <button type="button" class="sheet-submit spring-btn" :disabled="submittingRate" @click="submitRate">
          {{ submittingRate ? '正在提交...' : `提交 ${rateForm.rating} 星评价` }}
        </button>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import api from '../api'
import BottomSheet from './BottomSheet.vue'

const props = defineProps({
  dash: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['refresh', 'toast'])
const pendingRateCount = computed(() => (props.dash.myRepairs || []).filter(r => r.status === 'COMPLETED').length)
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '今日报修'

const sheetOpen = ref(false)
const submitting = ref(false)
const errorMsg = ref('')

const rateSheetOpen = ref(false)
const submittingRate = ref(false)
const ratingTarget = ref(null)
const rateForm = reactive({
  rating: 5,
  ratingComment: '师傅上门很及时，问题已彻底解决。'
})

const rateQuickTags = ['响应迅速', '维修专业', '态度温和', '清理现场', '有待改进']

const ratingDescText = computed(() => {
  const map = {
    1: '1星 · 很不满意',
    2: '2星 · 有待改进',
    3: '3星 · 一般',
    4: '4星 · 比较满意',
    5: '5星 · 非常满意'
  }
  return map[rateForm.rating] || '5星'
})

const types = [
  { val: 'ELECTRIC', label: '电力照明' },
  { val: 'WATER', label: '水暖卫浴' },
  { val: 'WOOD', label: '门窗床桌' },
  { val: 'NETWORK', label: '网络空调' }
]

const form = reactive({
  repairType: 'ELECTRIC',
  title: '书桌插座跳闸断电',
  description: '插上电源适配器时插座跳闸，请电工师傅排查'
})
const photoUrls = ref([])
const resolveUrl = (u) => {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('data:') || u.startsWith('blob:')) return u
  return (import.meta.env.VITE_API_BASE || 'http://localhost:7000') + u
}
const onPickPhotos = async (e) => {
  const files = Array.from(e.target.files || []).slice(0, 3)
  const urls = []
  for (const f of files) {
    try {
      const fd = new FormData()
      fd.append('file', f)
      fd.append('biz', 'repair')
      const res = await api.post('/upload/image', fd)
      if (res.data?.url) urls.push(res.data.url)
    } catch (_) {
      // fallback: local dataURL for demo if upload fails
      const dataUrl = await new Promise((resolve) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result)
        reader.readAsDataURL(f)
      })
      urls.push(dataUrl)
    }
  }
  photoUrls.value = urls.slice(0, 3)
  e.target.value = ''
}


const highWords = ['跳闸', '短路', '火花', '焦糊', '冒烟', '爆管', '漏水严重', '门锁打不开', '断电']
const isHighRiskPreview = computed(() => {
  const txt = `${form.title} ${form.description}`
  return highWords.some(w => txt.includes(w))
})

const statusLabel = (s) => {
  const m = { PENDING: '待派单', ASSIGNED: '待上门', PROCESSING: '维修中', COMPLETED: '待评价', RATED: '已完结' }
  return m[s] || s
}

const openSubmitSheet = () => {
  if (!props.dash.room) {
    emit('toast', '分配寝室后即可发起报修')
    return
  }
  errorMsg.value = ''
  sheetOpen.value = true
}

const submitRepair = async () => {
  errorMsg.value = ''
  if (!form.title.trim()) {
    errorMsg.value = '请填写故障标题'
    return
  }
  submitting.value = true
  try {
    await api.post('/repair/submit', {
      studentId: props.dash.user?.id || 10,
      buildingId: props.dash.building?.id || 1,
      roomId: props.dash.room?.id || 1,
      ...form,
      photoUrls: photoUrls.value.length ? JSON.stringify(photoUrls.value) : null
    })
    photoUrls.value = []
    sheetOpen.value = false
    emit('toast', '报修已提交，已为您指派师傅')
    emit('refresh')
  } finally {
    submitting.value = false
  }
}

const openRateSheet = (item) => {
  ratingTarget.value = item
  rateForm.rating = 5
  rateForm.ratingComment = '师傅响应及时，维修细致！'
  rateSheetOpen.value = true
}

const appendRateTag = (tag) => {
  if (!rateForm.ratingComment.includes(tag)) {
    rateForm.ratingComment = rateForm.ratingComment
      ? `${rateForm.ratingComment}，${tag}`
      : tag
  }
}

const submitRate = async () => {
  if (!ratingTarget.value) return
  submittingRate.value = true
  try {
    await api.post('/repair/rate', {
      id: ratingTarget.value.id,
      rating: rateForm.rating,
      ratingComment: rateForm.ratingComment
    })
    rateSheetOpen.value = false
    emit('toast', `已提交 ${rateForm.rating} 星评价`)
    emit('refresh')
  } finally {
    submittingRate.value = false
  }
}
</script>

<style scoped>
.repair-hero { background: transparent; color: var(--text-primary); padding: 7px 0 24px; display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.h-title { font-size: 27px; line-height: 1.3; font-weight: 600; letter-spacing: -.8px; }
.h-sub { font-size: 12px; color: var(--text-muted); margin-top: 8px; }
.call-btn {
  background: var(--button-primary);
  color: #ffffff;
  border: none;
  padding: 9px 15px;
  border-radius: 99px;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.empty-box {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 22px;
  padding: 26px 18px;
  text-align: center;
}
.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}
.empty-desc {
  font-size: 14px;
  color: var(--text-muted);
  margin: 6px 0 16px;
}
.empty-cta {
  background: var(--button-primary);
  color: #ffffff;
  border: none;
  padding: 9px 18px;
  border-radius: 99px;
  font-size: 14px;
  font-weight: 500;
}

.order-list { background: var(--bg-surface); border-radius: var(--radius-card); overflow: hidden; }
.order-card { background: transparent; border: 0; border-radius: 0; padding: 22px; margin: 0; }
.order-card + .order-card { border-top: 1px solid var(--border-subtle); }
.o-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.o-no {
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: var(--text-muted);
}
.prio-high {
  background: var(--status-danger-bg);
  color: var(--status-danger);
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 99px;
}
.prio-norm {
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 99px;
}
.o-title {
  font-size: 16px;
  font-weight: 600;
  margin: 8px 0 4px;
}
.o-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
}
.dispatch-trace { background: transparent; padding: 14px 0 0; border: 0; border-top: 1px solid var(--border-subtle); border-radius: 0; margin: 16px 0 12px; font-size: 12px; }
.t-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.t-result {
  color: var(--text-secondary);
  margin-top: 4px;
}
.o-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.o-time {
  font-size: 12px;
  color: var(--text-muted);
}
.status-tag {
  font-size: 13px;
  font-weight: 500;
  color: var(--accent-primary);
}
.rate-btn {
  background: var(--button-primary);
  color: #ffffff;
  border: none;
  padding: 6px 14px;
  border-radius: 99px;
  font-size: 13px;
  font-weight: 500;
}
.stars {
  color: #f59e0b;
  font-size: 13px;
  font-weight: 500;
}

.form-row {
  margin-bottom: 14px;
}
.form-row label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.type-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}
.type-chip {
  border: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  border-radius: 10px;
  padding: 10px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}
.type-chip.active {
  background: var(--accent-subtle);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}
.inp,
.area {
  width: 100%;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  
}
.inline-error {
  font-size: 13px;
  color: var(--status-danger);
  margin-bottom: 8px;
}
.ai-preview {
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  margin-bottom: 12px;
}
.ai-preview.high {
  background: var(--status-danger-bg);
  color: var(--status-danger);
  font-weight: 500;
}
.sheet-submit {
  width: 100%;
  background: var(--button-primary);
  color: #ffffff;
  border: none;
  padding: 13px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
}

/* 评价弹窗星级选择器 */
.rate-order-title {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}
.star-picker { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; margin-bottom: 18px; }
.star-btn {
  border: none;
  background: transparent;
  font-size: 28px;
  color: var(--border-strong);
  line-height: 1;
}
.star-btn.active {
  color: #f59e0b;
}
.star-desc {
  margin-left: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--status-warning);
}
.quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.q-tag {
  border: 1px solid var(--border-subtle);
  background: var(--bg-canvas);
  color: var(--text-secondary);
  padding: 5px 10px;
  border-radius: 99px;
  font-size: 13px;
}

.progress-timeline { display: flex; gap: 4px; list-style: none; padding: 10px 0 0; margin: 0; flex-wrap: wrap; }
.progress-timeline li { font-size: 10px; color: var(--text-muted); padding: 3px 7px; border-radius: 999px; background: var(--bg-muted); }
.progress-timeline li.done { color: var(--status-success); background: var(--status-success-bg); }
.progress-timeline li.current { color: var(--accent-primary); background: var(--accent-subtle); font-weight: 600; }
.rate-reminder { margin-top: 10px; font-size: 12px; color: #9a6700; background: #fff7e8; border-radius: 10px; padding: 8px 10px; }
.pending-rate-top { margin-bottom: 12px; padding: 10px 12px; border-radius: 12px; background: #fff7e8; color: #9a6700; font-size: 13px; font-weight: 500; }

.photo-preview { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
.photo-preview img { width: 72px; height: 72px; object-fit: cover; border-radius: 10px; border: 1px solid var(--border-subtle); }
</style>
