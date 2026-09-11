# 用户自带 DeepSeek API Key（BYOK）

## 使用

登录 → AI 助手右上角「模型配置」→ 输入自己的 Key → 可选「测试连接」→ 保存。
保存立即影响后续请求，无需重启。删除仅删除本项目的凭据，不会吊销 DeepSeek 平台密钥；已开始的请求可能继续完成。
仅支持固定官方地址 https://api.deepseek.com。模型由服务器的 DEEPSEEK_CHAT_MODEL 决定。
页面请求不再回退到全局 DEEPSEEK_API_KEY，原用户也需要在页面保存自己的 Key。

测试调用官方 GET /models，不生成文本；可访问模型列表不等于余额充足或文本生成一定成功。
官方接口说明：https://api-docs.deepseek.com/api/list-models/

## 部署 / 已有版本升级

1. 在 MySQL 的 big_event 数据库执行 `big-event/src/main/resources/db/knowledge/byok.sql`。只创建新表，可重复执行，不删除数据。
2. 管理员一次性生成 32 字节随机数，Base64 编码，设置后端环境变量 AI_CREDENTIAL_MASTER_KEY。不要将实际值放入源码、日志、聊天、Git 或数据库；安全备份在独立的密钥管理位置。
3. 重启后端并更新前端。缺少/无效主密钥时普通项目可启动，但个人 Key 保存及使用不可用。

Windows 本地开发可在 PowerShell 执行以下内容（不会输出密钥）：

```powershell
$byokMaster = [Environment]::GetEnvironmentVariable('AI_CREDENTIAL_MASTER_KEY', 'User')
if ([string]::IsNullOrWhiteSpace($byokMaster)) {
  $byokBytes = New-Object byte[] 32
  $byokRng = [Security.Cryptography.RandomNumberGenerator]::Create()
  $byokRng.GetBytes($byokBytes)
  $byokRng.Dispose()
  $byokMaster = [Convert]::ToBase64String($byokBytes)
  [Environment]::SetEnvironmentVariable('AI_CREDENTIAL_MASTER_KEY', $byokMaster, 'User')
}
$env:AI_CREDENTIAL_MASTER_KEY = $byokMaster
# 在后端目录启动
mvn spring-boot:run
```

用户级环境变量不是加密保险箱，适合本地演示；正式部署用密钥管理服务和 HTTPS。不要每次启动重新生成主密钥：更换或丢失它会导致旧凭据无法解密，此时需恢复原主密钥或让用户重新保存 Key。当前未实现主密钥轮换工具。
Docker 初始化挂载仅对全新 MySQL 数据卷生效，已有数据库必须手动执行迁移。不要为升级删除数据卷。

## 安全设计与边界

- 登录身份只来自服务端 ThreadLocal，不接收前端指定用户；服务层复查账号有效状态和角色。
- AES-256-GCM，随机 12 字节 nonce，AAD 绑定 provider、版本和用户 ID；篡改密文或跨用户搬移密文会解密失败。
- 数据库仅存密文；配置读取只返回状态和末尾 4 位。接口 no-store；密码输入不写 localStorage，保存/关闭后清空。
- 请求输入对象的 toString 脱敏；AI Mapper 日志保持 INFO，禁止生产请求体日志/APM 采集该接口。运维仍需审查反向代理及监控配置。
- 在请求线程解析凭据，创建不可变的请求独立 gateway，异步普通聊天、RAG、群摘要复用该实例，不修改单例 Key，不依赖异步线程的登录上下文。
- 固定官方 HTTPS 地址、禁用重定向，连接测试有超时和全局最多 2 个并发；当前不是跨实例限流，也未实现按用户的测试频率配额。
- 后端能够解密使用 Key，不属于端到端加密或零知识存储。用户自行承担模型费用；本地嵌入及数据库仍由项目统一提供。
- 保存不自动测试凭据，错误 Key 可被保存，但生成会失败；用户可在输入框测试新的 Key。
- 当前功能是 AI 应用工程增强，不是自动执行任务的 Agent。

## 验证

`AiCredentialTest`：随机加密、篡改/用户绑定/主密钥变更、状态脱敏、无 Key 不回退、账号无效、删除、登录身份、连接测试拒绝重定向。
`AiCredentialSqlTest`：实际 SQL 和可重复建表、用户隔离、替换和删除。
`AiChatControllerTest`：并发用户在异步工作线程中分别使用自己的 gateway，兼容原流式/取消测试。

发布前还需区分自动化模拟上游测试与真实付费调用：没有用户真实 Key 时不得宣称真实 DeepSeek 生成已验收。

2026-09-11 本次验证：55 项后端测试通过（含真实本地 BGE-M3 / PGVector 集成与 9 条小样本检索评测，无跳过），4 项前端 SSE 测试通过，Vite 生产构建通过。已有打包体积/混合导入警告不阻断构建。数据库新表迁移和新版后端启动成功；配置接口未登录返回 401。浏览器自动化连接失败，页面完整点击流程和用户真实 Key 的生成调用待手动验收。

## 面试学习卡

问：为什么不能把每个用户的 Key 写入单例 gateway 的字段？
答：两个并发请求可能相互覆盖字段，导致串用凭据和费用归属错误。我们先根据登录身份取出 Key，再创建仅供当前请求使用的实例，传入异步任务。

问：为什么不对 Key 做哈希？
答：密码登录只需验证，可以做单向哈希；调用外部模型必须拿到原始 Key，因此这里需要可逆的认证加密，并把主密钥与数据库分离。
