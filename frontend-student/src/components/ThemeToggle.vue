<template>
  <button type="button" class="theme-toggle" :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'" :title="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'" @click="toggle">
    <span class="theme-toggle-icon" aria-hidden="true">{{ theme === 'dark' ? '☀' : '☾' }}</span>
    <span class="theme-toggle-label">{{ theme === 'dark' ? '浅色' : '深色' }}</span>
  </button>
</template>

<script setup>
import { themeStore } from '../theme'
const { theme, toggle } = themeStore
</script>

<style scoped>
.theme-toggle { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 36px; padding: 0 11px; border: 1px solid var(--border-subtle); border-radius: 12px; background: var(--bg-surface); color: var(--text-secondary); font: inherit; font-size: 12px; cursor: pointer; transition: transform 180ms ease, background-color 180ms ease; }
.theme-toggle:active { transform: scale(.96); }
.theme-toggle:focus-visible { outline: 3px solid #95bff0; outline-offset: 3px; }
.theme-toggle-icon { font-size: 18px; line-height: 1; }
@media (prefers-reduced-motion: reduce) { .theme-toggle { transition: none; } }
</style>
