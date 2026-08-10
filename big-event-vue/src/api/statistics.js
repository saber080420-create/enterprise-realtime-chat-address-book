//导入request.js请求工具
import request from '@/utils/request.js'

/**
 * 获取系统概览统计数据
 * @returns {Promise} 返回系统概览统计数据
 */
export const getSystemOverviewService = () => {
  return request.get('/statistics/overview')
}

/**
 * 获取在线用户数量
 */
export const getOnlineUserCountService = () => {
  return request.get('/user/activity/online-count')
}

/**
 * 获取部门用户统计数据
 * @returns {Promise} 返回部门用户统计数据
 */
export const getDepartmentUserStatsService = () => {
  return request.get('/statistics/department/users')
}

/**
 * 获取部门在线时长统计数据
 * @param {number} days - 天数
 * @returns {Promise} 返回部门在线时长统计数据
 */
export const getDepartmentOnlineTimeStatsService = (days = 30) => {
  return request.get('/user/activity/department/online-time', {
    params: { days }
  })
}

/**
 * 获取消息类型统计数据
 * @returns {Promise} 返回消息类型统计数据
 */
export const getMessageTypeStatsService = () => {
  return request.get('/statistics/messages/types')
}

/**
 * 获取每日消息统计数据
 * @param {number} days - 天数
 * @returns {Promise} 返回每日消息统计数据
 */
export const getDailyMessageStatsService = (days = 7) => {
  return request.get('/statistics/messages/daily', {
    params: { days }
  })
}

/**
 * 获取每日活跃用户统计数据
 * @param {number} days - 天数
 * @returns {Promise} 返回每日活跃用户统计数据
 */
export const getDailyActiveUserStatsService = (days = 30) => {
  return request.get('/statistics/users/active/daily', {
    params: { days }
  })
}

/**
 * 获取公告阅读统计数据
 * @returns {Promise} 返回公告阅读统计数据
 */
export const getAnnouncementReadStatsService = () => {
  return request.get('/statistics/announcements/read')
}

/**
 * 获取完整的统计数据
 * @param {string} startDate - 开始日期（格式：yyyy-MM-dd）
 * @param {string} endDate - 结束日期（格式：yyyy-MM-dd）
 * @returns {Promise} 返回统计数据对象
 */
export const getFullStatisticsService = (startDate, endDate) => {
  return request.get('/statistics/full', {
    params: { startDate, endDate }
  })
}