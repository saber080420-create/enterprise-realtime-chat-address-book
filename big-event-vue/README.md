# 企业通讯录与即时通讯（前端）Lite 版

本文档说明精简后的前端目录、统一门面与运行方式，便于快速上手与验收。

## 统一 API 门面

统一入口：`src/api/index.js`

- account：登录/注册、用户信息与资料修改
  - `account.login({ username, password })`
  - `account.register(payload)`
  - `account.me()`、`account.logout()`、`account.loginActivity()`
  - `updateNickname/Realname/Email/Phone/Avatar/Password`
- org：组织与通讯录
  - 部门：`allDepartments()`、`departmentTree()`、`departmentUsers(id)`、增删改
  - 我的通讯录：`groups()`、`listContacts(groupId?)`、`addContact(...)`、`addExternal(payload)`、编辑/删除（含 abId 版本）
  - 常用联系人：`frequentList(limit)`、`favorite(contactId, isFavorite)`、`deleteFrequent(contactId)`
  - 用户搜索：`searchUsers(keyword, limit)`
- chat（路径前缀 `/api/chat`）：
  - 消息：`send`、`edit`、`recall`、`delete`、`singleHistory`、`groupHistory`、`recent`、`markRead`、`markBatch`
  - 群组：`createGroup`、`myGroups`、`group`、`groupMembers`、`addMembers`、`removeMember`、`leaveGroup`、`deleteGroup`、`memberRole`
  - 群公告：`publishAnnouncement`、`withdrawAnnouncement`、`listAnnouncements`、`markAnnouncementRead`、`listAnnouncementReaders`、`announcementUnreadCount`
- notice：系统公告与系统通知
  - 公告：`list/get/create/update/remove/important/latest/search/mine/setStatus`
  - 已读：`markRead/markReadBatch/readStatus/unreadCount/readCount/readList/readUsers/deleteRead`
  - 系统通知：`inboxList/inboxUnreadCount/inboxMarkRead/inboxMarkAllRead/inboxDelete`
- system（用户管理）：`userList/updateRole/deleteUser/updateStatus/updateDepartment/updatePosition/removeFromDepartment`

> 说明：所有页面已切换到以上门面；旧的 `src/api/*.js` 已删除（upload 保留）。

## 主要页面与门面对照

- 登录页 `src/views/Login.vue`：`api.account.*`、`api.org.departmentTree()`
- 首页 `src/views/Home.vue`：`api.notice.inboxUnreadCount()`、公告未读角标
- 部门组织架构 `src/views/Department.vue`：`api.org.departmentTree/departmentUsers`、`api.system.*`
- 通讯录 `src/views/Contacts.vue`：`api.org.groups/listContacts/searchUsers/addContact/addExternal/...`、`api.org.frequentList/deleteFrequent`
- 聊天 `src/views/chat.vue`：全部 `api.chat.*`、公告视图走 `api.notice.*`
- 账号设置/个人中心：`api.account.*`

## 运行方式

```sh
npm install
npm run dev
```

生产构建：

```sh
npm run build
```

## 验收清单（前端）

- 登录/注册成功；未登录访问受限页会跳转登录
- 首页铃铛角标：系统公告与系统通知未读正常
- 部门组织架构：树/增删改；成员查看权限（非系统管理员仅本部门）；移出/变更部门二次确认
- 通讯录：分组增删改；内/外部联系人新增；编辑/删除；常用联系人增删；分组计数准确
- 聊天：单聊消息收发、编辑/撤回/删除、已读与未读计数；群聊历史与公告（发布/撤回/已读名单/顶部横幅）

## 变更记录（精简）

- 统一 API 门面 `src/api/index.js`
- 删除旧前端 API：`user.js`、`addressBook*.js`、`addressBookContacts.js`、`frequentContact.js`、`chat.js`、`system.js`
- 页面已切换至门面；聊天后端路由统一 `/api/chat/**`
