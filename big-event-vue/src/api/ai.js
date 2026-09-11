import { getToken } from '@/utils/auth'
import { consumeSse } from '@/utils/sse'

async function checkedFetch(path, options = {}) {
  const response = await fetch(`/api/ai/${path}`, {
    ...options,
    headers: { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), Authorization: getToken() || '' },
  })
  if (!response.ok) {
    if (response.status === 401) throw new Error('登录已失效，请重新登录')
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || `请求失败（${response.status}）`)
  }
  return response
}

export async function getAiStatus(signal) {
  const response = await checkedFetch('status', { signal })
  const body = await response.json()
  if (body.code !== 0) throw new Error('无法获取 AI 状态')
  return body.data
}

export async function aiConfig(method = 'GET', apiKey, test = false) {
  const response = await checkedFetch(`config${test ? '/test' : ''}`, {
    method, cache: 'no-store', signal: AbortSignal.timeout(20000),
    body: apiKey == null ? undefined : JSON.stringify({ apiKey }),
  })
  const body = await response.json()
  if (body.code !== 0) throw new Error(body.message || '模型配置失败')
  return body.data
}

export async function streamAiChat(messages, signal, onEvent, mode = 'general', summary = {}) {
  const response = await checkedFetch('chat', { method: 'POST', body: JSON.stringify({ messages, mode, groupId: summary.groupId, start: summary.start, end: summary.end }), signal })
  if (!response.headers.get('content-type')?.includes('text/event-stream')) {
    throw new Error('服务未返回流式回答，请稍后重试')
  }
  await consumeSse(response.body, onEvent)
}

export async function getAnnouncementSource(id, signal) {
  const response = await checkedFetch(`knowledge/announcements/${id}`, { signal })
  const body = await response.json()
  if (body.code !== 0) throw new Error(body.message || '来源读取失败')
  return body.data
}

export async function cancelAiChat(requestId) {
  const response = await checkedFetch(`chat/${encodeURIComponent(requestId)}/cancel`, {
    method: 'POST', signal: AbortSignal.timeout(15000),
  })
  const body = await response.json()
  if (body.code !== 0) throw new Error(body.message || '取消生成失败')
}

export async function knowledgeIndex(method = 'GET', signal) {
  const response = await checkedFetch('knowledge/index', { method, signal })
  const body = await response.json()
  if (body.code !== 0) throw new Error(body.message || '索引操作失败')
  return body.data
}

export async function documentRequest(method = 'GET', id, file, signal) {
  let form
  if (file) { form = new FormData(); form.append('file', file) }
  const response = await checkedFetch(`knowledge/documents${id == null ? '' : `/${encodeURIComponent(id)}`}`, {
    method, body: form, signal,
  })
  const body = await response.json()
  if (body.code !== 0) throw new Error(body.message || '文档操作失败')
  return body.data
}

export async function groupSummaryRead(groupId, messageId, signal) {
  const path = groupId == null ? 'groups' : `groups/${encodeURIComponent(groupId)}/messages/${encodeURIComponent(messageId)}`
  const body = await (await checkedFetch(path, { signal })).json()
  if (body.code !== 0) throw new Error(body.message || '群聊资料读取失败')
  return body.data
}
