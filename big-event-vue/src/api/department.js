//导入request.js请求工具
import request from '@/utils/request.js'

/**
 * 获取所有部门列表
 * @returns {Promise} 返回部门列表
 */
export const getAllDepartmentsService = () => {
  return request.get('/department')
}

/**
 * 获取部门树形结构
 * @returns {Promise} 返回部门树形结构
 */
export const getDepartmentTreeService = () => {
  return request.get('/department/tree')
}

/**
 * 根据ID获取部门
 * @param {number} id - 部门ID
 * @returns {Promise} 返回部门信息
 */
export const getDepartmentByIdService = (id) => {
  return request.get(`/department/${id}`)
}

/**
 * 根据名称查询部门
 * @param {string} name - 部门名称
 * @returns {Promise} 返回部门信息
 */
export const searchDepartmentByNameService = (name) => {
  return request.get('/department/search', {
    params: { name }
  })
}

/**
 * 获取部门及其用户数量
 * @returns {Promise} 返回部门及用户数量列表
 */
export const getDepartmentsWithUserCountService = () => {
  return request.get('/department/withUserCount')
}

/**
 * 添加部门
 * @param {Object} departmentData - 部门数据
 * @returns {Promise} 返回添加结果
 */
export const addDepartmentService = (departmentData) => {
  return request.post('/department', departmentData)
}

/**
 * 更新部门
 * @param {number} id - 部门ID
 * @param {Object} departmentData - 部门数据
 * @returns {Promise} 返回更新结果
 */
export const updateDepartmentService = (id, departmentData) => {
  return request.put(`/department/${id}`, departmentData)
}

/**
 * 删除部门
 * @param {number} id - 部门ID
 * @returns {Promise} 返回删除结果
 */
export const deleteDepartmentService = (id) => {
  return request.delete(`/department/${id}`)
}

/**
 * 获取部门下的用户列表
 * @param {number} departmentId - 部门ID
 * @returns {Promise} 返回部门用户列表
 */
export const getDepartmentUsersService = (departmentId) => {
  return request.get(`/user/byDepartment/${departmentId}`)
}