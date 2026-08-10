//导入request.js请求工具
import request from '@/utils/request.js'

/**
 * 获取公告列表（分页）
 * @param {number} page - 页码
 * @param {number} pageSize - 每页数量
 * @param {string} type - 公告类型（可选，company或department）
 * @returns {Promise} 返回公告列表和分页信息
 */
export const getAnnouncementsService = (page = 1, pageSize = 10, type = null) => {
  const params = {}
  params.page = page
  params.pageSize = pageSize
  if (type) params.type = type
  
  return request.get('/announcement', { params })
}

/**
 * 根据ID获取公告详情
 * @param {number} id - 公告ID
 * @returns {Promise} 返回公告详情
 */
export const getAnnouncementByIdService = (id) => {
  return request.get(`/announcement/${id}`)
}

/**
 * 创建公告
 * @param {Object} announcementData - 公告数据
 * @returns {Promise} 返回创建结果
 */
export const createAnnouncementService = (announcementData) => {
  return request.post('/announcement', announcementData)
}

/**
 * 更新公告
 * @param {number} id - 公告ID
 * @param {Object} announcementData - 公告数据
 * @returns {Promise} 返回更新结果
 */
export const updateAnnouncementService = (id, announcementData) => {
  return request.put(`/announcement/${id}`, announcementData)
}

/**
 * 删除公告
 * @param {number} id - 公告ID
 * @returns {Promise} 返回删除结果
 */
export const deleteAnnouncementService = (id) => {
  return request.delete(`/announcement/${id}`)
}

/**
 * 更新公告状态
 * @param {number} id - 公告ID
 * @param {string} status - 状态（draft, published, revoked）
 * @returns {Promise} 返回更新结果
 */
export const updateAnnouncementStatusService = (id, status) => {
  return request.put(`/announcement/${id}/status`, { status })
}

/**
 * 获取重要公告
 * @returns {Promise} 返回重要公告列表
 */
export const getImportantAnnouncementsService = () => {
  return request.get('/announcement/important')
}

/**
 * 获取最新公告
 * @param {number} limit - 数量限制
 * @returns {Promise} 返回最新公告列表
 */
export const getLatestAnnouncementsService = (limit = 5) => {
  return request.get('/announcement/latest', { params: { limit } })
}

/**
 * 根据标题搜索公告
 * @param {string} title - 公告标题
 * @returns {Promise} 返回搜索结果
 */
export const searchAnnouncementsByTitleService = (title) => {
  return request.get('/announcement/search', { params: { title } })
}

/**
 * 获取用户创建的公告
 * @returns {Promise} 返回用户创建的公告列表
 */
export const getCreatedAnnouncementsService = () => {
  return request.get('/announcement/created')
}

/**
 * 标记公告为已读
 * @param {number} announcementId - 公告ID
 * @returns {Promise} 返回标记结果
 */
export const markAnnouncementAsReadService = (announcementId) => {
  return request.post(`/announcement/read-status/mark-read/${announcementId}`)
}

/**
 * 批量标记公告为已读
 * @param {Array<number>} announcementIds - 公告ID数组
 * @returns {Promise} 返回标记结果
 */
export const batchMarkAnnouncementsAsReadService = (announcementIds) => {
  return request.post('/announcement/read-status/mark-read-batch', announcementIds)
}

/**
 * 获取公告阅读状态
 * @param {number} announcementId - 公告ID
 * @returns {Promise} 返回阅读状态
 */
export const getAnnouncementReadStatusService = (announcementId) => {
  return request.get(`/announcement/read-status/status/${announcementId}`)
}

/**
 * 获取公告阅读统计
 * @param {number} announcementId - 公告ID
 * @returns {Promise} 返回阅读统计
 */
export const getAnnouncementReadStatsService = (announcementId) => {
  // 修改为使用正确的后端API路径
  return request.get(`/announcement/read-status/read-count/${announcementId}`)
}

/**
 * 获取公告已读用户详情列表
 * @param {number} announcementId - 公告ID
 * @returns {Promise} 返回已读用户详情列表
 */
export const getAnnouncementReadUsersService = (announcementId) => {
  return request.get(`/announcement/read-status/read-users/${announcementId}`)
}

// 删除已读公告会话（仅删除当前用户的阅读记录）
export const deleteReadAnnouncementConversationService = (announcementId) => {
  return request.delete(`/announcement/read-status/delete-read/${announcementId}`)
}

/**
 * 获取用户未读公告数量
 * @returns {Promise} 返回未读公告数量
 */
export const getUnreadAnnouncementCountService = () => {
  return request.get('/announcement/read-status/unread-count')
}

/**
 * 获取用户未读公告ID列表
 * @returns {Promise} 返回未读公告ID数组
 */
export const getUnreadAnnouncementIdsService = () => {
  return request.get('/announcement/read-status/unread-list')
}