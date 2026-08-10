/*
 * WebSocket 服务封装
 * 提供统一的连接管理与事件分发：connect、disconnect、onMessage、offMessage、onClose、onError、send
 * - 网关路径：开发环境通过 Vite 代理到 /api/ws（vite.config.js 中对 /api/ws 不做重写）
 * - 鉴权：从 localStorage 读取 token，拼到 query 参数中 ?token=xxx
 * - 消息格式：后端建议格式 { type: string, data: any }，按 type 分发给对应监听器
 */

import router from '@/router'
import { removeToken } from '@/utils/auth.js'
import { useUserStore } from '@/stores/user'

let socket = null
const messageListeners = new Map() // Map<string, Set<Function>>
const closeHandlers = new Set()
const errorHandlers = new Set()

/**
 * 建立 WebSocket 连接
 * 函数级注释：
 * - 若已有连接且未关闭，直接返回已连接状态
 * - 根据当前页面协议自动选择 ws/wss，并通过 Vite 代理连接 /api/ws
 * - 携带 localStorage 中的 token 作为 query 参数
 * - 在 onopen 时完成连接就绪
 * @returns {Promise<void>}
 */
export async function connect () {
  if (socket && socket.readyState === WebSocket.OPEN) return
  if (socket && socket.readyState === WebSocket.CONNECTING) {
    // 等待已存在的连接完成
    await new Promise(resolve => {
      const check = () => {
        if (!socket || socket.readyState === WebSocket.CLOSED) return resolve()
        if (socket.readyState === WebSocket.OPEN) return resolve()
        setTimeout(check, 50)
      }
      check()
    })
    if (socket && socket.readyState === WebSocket.OPEN) return
  }

  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const host = location.host // 例如 localhost:5173
  const token = localStorage.getItem('token') || ''
  const query = token ? `?token=${encodeURIComponent(token)}` : ''
  const url = `${protocol}://${host}/api/ws${query}`

  socket = new WebSocket(url)

  socket.onopen = () => {
    // 连接建立完成
  }

  socket.onmessage = (evt) => {
    let payload = evt.data
    try {
      payload = JSON.parse(evt.data)
    } catch (e) {
      payload = { type: 'message', data: evt.data }
    }

    const type = payload && payload.type ? payload.type : 'message'
    const data = payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload

    // 调试日志：记录所有 WebSocket 消息类型
    console.log('WebSocket收到消息:', { type, hasData: !!data })

    // 分发给对应的监听器
    const set = messageListeners.get(type)
    if (set && set.size) {
      for (const handler of set) {
        try { handler(data, type, evt) } catch (err) { console.error('WebSocket 消息处理错误:', err) }
      }
    }

    // 通配符监听（可选）：监听所有类型
    const anySet = messageListeners.get('*')
    if (anySet && anySet.size) {
      for (const handler of anySet) {
        try { handler(data, type, evt) } catch (err) { console.error('WebSocket 消息处理错误(*):', err) }
      }
    }

    // 内置处理：强制下线通知
    if (type === 'notification' && data && data.event === 'force_logout') {
      try { removeToken() } catch (_) {}
      try { useUserStore().clear() } catch (_) {}
      try {
        // 若当前不在登录页，跳转并提示
        if (router.currentRoute?.value?.path !== '/login') {
          router.push({ path: '/login', query: { reason: 'force_logout' } })
        }
      } catch (_) {}
      try { window?.ElMessage?.closeAll?.() } catch (_) {}
      try { window?.ElMessage?.error?.('该账号已在另一台设备登录') } catch (_) {}
    }
  }

  socket.onclose = (evt) => {
    for (const cb of closeHandlers) {
      try { cb(evt) } catch (e) { console.error(e) }
    }
    socket = null
  }

  socket.onerror = (err) => {
    for (const cb of errorHandlers) {
      try { cb(err) } catch (e) { console.error(e) }
    }
  }
}

/**
 * 主动断开 WebSocket 连接
 * 函数级注释：如果 WebSocket 正处于 OPEN 或 CONNECTING 状态，则执行关闭操作
 */
export function disconnect () {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    socket.close()
  }
}

/**
 * 发送消息
 * 函数级注释：
 * - 仅在连接已打开时发送
 * - 自动将 payload 包装为 { type, data } 并序列化为 JSON
 * @param {string} type 事件类型
 * @param {any} data 数据
 */
export function send (type, data) {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    console.warn('WebSocket 未连接，消息未发送:', type, data)
    return
  }
  try {
    socket.send(JSON.stringify({ type, data }))
  } catch (e) {
    console.error('WebSocket 发送失败:', e)
  }
}

/**
 * 订阅消息
 * 函数级注释：为指定类型注册回调，支持同一类型多个订阅者
 * @param {string} type 事件类型
 * @param {Function} handler 回调函数
 */
export function onMessage (type, handler) {
  if (!messageListeners.has(type)) {
    messageListeners.set(type, new Set())
  }
  messageListeners.get(type).add(handler)
}

/**
 * 取消订阅
 * 函数级注释：从指定类型的订阅集合中移除回调
 * @param {string} type 事件类型
 * @param {Function} handler 回调函数
 */
export function offMessage (type, handler) {
  const set = messageListeners.get(type)
  if (!set) return
  set.delete(handler)
  if (set.size === 0) messageListeners.delete(type)
}

/**
 * 注册连接关闭回调
 * 函数级注释：用于监听连接关闭事件，常用于重连或清理状态
 * @param {Function} cb 回调函数
 */
export function onClose (cb) {
  closeHandlers.add(cb)
}

/**
 * 注册错误回调
 * 函数级注释：用于统一处理底层连接错误
 * @param {Function} cb 回调函数
 */
export function onError (cb) {
  errorHandlers.add(cb)
}

export default {
  connect,
  disconnect,
  send,
  onMessage,
  offMessage,
  onClose,
  onError
}