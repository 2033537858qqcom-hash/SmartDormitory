import { ref } from 'vue'

const STORAGE_KEY = 'dorm_theme'

export function createThemeStore(storage, root) {
  let saved
  try { saved = storage?.getItem(STORAGE_KEY) } catch (_) { /* storage may be unavailable */ }
  const theme = ref(saved === 'dark' ? 'dark' : 'light')
  const apply = () => root?.classList.toggle('dark', theme.value === 'dark')
  const toggle = () => {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    apply()
    try { storage?.setItem(STORAGE_KEY, theme.value) } catch (_) { /* keep the current theme for this session */ }
  }
  apply()
  return { theme, toggle }
}

let storage
try { if (globalThis.document) storage = globalThis.localStorage } catch (_) { /* storage may be unavailable */ }
export const themeStore = createThemeStore(storage, globalThis.document?.documentElement)
