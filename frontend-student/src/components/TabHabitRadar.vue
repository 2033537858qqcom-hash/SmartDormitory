<template>
  <div class="tab-habit" :class="{ 'in-sheet-mode': inSheet }">
    <header v-if="!inSheet" class="mobile-page-heading">
      <h1>{{ isTransferMode ? '调宿习惯偏好' : '生活习惯' }}</h1>
      <p>{{ isTransferMode ? '调整作息与生活偏好，系统将基于最新画像为您重新匹配寝室。' : '记录你的日常偏好，遇见作息相近的室友。' }}</p>
    </header>

    <!-- 生活习惯问卷填写区 -->
    <section class="survey-card">
      <div class="s-head">
        <div>
          <h2 class="s-title">{{ isTransferMode ? '你的生活习惯' : '习惯问卷' }}</h2>
          <p class="s-desc">
            {{ isTransferMode ? '拖动图中顶点或下方滑块，调整后保存即可更新推荐。' : '调整日常习惯，帮助系统匹配作息相近的室友。' }}
          </p>
          <p v-if="!dash.habitProfile" class="profile-note">尚未保存习惯，当前显示初始值。</p>
        </div>
      </div>

      <!-- 核心交互区：直接呈现实时可滑动调整的六维雷达图 -->
      <div class="radar-preview-box">
        <HabitRadarChart
          :model-value="form"
          @update:model-value="updateForm"
          :room-radar="dash.roomRadar"
          :sub-title="inSheet ? '' : radarSubtitle"
          :show-hint="!inSheet"
          @change="onRadarChange"
        />
      </div>

      <!-- 6 项习惯偏好液态滑块（与雷达图双向完全联动） -->
      <div class="slider-group">
        <div class="slider-heading"><b>六项偏好</b><span>从 1 到 5 调整</span></div>
        <div v-for="dim in dimensions" :key="dim.key" class="slider-item">
          <div class="sl-head">
            <label :for="dim.key" class="sl-label">{{ dim.label }}</label>
            <span class="sl-bubble tabular-nums">{{ form[dim.key] }}分 · {{ dim.tips[form[dim.key] - 1] }}</span>
          </div>
          <input
            :id="dim.key"
            v-model.number="form[dim.key]"
            type="range"
            min="1"
            max="5"
            step="1"
            class="liquid-range"
            :style="{ '--range-progress': `${((form[dim.key] - 1) / 4) * 100}%` }"
            :aria-valuetext="`${form[dim.key]}分，${dim.tips[form[dim.key] - 1]}`"
          />
          <div class="sl-ends tabular-nums">
            <span>1分：{{ dim.left }}</span>
            <span>5分：{{ dim.right }}</span>
          </div>
        </div>
      </div>

      <div class="tag-input-box">
        <label for="hobby-tags-input">个人作息与兴趣标签</label>
        <input
          id="hobby-tags-input"
          v-model="form.hobbyTags"
          class="text-inp"
          placeholder="如：考研早起,羽毛球,安静自习"
        />
      </div>

      <button type="button" class="submit-btn spring-btn" :disabled="saving" @click="saveHabit">
        {{ saving ? '正在保存...' : (isTransferMode ? '保存习惯并更新调宿匹配' : '保存习惯问卷') }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue'
import api from '../api'
import HabitRadarChart from './HabitRadarChart.vue'

const props = defineProps({
  dash: { type: Object, default: () => ({}) },
  inSheet: { type: Boolean, default: false },
  mode: { type: String, default: 'normal' } // 'normal' | 'transfer'
})

const emit = defineEmits(['refresh', 'toast', 'saved'])

const saving = ref(false)
const isTransferMode = computed(() => props.mode === 'transfer')

const form = reactive({
  scheduleScore: 3,
  sleepDepthScore: 3,
  acTempScore: 3,
  cleanlinessScore: 3,
  atmosphereScore: 3,
  odorSensitiveScore: 3,
  hobbyTags: ''
})

watch(
  () => props.dash.habitProfile,
  (hp) => {
    if (hp) {
      form.scheduleScore = hp.scheduleScore || 3
      form.sleepDepthScore = hp.sleepDepthScore || 3
      form.acTempScore = hp.acTempScore || 3
      form.cleanlinessScore = hp.cleanlinessScore || 3
      form.atmosphereScore = hp.atmosphereScore || 3
      form.odorSensitiveScore = hp.odorSensitiveScore ?? 3
      form.hobbyTags = hp.hobbyTags || ''
    }
  },
  { immediate: true }
)

const radarSubtitle = computed(() => {
  if (isTransferMode.value) {
    return '调宿画像实时预览（拖拽顶点可修改偏好）'
  }
  const radar = props.dash.roomRadar
  return radar?.harmonyScore != null
    ? `寝室和谐度：${radar.harmonyScore}%${radar.styleTag ? ` · ${radar.styleTag}` : ''}`
    : '个人生活习惯画像'
})

const dimensions = [
  { key: 'scheduleScore', label: '作息时间', left: '22点前早睡', right: '24点后晚睡', tips: ['极早睡', '规律早睡', '23点正常', '偏晚睡', '熬夜型'] },
  { key: 'sleepDepthScore', label: '睡眠深浅', left: '深睡不怕吵', right: '浅眠需安静', tips: ['深度睡眠', '较深睡', '正常睡眠', '偏浅眠', '易惊醒'] },
  { key: 'acTempScore', label: '空调温度', left: '偏好凉爽', right: '偏好温暖', tips: ['偏低温', '较凉爽', '25℃适中', '偏温暖', '怕冷风'] },
  { key: 'cleanlinessScore', label: '卫生习惯', left: '随性自在', right: '每日清扫', tips: ['随性派', '定期收拾', '整洁干净', '高标准', '每日清扫'] },
  { key: 'atmosphereScore', label: '寝室氛围', left: '安静自习', right: '热闹活跃', tips: ['安静自习', '偏安静', '劳逸结合', '热闹活跃', '社交娱乐'] },
  { key: 'odorSensitiveScore', label: '无烟要求', left: '不敏感', right: '严格无烟', tips: ['不介意', '轻微介意', '普通标准', '比较敏感', '严格无烟'] }
]

const onRadarChange = () => {
  // 雷达图微调时可以进行轻量触感反馈或状态追踪
}

const updateForm = (values) => Object.assign(form, values)

const saveHabit = async () => {
  if (saving.value) return
  saving.value = true
  try {
    await api.post('/smart-alloc/habit-profile', {
      studentId: props.dash.user?.id,
      ...form
    })
    emit('toast', { msg: isTransferMode.value ? '调宿习惯偏好已更新' : '习惯问卷已保存成功', type: 'success' })
    emit('saved', { ...form })
    emit('refresh')
  } catch (err) {
    emit('toast', { msg: '保存失败，请稍后重试', type: 'error' })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.tab-habit {
  width: 100%;
}

.in-sheet-mode {
  padding-bottom: 6px;
}

.survey-card {
  background: var(--bg-surface);
  border: 0;
  border-radius: var(--radius-card);
  padding: 20px 18px;
  margin-bottom: 16px;
}

.in-sheet-mode .survey-card { padding: 2px 0 0; margin-bottom: 0; border-radius: 0; }

.s-head {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 16px;
}

.s-title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -.4px;
  color: var(--text-primary);
}

.s-desc {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.55;
  margin-top: 5px;
}
.profile-note { margin-top: 5px; color: var(--text-muted); font-size: 12px; line-height: 1.5; }

.radar-preview-box {
  background: var(--bg-canvas);
  border: 1px solid var(--border-subtle);
  border-radius: 18px;
  padding: 15px 6px 14px;
  margin-bottom: 22px;
}

.slider-group {
  margin-top: 10px;
}
.slider-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; margin-bottom: 5px; }
.slider-heading b { font-size: 15px; color: var(--text-primary); }
.slider-heading span { font-size: 11px; color: var(--text-muted); }

.slider-item {
  padding: 14px 0;
  margin: 0;
  border-top: 1px solid var(--border-subtle);
}

.sl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.sl-label {
  font-size: 14px;
  font-weight: 500;
}

.sl-bubble {
  font-size: 12px;
  color: var(--accent-primary);
  font-weight: 600;
  text-align: right;
}

.liquid-range {
  appearance: none;
  -webkit-appearance: none;
  width: 100%;
  height: 32px;
  border: 0;
  background: linear-gradient(to right, var(--accent-primary) 0 var(--range-progress), #dedee3 var(--range-progress) 100%) center / 100% 5px no-repeat;
  cursor: pointer;
}

.liquid-range::-webkit-slider-thumb {
  appearance: none;
  -webkit-appearance: none;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 7px rgb(29 29 31 / 20%), 0 0 0 1px rgb(29 29 31 / 5%);
}

.liquid-range::-moz-range-thumb {
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 7px rgb(29 29 31 / 20%), 0 0 0 1px rgb(29 29 31 / 5%);
}

.liquid-range::-moz-range-track {
  height: 5px;
  background: transparent;
}

.sl-ends {
  display: flex;
  justify-content: space-between;
  font-size: 11.5px;
  color: var(--text-muted);
  margin-top: 3px;
}

.tag-input-box {
  margin: 16px 0 18px;
  border-top: 1px solid var(--border-subtle);
  padding-top: 14px;
}

.tag-input-box label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 6px;
}

.text-inp {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  font-size: 14px;
  background: var(--bg-surface);
  color: var(--text-primary);
  box-sizing: border-box;
}

.submit-btn {
  width: 100%;
  background: var(--accent-primary);
  color: #ffffff;
  border: none;
  padding: 13px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
</style>
