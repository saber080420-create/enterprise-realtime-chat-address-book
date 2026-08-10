# 企业通讯录与即时通讯（后端）Lite 版

本项目为 Spring Boot 3 + MyBatis 的后端服务，已完成“统一门面收口”，保留原路径兼容并逐步下线旧路由。

## 运行环境

- JDK 17（或 19），请确保运行 JDK 与编译目标一致
- MySQL、Redis
- Maven 3.8+

> 若出现 class file version 65.0 报错，请删除 target/ 并使用 JDK17 重新编译：`mvn clean package -DskipTests`

## 统一门面路径

- 认证与账户：`/account/**`
  - `POST /account/login`、`POST /account/register`
- 组织与通讯录：`/org/**`
  - 部门：`/org/department`、`/org/department/tree`、`/org/department/{id}/users`
  - 我的通讯录：`/org/addressbook/**`（分组/联系人/外部联系人/按 abId 编辑删除）
  - 常用联系人：`/org/contact/frequent/**`
- 系统公告与通知：`/notice/**`
  - 公告 CRUD、已读统计、读者名单、未读数；系统通知 inbox 列表与未读数、标记/删除
- 聊天聚合（新命名空间）：`/api/chat/**`
  - 消息：发送/编辑/撤回/删除、单聊/群聊历史、最近会话、批量已读
  - 群组：创建/成员增删/退出/解散、成员角色
  - 群公告：发布/撤回/列表/已读读者/未读数

> 旧 `/chat/**` 控制器已下线，前端已全部切到 `/api/chat/**`。

## 关键组件

- `LoginInterceptor`：JWT 校验与 ThreadLocal 注入
- `ThreadLocalUtil`：在 Service 层读取当前用户上下文
- `StringRedisTemplate`：单点登录、令牌失效、公告软删除等
- `WebSocket`：消息/通知推送；单聊已读回执

## 常见问题

- 启动失败（Mapper 冲突/参数名丢失）：已在 `pom.xml` 启用 `<parameters>true>`；仍有问题请检查 Mapper SQL 与入参
- 类版本不匹配：清理 `target/` 并统一 JDK 版本后重编译

## 验收要点（后端）

- 登录/注册成功，Redis 中 SSO 生效（旧令牌失效）
- `GET /org/department/{id}/users` 权限：非系统管理员仅返回“当前用户部门”
- 公告已读统计/读者名单接口可返回完整用户字段（含头像与姓名）
- 群公告：仅群主可撤回；撤回后通过 WS 通知

## 构建与运行

```bash
mvn clean package -DskipTests
java -jar target/big-event-1.0-SNAPSHOT.jar
```

> 配置项参见 `src/main/resources/application.yml`。
