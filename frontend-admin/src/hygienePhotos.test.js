import test from 'node:test'
import assert from 'node:assert/strict'
import { uploadHygienePhotos } from './hygienePhotos.js'

test('uploads selected hygiene photos and returns stored paths', async () => {
  const calls = []
  const createFormData = () => ({ entries: [], append(key, value) { this.entries.push([key, value]) } })
  const files = [{ name: 'one.jpg' }, { name: 'two.png' }]
  const paths = await uploadHygienePhotos(files, async (url, body) => {
    calls.push({ url, entries: body.entries })
    return { data: { url: `/uploads/hygiene/${calls.length}.jpg` } }
  }, createFormData)
  assert.deepEqual(paths, ['/uploads/hygiene/1.jpg', '/uploads/hygiene/2.jpg'])
  assert.deepEqual(calls.map(call => call.url), ['/upload/image', '/upload/image'])
  assert.deepEqual(calls[0].entries, [['file', files[0]], ['biz', 'hygiene']])
})

test('rejects an incomplete upload response', async () => {
  await assert.rejects(uploadHygienePhotos([{ name: 'one.jpg' }], async () => ({ data: {} }),
    () => ({ append() {} })), /未返回图片地址/)
})
