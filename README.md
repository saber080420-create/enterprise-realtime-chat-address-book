# 企业实时聊天通讯录系统

毕业设计项目，包含企业通讯录、部门组织架构、私聊/群聊、公告、在线状态与数据统计等功能。

## 技术栈

- 前端：Vue 3、Vite、Element Plus、Pinia、ECharts
- 后端：Spring Boot 3、MyBatis、MySQL、Redis、WebSocket

## 笔记本快速启动（推荐）

先安装 Git、JDK 17+、Maven 3.8+、Node.js 20+ 和 Docker Desktop，然后执行：

```powershell
git clone https://github.com/saber080420-create/enterprise-realtime-chat-address-book.git
cd enterprise-realtime-chat-address-book
docker compose up -d
```

等待 MySQL 和 Redis 启动完成。打开一个终端启动后端：

```powershell
cd big-event
mvn spring-boot:run
```

再打开一个终端启动前端：

```powershell
cd big-event-vue
npm install
npm run dev
```

浏览器访问 <http://localhost:5173>。演示管理员账号为 `admin`，密码为 `111111`。

> 第一次初始化数据库可能需要几十秒。`docker compose up -d` 只会在数据库数据卷为空时导入 SQL；如需重新初始化演示数据，可执行 `docker compose down -v` 后再次启动（该命令会删除容器中的数据库数据）。

## 不使用 Docker

1. 启动 MySQL 8 和 Redis。
2. 依次导入根目录的 `big_event.sql`，以及 `V7`、`V8`、`V9` 三个迁移文件。
3. 启动后端前设置相应环境变量：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/big_event?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Hong_Kong'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的MySQL密码'
$env:REDIS_HOST='localhost'
$env:REDIS_PORT='6379'
$env:REDIS_PASSWORD='你的Redis密码'
$env:JWT_SECRET='请替换为随机长字符串'
mvn spring-boot:run
```

## 默认端口

- 前端：5173
- 后端：8081
- MySQL：3306
- Redis：6379
- WebSocket：前端通过 Vite 代理连接 `/api/ws`

## AI 二次开发（进行中）

新增侧边栏「AI 助手」：DeepSeek 多轮流式问答、停止生成、重试、Token 与耗时展示。
后端设置 `DEEPSEEK_API_KEY` 后启动；未设置时原项目可继续启动，AI 页面显示未配置。
支持通用问答和公告问答。公告问答以关键词检索用户有权查看的已发布公告，返回参考片段并支持重新鉴权查看来源；无匹配资料时明确提示。
支持关键词基线和可选的 BGE-M3 + PGVector 语义检索，开启向量服务后同步可见公告索引即可使用。
安装和限制见 [向量检索说明](docs/vector-rag.md)。个人 TXT/Markdown 文档支持入库、关键词及向量问答、所有权隔离、引用和移出；需执行 MySQL 文档表及 PGVector 文档索引表迁移，见 [文档知识库说明](docs/document-knowledge.md)。PDF/Word 暂未实现。对话仅保存在页面内存中。
详细范围、配置和验证记录见 [AI 开发记录](docs/ai-development.md)。
新增按群和时间范围的群聊摘要与行动项提取，复查成员权限、排除撤回消息，结构和引用校验后展示；不自动创建任务。限制与验收见 [群聊摘要说明](docs/group-summary.md)。
第一阶段资料：[验收报告](docs/phase-one-acceptance.md)、[演示步骤](docs/demo-runbook.md)。核心链路已实测，仍有报告列出的验收尾项，非生产就绪。

## 项目演示建议

先运行 `docker compose up -d`，分别启动前后端，并准备两个浏览器窗口登录不同账号。展示顺序建议为：登录与通讯录 → 私聊实时收发 → 群聊 → 公告 → 在线状态和数据统计。

