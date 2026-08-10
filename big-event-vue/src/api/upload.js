import request from '@/utils/request'

/**
 * 上传聊天图片
 * 函数级注释：
 * - 以 multipart/form-data 方式上传图片文件到后端 /upload/chat/image
 * - 支持上传进度回调（onProgress: (percent: number [0-100]) => void）与取消（传入 AbortController.signal）
 * - 成功时返回后端的标准响应（{ code, data: { url, name, size, mime, width, height }, message }）
 * @param {File|Blob} file - 待上传的图片文件
 * @param {Object} [options] - 可选项 { onProgress?: Function, signal?: AbortSignal }
 * @returns {Promise<Object>} - 后端响应
 */
export const uploadChatImageService = (file, options = {}) => {
  const { onProgress, signal } = options
  const formData = new FormData()
  formData.append('file', file)

  return request.post('/upload/chat/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    signal,
    onUploadProgress: (evt) => {
      try {
        const total = evt.total || evt.srcElement?.getResponseHeader?.('Content-Length') || 0
        const loaded = evt.loaded || 0
        // axios v1 提供 evt.progress(0~1)
        const ratio = typeof evt.progress === 'number' && evt.progress >= 0 ? evt.progress : (total ? loaded / total : 0)
        const percent = Math.max(0, Math.min(100, Math.round(ratio * 100)))
        if (typeof onProgress === 'function') onProgress(percent)
      } catch (_) {
        // 忽略进度计算异常
      }
    }
  })
}

/**
 * 上传聊天文件（非图片）
 * 函数级注释：
 * - 以 multipart/form-data 方式上传通用文件到后端 /upload/chat/file
 * - 支持上传进度回调与取消；大小与类型限制由后端校验（≤20MB，白名单）
 * - 成功时返回后端的标准响应（{ code, data: { url, name, size, mime }, message }）
 * @param {File|Blob} file - 待上传的文件
 * @param {Object} [options] - 可选项 { onProgress?: Function, signal?: AbortSignal }
 * @returns {Promise<Object>} - 后端响应
 */
export const uploadChatFileService = (file, options = {}) => {
  const { onProgress, signal } = options
  const formData = new FormData()
  formData.append('file', file)

  return request.post('/upload/chat/file', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    signal,
    onUploadProgress: (evt) => {
      try {
        const total = evt.total || 0
        const loaded = evt.loaded || 0
        const ratio = typeof evt.progress === 'number' && evt.progress >= 0 ? evt.progress : (total ? loaded / total : 0)
        const percent = Math.max(0, Math.min(100, Math.round(ratio * 100)))
        if (typeof onProgress === 'function') onProgress(percent)
      } catch (_) {
        // 忽略进度计算异常
      }
    }
  })
}

export default {
  uploadChatImageService,
  uploadChatFileService
}