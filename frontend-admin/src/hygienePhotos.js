export async function uploadHygienePhotos(files, post, createFormData = () => new FormData()) {
  const urls = []
  for (const file of files) {
    const body = createFormData()
    body.append('file', file)
    body.append('biz', 'hygiene')
    const result = await post('/upload/image', body)
    if (!result?.data?.url) throw new Error('上传成功但未返回图片地址')
    urls.push(result.data.url)
  }
  return urls
}
