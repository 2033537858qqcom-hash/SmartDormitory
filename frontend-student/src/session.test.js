import { test } from 'node:test'
import assert from 'node:assert/strict'
import { signInStudent } from './session.js'

test('student login persists a token and uses the authenticated student id', async () => {
  const saved = new Map()
  const storage = { setItem: (key, value) => saved.set(key, value) }
  const api = { post: async () => ({ code: 200, data: { token: 'opaque-token', user: { id: 21, role: 'STUDENT' } } }) }
  assert.equal(await signInStudent(api, storage, '20260101', 'secret'), 21)
  assert.equal(saved.get('dorm_student_token'), 'opaque-token')
  assert.equal(saved.get('dorm_student_id'), '21')
})

test('student login rejects errors and non-student accounts without storing credentials', async () => {
  const storage = { setItem: () => assert.fail('must not store credentials') }
  await assert.rejects(signInStudent({ post: async () => ({ code: 500, message: '登录失败' }) }, storage, 'x', 'y'), /登录失败/)
  await assert.rejects(signInStudent({ post: async () => ({ code: 200, data: { token: 'x', user: { id: 1, role: 'ADMIN' } } }) }, storage, 'x', 'y'), /学生账号/)
})
