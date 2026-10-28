export async function signInStudent(api, storage, username, password) {
  const result = await api.post('/auth/login', { username, password })
  if (result.code !== 200) throw new Error(result.message || '登录失败')
  if (result.data?.user?.role !== 'STUDENT' || !result.data?.token || !result.data?.user?.id) {
    throw new Error('请使用学生账号登录')
  }
  storage.setItem('dorm_student_token', result.data.token)
  storage.setItem('dorm_student_id', String(result.data.user.id))
  return result.data.user.id
}
