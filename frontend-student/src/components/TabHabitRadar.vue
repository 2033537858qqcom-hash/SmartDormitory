<template>
  <div class="tab-habit">
    <header class="mobile-page-heading"><h1>生活习惯</h1><p>记录你的日常偏好，遇见作息相近的室友。</p></header>
    <!-- 优先展示：生活习惯问卷填写区（首屏即达核心任务） -->
    <section class="survey-card">
      <div class="s-head">
        <div>
          <h2 class="s-title">习惯问卷</h2>
          <p class="s-desc">滑动调整您的作息与习惯，系统将据此匹配最契合的室友</p>
        </div>
        <button
          type="button"
          class="radar-toggle-btn spring-btn"
          @click="showRadar = !showRadar"
        >
          {{ showRadar ? '收起雷达图 ▲' : '预览雷达图 ▼' }}
        </button>
      </div>

      <!-- 可折叠的习惯雷达图与室友对比 -->
      <transition name="expand">
        <div v-if="showRadar" class="radar-preview-box">
          <div class="r-sub tabular-nums">
            {{ dash.room ? `室友契合度：${dash.roomRadar?.harmonyScore || 95}% (${dash.roomRadar?.styleTag || '融洽寝室'})` : '个人生活习惯六维向量预览' }}
          </div>
          <div class="svg-radar-box">
            <svg viewBox="-14 0 278 214" class="radar-svg" role="img" aria-label="生活习惯雷达图">
              <polygon
                v-for="lv in [1, 2, 3, 4, 5]"
                :key="lv"
                :points="getGridPolygon(lv)"
                fill="none"
                stroke="#e2e8f0"
                stroke-width="1"
              />
              <line
                v-for="(pt, idx) in axisOuterPoints"
                :key="idx"
                x1="125"
                y1="107"
                :x2="pt.x"
                :y2="pt.y"
                stroke="#cbd5e1"
                stroke-dasharray="2,2"
              />
              <polygon
                v-if="roomAvgPolygon"
                :points="roomAvgPolygon"
                fill="rgba(148, 163, 184, 0.22)"
                stroke="#64748b"
                stroke-width="2"
              />
              <polygon
                :points="myPolygon"
                fill="rgba(0, 110, 219, 0.16)"
                stroke="#006edb"
                stroke-width="2.5"
              />
              <text x="125" y="16" text-anchor="middle" class="axis-txt">作息时间</text>
              <text x="210" y="64" text-anchor="start" class="axis-txt">睡眠深浅</text>
              <text x="210" y="158" text-anchor="start" class="axis-txt">空调偏好</text>
              <text x="125" y="206" text-anchor="middle" class="axis-txt">卫生习惯</text>
              <text x="40" y="158" text-anchor="end" class="axis-txt">寝室氛围</text>
              <text x="40" y="64" text-anchor="end" class="axis-txt">无烟要求</text>
            </svg>
            <div class="radar-legend">
              <span><i class="dot primary"></i> 我的习惯</span>
              <span v-if="roomAvgPolygon"><i class="dot slate"></i> 室友平均</span>
            </div>
          </div>
        </div>
      </transition>

      <!-- 6 项习惯偏好液态滑块 -->
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
        {{ saving ? '正在保存...' : '保存习惯问卷' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue'
import api from '../api'

const props = defineProps({
  dash: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['refresh', 'toast'])

const saving = ref(false)
const showRadar = ref(false)
const form = reactive({
  scheduleScore: 2,
  sleepDepthScore: 4,
  acTempScore: 3,
  cleanlinessScore: 5,
  atmosphereScore: 2,
  odorSensitiveScore: 5,
  hobbyTags: '图书馆自习,早起,羽毛球'
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
      form.odorSensitiveScore = hp.odorSensitiveScore || 5
      form.hobbyTags = hp.hobbyTags || ''
    }
  },
  { immediate: true }
)

const dimensions = [
  { key: 'scheduleScore', label: '作息时间', left: '22点前早睡', right: '24点后晚睡', tips: ['极早睡', '规律早睡', '23点正常', '偏晚睡', '熬夜型'] },
  { key: 'sleepDepthScore', label: '睡眠深浅', left: '深睡不怕吵', right: '浅眠需安静', tips: ['深度睡眠', '较深睡', '正常睡眠', '偏浅眠', '易惊醒'] },
  { key: 'acTempScore', label: '空调温度', left: '偏好凉爽', right: '偏好温暖', tips: ['偏低温', '较凉爽', '25℃适中', '偏温暖', '怕冷风'] },
  { key: 'cleanlinessScore', label: '卫生习惯', left: '随性自在', right: '每日清扫', tips: ['随性派', '定期收拾', '整洁干净', '高标准', '每日清扫'] },
  { key: 'atmosphereScore', label: '寝室氛围', left: '安静自习', right: '热闹活跃', tips: ['安静自习', '偏安静', '劳逸结合', '热闹活跃', '社交娱乐'] },
  { key: 'odorSensitiveScore', label: '无烟要求', left: '不敏感', right: '严格无烟', tips: ['不介意', '轻微介意', '普通标准', '比较敏感', '严格无烟'] }
]

const angles = [-90, -30, 30, 90, 150, 210].map(a => (a * Math.PI) / 180)
const center = { x: 125, y: 107 }
const maxR = 76

const getPoint = (angleIdx, val) => {
  const r = (val / 5) * maxR
  return {
    x: (center.x + r * Math.cos(angles[angleIdx])).toFixed(1),
    y: (center.y + r * Math.sin(angles[angleIdx])).toFixed(1)
  }
}

const getGridPolygon = (level) => {
  return angles.map((_, idx) => {
    const p = getPoint(idx, level)
    return `${p.x},${p.y}`
  }).join(' ')
}

const axisOuterPoints = computed(() => angles.map((_, idx) => getPoint(idx, 5)))

const myPolygon = computed(() => {
  const vec = [
    form.scheduleScore,
    form.sleepDepthScore,
    form.acTempScore,
    form.cleanlinessScore,
    form.atmosphereScore,
    form.odorSensitiveScore
  ]
  return vec.map((v, idx) => {
    const p = getPoint(idx, v)
    return `${p.x},${p.y}`
  }).join(' ')
})

const roomAvgPolygon = computed(() => {
  const mates = props.dash.roomRadar?.roommates || []
  if (!mates.length) return ''
  const avg = [0, 0, 0, 0, 0, 0]
  mates.forEach(m => {
    const v = m.vector || [3, 3, 3, 3, 3, 5]
    for (let i = 0; i < 6; i++) avg[i] += v[i]
  })
  return avg.map((sum, idx) => {
    const p = getPoint(idx, sum / mates.length)
    return `${p.x},${p.y}`
  }).join(' ')
})

const saveHabit = async () => {
  saving.value = true
  try {
    await api.post('/smart-alloc/habit-profile', {
      studentId: props.dash.user?.id,
      ...form
    })
    emit('toast', { msg: '习惯问卷已保存成功', type: 'success' })
    emit('refresh')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.survey-card { background: var(--bg-surface); border: 0; border-radius: var(--radius-card); padding: 23px; margin-bottom: 16px; }
.s-head { display: flex; flex-direction: column; align-items: flex-start; gap: 13px; margin-bottom: 20px; }
.s-title { font-size: 19px; font-weight: 600; letter-spacing: -.4px; color: var(--text-primary); }
.s-desc {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 3px;
  line-height: 1.4;
}
.radar-toggle-btn {
  border: 1px solid var(--border-subtle);
  background: var(--accent-subtle);
  color: var(--accent-primary);
  font-size: 12px;
  font-weight: 500;
  padding: 5px 11px;
  border-radius: 99px;
  white-space: nowrap;
}

.radar-preview-box {
  background: var(--bg-canvas);
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  padding: 12px 10px;
  margin-bottom: 16px;
  text-align: center;
}
.r-sub {
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-primary);
  margin-bottom: 4px;
}
.svg-radar-box {
  text-align: center;
}
.radar-svg {
  width: 240px;
  height: 205px;
}
.axis-txt {
  font-size: 11px;
  fill: var(--text-secondary);
  font-weight: 600;
}
.radar-legend {
  display: flex;
  justify-content: center;
  gap: 18px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}
.dot.primary { background: var(--accent-primary); }
.dot.slate { background: var(--text-muted); }

.slider-item { padding: 18px 0; margin: 0; border-top: 1px solid var(--border-subtle); }
.sl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.sl-label {
  font-size: 14px;
  font-weight: 500;
}
.sl-bubble { font-size: 12px; color: var(--text-muted); font-weight: 400; }
.liquid-range { appearance: none; -webkit-appearance: none; width: 100%; height: 32px; border: 0; background: linear-gradient(to right, var(--accent-primary) 0 var(--range-progress), #dedee3 var(--range-progress) 100%) center / 100% 5px no-repeat; cursor: pointer; }
.liquid-range::-webkit-slider-thumb { appearance: none; -webkit-appearance: none; width: 24px; height: 24px; border: 0; border-radius: 50%; background: #fff; box-shadow: 0 2px 7px rgb(29 29 31 / 20%), 0 0 0 1px rgb(29 29 31 / 5%); }
.liquid-range::-moz-range-thumb { width: 24px; height: 24px; border: 0; border-radius: 50%; background: #fff; box-shadow: 0 2px 7px rgb(29 29 31 / 20%), 0 0 0 1px rgb(29 29 31 / 5%); }
.liquid-range::-moz-range-track { height: 5px; background: transparent; }
.sl-ends {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}
.tag-input-box {
  margin: 14px 0 16px;
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
}
</style>
