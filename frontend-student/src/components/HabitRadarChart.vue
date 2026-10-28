<template>
  <div class="habit-radar-container" :class="{ compact, dragging: isDragging }">
    <!-- 交互提示条 -->
    <div v-if="!readonly" class="drag-hint-pill">
      <span class="hint-icon">👆</span>
      <span class="hint-text">
        {{ isDragging ? `正在调整【${activeDimLabel}】：${activeDimScore}分 · ${activeDimTip}` : '按住雷达图顶点向外或向内滑动，可直接调分' }}
      </span>
    </div>

    <!-- 契合度与副标题（若有） -->
    <div v-if="subTitle" class="r-sub tabular-nums">
      {{ subTitle }}
    </div>

    <!-- SVG 雷达图核心绘图区 -->
    <div class="svg-radar-box">
      <svg
        ref="svgRef"
        viewBox="-14 0 288 224"
        class="radar-svg"
        role="img"
        aria-label="可滑动调整的生活习惯六维雷达图"
        @pointerleave="onPointerUp"
      >
        <defs>
          <!-- 径向渐变：我的习惯多边形填充 -->
          <radialGradient id="radarFillGrad" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="#006edb" stop-opacity="0.32" />
            <stop offset="100%" stop-color="#006edb" stop-opacity="0.10" />
          </radialGradient>
          <!-- 顶点光晕滤镜 -->
          <filter id="handleGlow" x="-50%" y="-50%" width="200%" height="200%">
            <feDropShadow dx="0" dy="1.5" stdDeviation="2.5" flood-color="rgba(0,110,219,0.4)" />
          </filter>
        </defs>

        <!-- 背景多边形网格（1至5档） -->
        <polygon
          v-for="lv in [1, 2, 3, 4, 5]"
          :key="'grid-' + lv"
          :points="getGridPolygon(lv)"
          fill="none"
          :stroke="lv === 5 ? '#cbd5e1' : '#e2e8f0'"
          :stroke-width="lv === 5 ? 1.4 : 1"
          stroke-linejoin="round"
        />

        <!-- 六根辐射轴虚线 -->
        <line
          v-for="(pt, idx) in axisOuterPoints"
          :key="'axis-' + idx"
          :x1="center.x"
          :y1="center.y"
          :x2="pt.x"
          :y2="pt.y"
          :stroke="activeDragIndex === idx ? '#006edb' : '#cbd5e1'"
          :stroke-width="activeDragIndex === idx ? 1.8 : 1"
          :stroke-dasharray="activeDragIndex === idx ? 'none' : '2,2'"
        />

        <!-- 刻度吸附参考点（当前拖拽轴线高亮显现） -->
        <g v-if="activeDragIndex >= 0">
          <circle
            v-for="lv in [1, 2, 3, 4, 5]"
            :key="'tick-' + lv"
            :cx="getTickPoint(activeDragIndex, lv).x"
            :cy="getTickPoint(activeDragIndex, lv).y"
            :r="lv === currentScoreOnActiveAxis ? 3.5 : 2"
            :fill="lv === currentScoreOnActiveAxis ? '#006edb' : '#94a3b8'"
            opacity="0.85"
          />
        </g>

        <!-- 轴线触摸点击响应区域（点击轴上任意位置即可设分） -->
        <line
          v-for="(pt, idx) in axisOuterPoints"
          :key="'hit-' + idx"
          :x1="center.x"
          :y1="center.y"
          :x2="pt.x"
          :y2="pt.y"
          stroke="transparent"
          stroke-width="26"
          stroke-linecap="round"
          class="axis-hit-line"
          :style="{ cursor: readonly ? 'default' : 'pointer' }"
          @pointerdown="onAxisPointerDown(idx, $event)"
        />

        <!-- 室友平均多边形（灰色对比参考） -->
        <polygon
          v-if="roomAvgPolygon"
          :points="roomAvgPolygon"
          fill="rgba(148, 163, 184, 0.20)"
          stroke="#64748b"
          stroke-width="2"
          stroke-linejoin="round"
          stroke-dasharray="3,2"
        />

        <!-- 我的习惯动态多边形（支持拖拽实时形变） -->
        <polygon
          :points="myPolygon"
          fill="url(#radarFillGrad)"
          stroke="#006edb"
          :stroke-width="isDragging ? 3 : 2.5"
          stroke-linejoin="round"
        />

        <!-- 维度标签文字与当前分值 -->
        <g v-for="(dim, idx) in dimensions" :key="'label-' + dim.key">
          <text
            :x="labelPositions[idx].x"
            :y="labelPositions[idx].y"
            :text-anchor="labelPositions[idx].anchor"
            class="axis-txt"
            :class="{ active: activeDragIndex === idx }"
            @click="!readonly && stepDimension(idx)"
          >
            {{ dim.label }}
            <tspan class="score-tspan" :fill="activeDragIndex === idx ? '#006edb' : '#006edb'">
              {{ localForm[dim.key] }}分
            </tspan>
          </text>
        </g>

        <!-- 交互顶点手柄（每个角均支持触控/鼠标滑动） -->
        <g v-for="(dim, idx) in dimensions" :key="'handle-' + dim.key">
          <!-- 顶点外扩光晕环（拖拽或悬停时发光放大） -->
          <circle
            v-if="activeDragIndex === idx"
            :cx="getVertexPoint(idx).x"
            :cy="getVertexPoint(idx).y"
            r="14"
            fill="rgba(0, 110, 219, 0.22)"
            class="pulse-ring"
          />

          <!-- 视觉圆点手柄 -->
          <circle
            :cx="getVertexPoint(idx).x"
            :cy="getVertexPoint(idx).y"
            :r="activeDragIndex === idx ? 7 : 5.5"
            fill="#ffffff"
            stroke="#006edb"
            :stroke-width="activeDragIndex === idx ? 3.2 : 2.4"
            filter="url(#handleGlow)"
            class="handle-dot"
          />

          <!-- 触控热区（覆盖半径达 22px，手机触摸极其灵敏） -->
          <circle
            v-if="!readonly"
            :cx="getVertexPoint(idx).x"
            :cy="getVertexPoint(idx).y"
            r="22"
            fill="transparent"
            class="handle-hit-circle"
            @pointerdown.stop.prevent="startDrag(idx, $event)"
          />
        </g>

        <!-- 实时拖动浮动气泡（指示分值与具体作息含义） -->
        <g v-if="isDragging && activeDragIndex >= 0" class="floating-tooltip">
          <rect
            :x="floatingBubblePos.x - 38"
            :y="floatingBubblePos.y - 20"
            width="76"
            height="20"
            rx="10"
            fill="rgba(15, 23, 42, 0.88)"
          />
          <text
            :x="floatingBubblePos.x"
            :y="floatingBubblePos.y - 6"
            text-anchor="middle"
            fill="#ffffff"
            font-size="10.5"
            font-weight="600"
          >
            {{ activeDimScore }}分·{{ activeDimTip }}
          </text>
        </g>
      </svg>

      <!-- 图例与说明 -->
      <div v-if="showLegend" class="radar-legend">
        <span><i class="dot primary"></i> 我的习惯偏好（可拖动调整）</span>
        <span v-if="roomAvgPolygon"><i class="dot slate"></i> 寝室室友平均</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({
      scheduleScore: 3,
      sleepDepthScore: 3,
      acTempScore: 3,
      cleanlinessScore: 3,
      atmosphereScore: 3,
      odorSensitiveScore: 5
    })
  },
  roomRadar: {
    type: Object,
    default: null
  },
  subTitle: {
    type: String,
    default: ''
  },
  readonly: {
    type: Boolean,
    default: false
  },
  compact: {
    type: Boolean,
    default: false
  },
  showLegend: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const dimensions = [
  { key: 'scheduleScore', label: '作息时间', tips: ['极早睡', '规律早睡', '23点正常', '偏晚睡', '熬夜型'] },
  { key: 'sleepDepthScore', label: '睡眠深浅', tips: ['深度睡眠', '较深睡', '正常睡眠', '偏浅眠', '易惊醒'] },
  { key: 'acTempScore', label: '空调温度', tips: ['偏低温', '较凉爽', '25℃适中', '偏温暖', '怕冷风'] },
  { key: 'cleanlinessScore', label: '卫生习惯', tips: ['随性派', '定期收拾', '整洁干净', '高标准', '每日清扫'] },
  { key: 'atmosphereScore', label: '寝室氛围', tips: ['安静自习', '偏安静', '劳逸结合', '热闹活跃', '社交娱乐'] },
  { key: 'odorSensitiveScore', label: '无烟要求', tips: ['不介意', '轻微介意', '普通标准', '比较敏感', '严格无烟'] }
]

const localForm = reactive({
  scheduleScore: 3,
  sleepDepthScore: 3,
  acTempScore: 3,
  cleanlinessScore: 3,
  atmosphereScore: 3,
  odorSensitiveScore: 5
})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      localForm.scheduleScore = val.scheduleScore ?? 3
      localForm.sleepDepthScore = val.sleepDepthScore ?? 3
      localForm.acTempScore = val.acTempScore ?? 3
      localForm.cleanlinessScore = val.cleanlinessScore ?? 3
      localForm.atmosphereScore = val.atmosphereScore ?? 3
      localForm.odorSensitiveScore = val.odorSensitiveScore ?? 5
    }
  },
  { immediate: true, deep: true }
)

// 雷达图几何参数
// 6 个顶点的角度（依次：正上、右上、右下、正下、左下、左上）
const angles = [-90, -30, 30, 90, 150, 210].map(a => (a * Math.PI) / 180)
const center = { x: 130, y: 110 }
const maxR = 76

const svgRef = ref(null)
const activeDragIndex = ref(-1)
const isDragging = ref(false)
const dragRadius = ref(null)

const activeDimLabel = computed(() => {
  if (activeDragIndex.value < 0) return ''
  return dimensions[activeDragIndex.value].label
})

const activeDimScore = computed(() => {
  if (activeDragIndex.value < 0) return 3
  const key = dimensions[activeDragIndex.value].key
  return localForm[key] || 3
})

const activeDimTip = computed(() => {
  if (activeDragIndex.value < 0) return ''
  const dim = dimensions[activeDragIndex.value]
  const score = localForm[dim.key] || 3
  return dim.tips[score - 1] || ''
})

const currentScoreOnActiveAxis = computed(() => {
  if (activeDragIndex.value < 0) return 3
  const key = dimensions[activeDragIndex.value].key
  return localForm[key] || 3
})

// 计算刻度网格点
const getPointAt = (angleIdx, r) => {
  return {
    x: Number((center.x + r * Math.cos(angles[angleIdx])).toFixed(1)),
    y: Number((center.y + r * Math.sin(angles[angleIdx])).toFixed(1))
  }
}

const getGridPolygon = (level) => {
  const r = (level / 5) * maxR
  return angles.map((_, idx) => {
    const p = getPointAt(idx, r)
    return `${p.x},${p.y}`
  }).join(' ')
}

const axisOuterPoints = computed(() => angles.map((_, idx) => getPointAt(idx, maxR)))

const getTickPoint = (axisIdx, level) => {
  return getPointAt(axisIdx, (level / 5) * maxR)
}

// 维度文字位置（微调距离，避免遮挡）
const labelPositions = [
  { x: 130, y: 15, anchor: 'middle' },
  { x: 218, y: 64, anchor: 'start' },
  { x: 218, y: 160, anchor: 'start' },
  { x: 130, y: 212, anchor: 'middle' },
  { x: 42, y: 160, anchor: 'end' },
  { x: 42, y: 64, anchor: 'end' }
]

// 获取每个顶点的即时坐标（拖拽时连续跟随手指，松手时离散吸附）
const getVertexPoint = (idx) => {
  let r
  if (isDragging.value && activeDragIndex.value === idx && dragRadius.value !== null) {
    r = dragRadius.value
  } else {
    const key = dimensions[idx].key
    const val = localForm[key] || 3
    r = (val / 5) * maxR
  }
  return getPointAt(idx, r)
}

// 我的习惯多边形 points 字符串
const myPolygon = computed(() => {
  return dimensions.map((_, idx) => {
    const p = getVertexPoint(idx)
    return `${p.x},${p.y}`
  }).join(' ')
})

// 室友平均多边形 points 字符串
const roomAvgPolygon = computed(() => {
  const mates = props.roomRadar?.roommates || []
  if (!mates.length) return ''
  const avg = [0, 0, 0, 0, 0, 0]
  mates.forEach(m => {
    const v = m.vector || [3, 3, 3, 3, 3, 5]
    for (let i = 0; i < 6; i++) avg[i] += v[i]
  })
  return avg.map((sum, idx) => {
    const p = getPointAt(idx, (sum / mates.length / 5) * maxR)
    return `${p.x},${p.y}`
  }).join(' ')
})

// 浮动气泡位置
const floatingBubblePos = computed(() => {
  if (activeDragIndex.value < 0) return { x: center.x, y: center.y }
  const pt = getVertexPoint(activeDragIndex.value)
  // 根据所在象限偏置气泡，避免手指遮挡
  const angle = angles[activeDragIndex.value]
  const ox = Math.cos(angle) * 12
  const oy = Math.sin(angle) * 12 - 8
  return {
    x: Math.max(30, Math.min(230, pt.x + ox)),
    y: Math.max(22, Math.min(200, pt.y + oy))
  }
})

// 坐标转换辅助函数
const getSvgCoords = (e) => {
  const svg = svgRef.value
  if (!svg) return { x: center.x, y: center.y }
  const pt = svg.createSVGPoint()
  const clientX = e.touches && e.touches.length ? e.touches[0].clientX : e.clientX
  const clientY = e.touches && e.touches.length ? e.touches[0].clientY : e.clientY
  if (clientX == null || clientY == null) return { x: center.x, y: center.y }
  pt.x = clientX
  pt.y = clientY
  const transformed = pt.matrixTransform(svg.getScreenCTM().inverse())
  return { x: transformed.x, y: transformed.y }
}

// 手势滑动核心算法
const updateDragFromPointer = (e) => {
  if (activeDragIndex.value < 0) return
  const coords = getSvgCoords(e)
  const idx = activeDragIndex.value
  const angle = angles[idx]
  const ux = Math.cos(angle)
  const uy = Math.sin(angle)

  const dx = coords.x - center.x
  const dy = coords.y - center.y

  // 沿该轴向投影标量距离
  const proj = dx * ux + dy * uy

  // 限制拖拽半径在 [0.18 * maxR, 1.05 * maxR] 范围内弹性跟随
  const clampedR = Math.max(maxR * 0.16, Math.min(maxR * 1.04, proj))
  dragRadius.value = clampedR

  // 映射到离散 1~5 分档位并吸附
  const rawScore = (clampedR / maxR) * 5
  let snapped = Math.round(rawScore)
  if (snapped < 1) snapped = 1
  if (snapped > 5) snapped = 5

  const key = dimensions[idx].key
  if (localForm[key] !== snapped) {
    localForm[key] = snapped
    if (navigator.vibrate) navigator.vibrate(10)
    emitSync(key, snapped)
  }
}

const emitSync = (key, val) => {
  const updated = { ...localForm }
  emit('update:modelValue', updated)
  emit('change', { key, value: val })
}

// 触摸/指针按下开始拖拽
const startDrag = (idx, e) => {
  if (props.readonly) return
  activeDragIndex.value = idx
  isDragging.value = true
  updateDragFromPointer(e)

  window.addEventListener('pointermove', onPointerMove, { passive: false })
  window.addEventListener('pointerup', onPointerUp)
  window.addEventListener('pointercancel', onPointerUp)
  window.addEventListener('touchmove', onTouchMove, { passive: false })
  window.addEventListener('touchend', onPointerUp)
}

// 点击轴线任意位置直接设定档位
const onAxisPointerDown = (idx, e) => {
  if (props.readonly) return
  activeDragIndex.value = idx
  isDragging.value = true
  updateDragFromPointer(e)

  window.addEventListener('pointermove', onPointerMove, { passive: false })
  window.addEventListener('pointerup', onPointerUp)
  window.addEventListener('pointercancel', onPointerUp)
  window.addEventListener('touchmove', onTouchMove, { passive: false })
  window.addEventListener('touchend', onPointerUp)
}

const onPointerMove = (e) => {
  if (!isDragging.value) return
  e.preventDefault()
  updateDragFromPointer(e)
}

const onTouchMove = (e) => {
  if (!isDragging.value) return
  e.preventDefault()
  updateDragFromPointer(e)
}

const onPointerUp = () => {
  if (isDragging.value) {
    isDragging.value = false
    dragRadius.value = null
    activeDragIndex.value = -1
  }
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', onPointerUp)
}

// 点击标签循环步进切换
const stepDimension = (idx) => {
  const key = dimensions[idx].key
  const cur = localForm[key] || 3
  const next = cur >= 5 ? 1 : cur + 1
  localForm[key] = next
  if (navigator.vibrate) navigator.vibrate(8)
  emitSync(key, next)
}

onBeforeUnmount(() => {
  onPointerUp()
})
</script>

<style scoped>
.habit-radar-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  user-select: none;
  -webkit-user-select: none;
  touch-action: pan-y;
}

.drag-hint-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--accent-subtle, rgba(0, 110, 219, 0.08));
  border: 1px solid var(--border-subtle, rgba(0, 110, 219, 0.18));
  border-radius: 99px;
  padding: 4px 12px;
  margin-bottom: 8px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--accent-primary, #006edb);
  animation: hintPulse 2.8s ease-in-out infinite;
}

@keyframes hintPulse {
  0%, 100% { transform: scale(1); opacity: 0.95; }
  50% { transform: scale(1.02); opacity: 1; }
}

.hint-icon {
  font-size: 13px;
}

.r-sub {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--accent-primary, #006edb);
  margin-bottom: 4px;
}

.svg-radar-box {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.radar-svg {
  width: 100%;
  max-width: 290px;
  height: 228px;
  overflow: visible;
  touch-action: none;
}

.axis-hit-line {
  cursor: pointer;
}

.axis-txt {
  font-size: 11px;
  fill: var(--text-secondary, #475569);
  font-weight: 600;
  cursor: pointer;
  transition: fill 0.2s ease;
}

.axis-txt.active {
  fill: var(--accent-primary, #006edb);
  font-weight: 700;
}

.score-tspan {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: -0.2px;
}

.handle-dot {
  cursor: grab;
  transition: r 0.16s cubic-bezier(0.25, 1.4, 0.5, 1);
}

.habit-radar-container.dragging .handle-dot {
  cursor: grabbing;
}

.pulse-ring {
  animation: ringPulse 1.4s ease-out infinite;
  transform-origin: center;
}

@keyframes ringPulse {
  0% { transform: scale(0.9); opacity: 0.8; }
  50% { transform: scale(1.2); opacity: 0.3; }
  100% { transform: scale(0.9); opacity: 0.8; }
}

.handle-hit-circle {
  cursor: grab;
}

.radar-legend {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 11.5px;
  color: var(--text-secondary, #64748b);
  margin-top: 2px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}

.dot.primary { background: var(--accent-primary, #006edb); }
.dot.slate { background: #94a3b8; }

.compact .radar-svg {
  max-width: 260px;
  height: 205px;
}
</style>
