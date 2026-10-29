import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createThemeStore } from './theme.js'

test('theme switching applies and persists a choice across reloads', () => {
  const values = new Map()
  const storage = { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value) }
  const classes = new Set()
  const root = { classList: { toggle: (name, on) => on ? classes.add(name) : classes.delete(name) } }
  const first = createThemeStore(storage, root)
  assert.equal(first.theme.value, 'light')
  first.toggle()
  assert.equal(first.theme.value, 'dark')
  assert.equal(classes.has('dark'), true)
  const restored = createThemeStore(storage, root)
  assert.equal(restored.theme.value, 'dark')
  restored.toggle()
  assert.equal(classes.has('dark'), false)
  assert.equal(values.get('dorm_theme'), 'light')
})
