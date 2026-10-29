<template>
  <section class="residence-card" id="residence-requests">
    <div class="residence-head"><h3>入住与假期留宿</h3><button type="button" @click="showForm = !showForm">{{ showForm ? '收起' : '+ 提交申请' }}</button></div>
    <form v-if="showForm" @submit.prevent="submit">
      <label>申请类型<select v-model="form.requestType"><option value="CHECKIN">入住登记</option><option value="HOLIDAY">假期留宿</option></select></label>
      <template v-if="form.requestType === 'HOLIDAY'"><label>开始日期<input v-model="form.fromDate" type="date" /></label><label>结束日期<input v-model="form.toDate" type="date" /></label></template>
      <label>申请说明<textarea v-model="form.reason" rows="2" maxlength="500" /></label>
      <button type="submit" :disabled="saving">{{ saving ? '提交中…' : '提交审核' }}</button>
    </form>
    <p v-if="loading">正在加载申请…</p><p v-else-if="error" role="alert">{{ error }} <button type="button" @click="load">重试</button></p>
    <p v-else-if="!items.length">暂无入住或留宿申请</p>
    <article v-for="item in items" :key="item.id" class="residence-item" :data-message-key="`RESIDENCE:${item.id}`">
      <b>{{ item.requestType === 'CHECKIN' ? '入住登记' : '假期留宿' }} · {{ statusText(item.status) }}</b>
      <small v-if="item.requestType === 'HOLIDAY'">{{ item.fromDate }} 至 {{ item.toDate }}</small>
      <small>{{ item.reason }}</small><small v-if="item.reviewComment">审核意见：{{ item.reviewComment }}</small>
    </article>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch, nextTick } from 'vue'
import api from '../api'
import { residenceError } from '../residence'

const props = defineProps({ targetKey: { type: String, default: '' } })
const emit = defineEmits(['toast'])
const items = ref([]), loading = ref(false), saving = ref(false), showForm = ref(false), error = ref('')
const form = reactive({ requestType: 'CHECKIN', fromDate: '', toDate: '', reason: '' })
const statusText = status => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status)
const focusTarget = async () => {
  if (!props.targetKey) return
  await nextTick()
  const item = [...document.querySelectorAll('[data-message-key]')].find(node => node.dataset.messageKey === props.targetKey)
  if (item) item.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
const load = async () => {
  loading.value = true; error.value = ''
  try { const result = await api.get('/residence/requests'); items.value = result.data || []; await focusTarget() }
  catch (e) { error.value = e.message || '加载失败' }
  finally { loading.value = false }
}
const submit = async () => {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
  const problem = residenceError(form, today)
  if (problem) return emit('toast', { msg: problem, type: 'warning' })
  saving.value = true
  try {
    await api.post('/residence/requests', form)
    showForm.value = false; form.reason = ''; await load()
    emit('toast', { msg: '住宿申请已提交', type: 'success' })
  } catch (e) { emit('toast', { msg: e.message || '提交失败', type: 'error' }) }
  finally { saving.value = false }
}
watch(() => props.targetKey, focusTarget)
onMounted(load)
</script>

<style scoped>
.residence-card { background: var(--bg-surface); border-radius: 18px; padding: 18px; margin: 16px 0; }
.residence-head { display: flex; align-items: center; justify-content: space-between; }.residence-head h3 { margin: 0; font-size: 16px; }
button { border: 0; background: #e9f2ff; color: #145bad; border-radius: 8px; padding: 8px 10px; cursor: pointer; }
form { display: grid; gap: 10px; margin: 16px 0; } label { display: grid; gap: 5px; font-size: 13px; } input, select, textarea { width: 100%; padding: 9px; border: 1px solid #d5d5da; border-radius: 8px; font: inherit; }
.residence-card p, .residence-item small { color: #666; font-size: 12px; }.residence-item { display: grid; gap: 5px; padding: 12px 0; border-bottom: 1px solid #eee; }
</style>
