<template>
  <transition name="mp-sheet">
    <div v-if="modelValue" class="sheet-scrim" @click.self="close">
      <div
        ref="panel"
        class="sheet-panel"
        :class="{ 'is-dragging': dragging }"
        :style="panelStyle"
        role="dialog"
        aria-modal="true"
        :aria-label="title || '详情'"
        @keydown="onKeydown"
      >
        <!-- 下拉拖拽把手区：手势仅绑定在顶部把手和标题栏，正文滚动不冲突 -->
        <div
          class="sheet-grabber-zone"
          aria-label="下拉关闭弹窗"
          @touchstart.passive="onStart"
          @touchmove.passive="onMove"
          @touchend="onEnd"
          @mousedown="onMouseDown"
        >
          <div class="sheet-handle"></div>
        </div>
        <div
          v-if="title"
          class="sheet-header"
          @touchstart.passive="onStart"
          @touchmove.passive="onMove"
          @touchend="onEnd"
        >
          <h3 class="sheet-title">{{ title }}</h3>
          <button class="sheet-close-btn spring-btn" aria-label="关闭弹窗" @click="close">✕</button>
        </div>
        <!-- 正文独立滚动区，限制最大高度，防止软键盘或长内容溢出 -->
        <div class="sheet-body">
          <slot />
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const dragging = ref(false)
const deltaY = ref(0)
const panel = ref(null)
let previousFocus = null
let startY = 0
let startTime = 0

const panelStyle = computed(() => {
  if (deltaY.value <= 0) return {}
  return {
    transform: `translate3d(0, ${deltaY.value}px, 0)`
  }
})

const close = () => {
  deltaY.value = 0
  dragging.value = false
  emit('update:modelValue', false)
}

watch(() => props.modelValue, async (open) => {
  if (open) {
    previousFocus = document.activeElement
    await nextTick()
    panel.value?.querySelector('.sheet-close-btn')?.focus()
  } else {
    previousFocus?.focus?.()
    previousFocus = null
  }
})

const onKeydown = (event) => {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (event.key !== 'Tab' || !panel.value) return
  const focusable = Array.from(panel.value.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'))
    .filter((element) => element.getClientRects().length > 0)
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

const onStart = (e) => {
  if (!e.touches || !e.touches.length) return
  startY = e.touches[0].clientY
  startTime = performance.now()
  dragging.value = true
}

const onMove = (e) => {
  if (!dragging.value || !e.touches || !e.touches.length) return
  const diff = e.touches[0].clientY - startY
  deltaY.value = diff > 0 ? diff : 0
}

const onEnd = () => {
  if (!dragging.value) return
  dragging.value = false
  const elapsed = Math.max(1, performance.now() - startTime)
  const velocity = deltaY.value / elapsed // px per ms
  if (deltaY.value > 80 || velocity > 0.6) {
    close()
  } else {
    deltaY.value = 0
  }
}

const onMouseDown = (e) => {
  startY = e.clientY
  startTime = performance.now()
  dragging.value = true
  const moveHandler = (ev) => {
    const diff = ev.clientY - startY
    deltaY.value = diff > 0 ? diff : 0
  }
  const upHandler = () => {
    window.removeEventListener('mousemove', moveHandler)
    window.removeEventListener('mouseup', upHandler)
    onEnd()
  }
  window.addEventListener('mousemove', moveHandler)
  window.addEventListener('mouseup', upHandler)
}
</script>

<style scoped>
.sheet-scrim { position: absolute; inset: 0; background: rgb(29 29 31 / 27%); z-index: 60; display: flex; align-items: flex-end; }
.sheet-panel {
  width: 100%;
  max-height: 88%;
  max-height: min(88%, calc(100dvh - 30px));
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-radius: 28px 28px 0 0;
  padding: 8px 23px calc(16px + env(safe-area-inset-bottom, 0px));
  box-shadow: 0 -8px 28px rgba(15, 23, 42, 0.14);
  transition: transform 190ms ease-out;
  overflow: hidden;
}
.sheet-panel.is-dragging {
  transition: none;
}
.sheet-grabber-zone {
  padding: 8px 0 6px;
  cursor: grab;
  touch-action: none;
  flex-shrink: 0;
}
.sheet-handle {
  width: 38px;
  height: 4px;
  background: var(--border-strong);
  border-radius: 99px;
  margin: 0 auto;
}
.sheet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-shrink: 0;
  touch-action: none;
}
.sheet-title {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -.35px;
  color: var(--text-primary);
}
.sheet-close-btn { width: 36px; height: 36px; flex-shrink: 0; border-radius: 50%; border: 0; background: var(--bg-canvas); color: var(--text-muted); font-size: 14px; display: grid; place-items: center; }
.sheet-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  padding: 2px 2px 10px;
}
.sheet-body::-webkit-scrollbar {
  width: 4px;
}
.sheet-body::-webkit-scrollbar-thumb {
  background: var(--border-subtle);
  border-radius: 4px;
}

/* 遮罩与面板分离合成层过渡 */
.mp-sheet-enter-active,
.mp-sheet-leave-active {
  transition: opacity 180ms ease-out;
}
.mp-sheet-enter-active .sheet-panel,
.mp-sheet-leave-active .sheet-panel {
  transition: transform 190ms ease-out;
}
.mp-sheet-enter-from,
.mp-sheet-leave-to {
  opacity: 0;
}
.mp-sheet-enter-from .sheet-panel,
.mp-sheet-leave-to .sheet-panel {
  transform: translate3d(0, 100%, 0);
}
</style>
