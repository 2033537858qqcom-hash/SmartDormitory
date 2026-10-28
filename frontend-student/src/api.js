import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('dorm_student_token')
  if (token) config.headers.Authorization = token
  return config
})

api.interceptors.response.use(res => {
  if (res.data?.code && res.data.code !== 200) return Promise.reject(new Error(res.data.message || '请求失败'))
  return res.data
}, error => {
  if (error.response?.status === 401) window.dispatchEvent(new Event('student-session-expired'))
  return Promise.reject(error)
})

export default api
