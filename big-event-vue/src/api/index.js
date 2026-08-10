// 统一 API 门面（Lite 版）。
// 说明：仅新增，不影响原 api/*.js 的现有导出与调用。
// 使用方式：import api from '@/api'; 然后 api.account.login(data) 等。

import request from '@/utils/request'

const account = {
  // 与 /user/register 等价
  register(data) {
    const params = new URLSearchParams()
    Object.entries(data || {}).forEach(([k, v]) => {
      if (k === 'rePassword') return
      params.append(k, v)
    })
    return request.post('/account/register', params)
  },
  // 与 /user/login 等价
  login({ username, password }) {
    const params = new URLSearchParams()
    params.append('username', username)
    params.append('password', password)
    return request.post('/account/login', params)
  },
  me() {
    return request.get('/user/userInfo')
  },
  logout() {
    return request.post('/user/activity/logout')
  },
  // 用户信息与资料修改
  me() { return request.get('/user/userInfo') },
  updateNickname(nickname) {
    const p = new URLSearchParams(); p.append('nickname', nickname); return request.put('/user/update/nickname', p)
  },
  updateRealname(realname) {
    const p = new URLSearchParams(); p.append('realname', realname); return request.put('/user/update/realname', p)
  },
  updateEmail(email) {
    const p = new URLSearchParams(); p.append('email', email); return request.put('/user/update/email', p)
  },
  updatePhone(phone) {
    const p = new URLSearchParams(); p.append('phone', phone); return request.put('/user/update/phone', p)
  },
  updateAvatar(avatarUrl) {
    const p = new URLSearchParams(); p.append('avatarUrl', avatarUrl); return request.patch('/user/updateAvatar', p)
  },
  updatePassword(oldPwd, newPwd, rePwd) { return request.patch('/user/updatePwd', { old_pwd: oldPwd, new_pwd: newPwd, re_pwd: rePwd }) },
  loginActivity() { return request.post('/user/activity/login') }
}

const org = {
  // 部门
  allDepartments() { return request.get('/org/department') },
  departmentTree() { return request.get('/org/department/tree') },
  departmentById(id) { return request.get(`/org/department/${id}`) },
  addDepartment(payload) { return request.post('/org/department', payload) },
  updateDepartment(id, payload) { return request.put(`/org/department/${id}`, payload) },
  deleteDepartment(id) { return request.delete(`/org/department/${id}`) },
  // 通讯录
  groups() { return request.get('/org/addressbook/groups') },
  addContact(contactId, groupId = null, alias = '', remark = '') {
    return request.post('/org/addressbook/add', { contactId, groupId, alias, remark })
  },
  createGroup(name) { return request.post('/org/addressbook/groups', { groupName: name }) },
  deleteGroup(id) { return request.delete(`/org/addressbook/groups/${id}`) },
  renameGroup(id, groupName) { return request.put(`/org/addressbook/groups/${id}`, { groupName }) },
  listContacts(groupId) {
    if (groupId === null || groupId === undefined) {
      return request.get('/org/addressbook/contacts')
    }
    return request.get('/org/addressbook/contacts', { params: { groupId } })
  },
  addExternal(data) { return request.post('/org/addressbook/external/add', data) },
  editContact(contactId, payload) { return request.put(`/org/addressbook/contacts/${contactId}`, payload) },
  deleteContact(contactId) { return request.delete(`/org/addressbook/contacts/${contactId}`) },
  editContactByAbId(abId, payload) { return request.put(`/org/addressbook/ab/${abId}`, payload) },
  deleteContactByAbId(abId) { return request.delete(`/org/addressbook/ab/${abId}`) },
  // 部门成员（新门面）
  departmentUsers(id) { return request.get(`/org/department/${id}/users`) },
  // 用户搜索（用于添加到我的通讯录）
  searchUsers(keyword, limit = 20) {
    if (!keyword || !keyword.trim()) {
      return Promise.reject(new Error('搜索关键词不能为空'))
    }
    const params = {
      keyword: keyword.trim(),
      limit: Math.min(Math.max(1, limit || 20), 50)
    }
    return request.get('/user/search', { params })
  },
  // 常用联系人
  frequentList(limit = 10) { return request.get('/org/contact/frequent', { params: { limit } }) },
  deleteFrequent(contactId) { return request.delete(`/org/contact/frequent/${contactId}`) },
  favorite(contactId, isFavorite = true) { return request.put(`/org/contact/frequent/favorite/${contactId}`, null, { params: { isFavorite } }) }
}

const chat = {
  send(payload) { return request.post('/api/chat/message/send', payload) },
  edit(messageId, content) { return request.put(`/api/chat/message/edit/${messageId}`, { content }) },
  recall(messageId) { return request.put(`/api/chat/message/recall/${messageId}`) },
  delete(messageId) { return request.delete(`/api/chat/message/${messageId}`) },
  singleHistory(userId, limit = 15, offset = 0) {
    return request.get(`/api/chat/message/private/${userId}`, { params: { limit, offset } })
  },
  groupHistory(groupId, limit = 20, offset = 0) {
    return request.get(`/api/chat/message/group/${groupId}`, { params: { limit, offset } })
  },
  recent(limit = 20) { return request.get('/api/chat/message/recent', { params: { limit } }) },
  markRead(messageId) { return request.put(`/api/chat/message/markMessageAsRead/${messageId}`, {}) },
  markBatch(params) { return request.put('/api/chat/message/read/batch', null, { params }) },
  // 群
  createGroup(chatGroup, memberIds = []) {
    const params = new URLSearchParams()
    memberIds.forEach(id => params.append('memberIds', id))
    return request.post('/api/chat/group', chatGroup, { params })
  },
  myGroups() { return request.get('/api/chat/group/my') },
  group(id) { return request.get(`/api/chat/group/${id}`) },
  groupMembers(groupId) { return request.get(`/api/chat/group/member/list/${groupId}`) },
  addMembers(groupId, userIds = []) { return request.post(`/api/chat/group/member/add/${groupId}`, userIds) },
  removeMember(groupId, userId) { return request.delete(`/api/chat/group/member/remove/${groupId}/${userId}`) },
  leaveGroup(groupId) { return request.post(`/api/chat/group/member/leave/${groupId}`) },
  memberRole(groupId) { return request.get(`/api/chat/member/role/${groupId}`) },
  deleteGroup(groupId) { return request.delete(`/api/chat/group/${groupId}`) },
  // 群公告
  publishAnnouncement(groupId, { title, content }) { return request.post(`/api/chat/group/${groupId}/announcement`, { title, content }) },
  withdrawAnnouncement(groupId, id) { return request.post(`/api/chat/group/${groupId}/announcement/${id}/withdraw`) },
  listAnnouncements(groupId, limit = 20) { return request.get(`/api/chat/group/${groupId}/announcement/list`, { params: { limit } }) },
  markAnnouncementRead(id) { return request.post(`/api/chat/group/announcement/${id}/read`) },
  listAnnouncementReaders(id) { return request.get(`/api/chat/group/announcement/${id}/readers`) },
  announcementUnreadCount(groupId) { return request.get(`/api/chat/group/${groupId}/announcement/unread-count`) }
}

const notice = {
  // 系统公告（等价 /announcement/**）
  list(params) { return request.get('/notice', { params }) },
  get(id) { return request.get(`/notice/${id}`) },
  create(payload) { return request.post('/notice', payload) },
  update(id, payload) { return request.put(`/notice/${id}`, payload) },
  remove(id) { return request.delete(`/notice/${id}`) },
  important() { return request.get('/notice/important') },
  latest(limit = 5) { return request.get('/notice/latest', { params: { limit } }) },
  search(title) { return request.get('/notice/search', { params: { title } }) },
  mine() { return request.get('/notice/created') },
  setStatus(id, status) { return request.put(`/notice/${id}/status`, { status }) },
  // 已读接口
  markRead(id) { return request.post(`/notice/read/mark-read/${id}`) },
  markReadBatch(ids) { return request.post('/notice/read/mark-read-batch', ids) },
  readStatus(id) { return request.get(`/notice/read/status/${id}`) },
  unreadCount() { return request.get('/notice/read/unread-count') },
  readCount(id) { return request.get(`/notice/read/read-count/${id}`) },
  readList() { return request.get('/notice/read/read-list') },
  // 获取某公告的已读人员详情列表
  readUsers(id) { return request.get(`/notice/read/read-users/${id}`) },
  unreadList() { return request.get('/notice/read/unread-list') },
  // 删除我的已读公告会话（仅删除当前用户的阅读记录）
  deleteRead(id) { return request.delete(`/notice/read/delete-read/${id}`) },
  // 系统通知（等价 /systemNotice/**）
  inboxList(limit = 20, offset = 0) { return request.get('/notice/inbox/list', { params: { limit, offset } }) },
  inboxUnreadCount() { return request.get('/notice/inbox/unreadCount') },
  inboxMarkRead(id) { return request.post(`/notice/inbox/markRead/${id}`) },
  inboxMarkAllRead() { return request.post('/notice/inbox/markAllRead') },
  inboxDelete(id) { return request.delete(`/notice/inbox/delete/${id}`) }
}

// 系统管理（用户管理）门面：统一收口到一个命名空间
const system = {
  userList() { return request.get('/user/list') },
  updateRole(userId, role) {
    const params = new URLSearchParams()
    params.append('role', role)
    return request.put(`/user/role/${userId}`, params)
  },
  deleteUser(userId) { return request.delete(`/user/${userId}`) },
  updateStatus(userId, status) {
    const params = new URLSearchParams()
    params.append('status', status)
    return request.put(`/user/status/${userId}`, params)
  },
  updateDepartment(userId, departmentId) {
    if (departmentId === null || departmentId === undefined) {
      return Promise.reject(new Error('部门ID不能为空'))
    }
    const idInt = typeof departmentId === 'string' ? parseInt(departmentId) : departmentId
    if (Number.isNaN(idInt) || idInt <= 0) {
      return Promise.reject(new Error('无效的部门ID'))
    }
    return request.put(`/user/department/${userId}?departmentId=${idInt}`)
  },
  updatePosition(userId, position) {
    const encoded = encodeURIComponent(position ?? '')
    return request.put(`/user/position/${userId}?position=${encoded}`)
  },
  removeFromDepartment(userId) { return request.post(`/user/department/remove/${userId}`) }
}

export default { account, org, chat, notice, system }


