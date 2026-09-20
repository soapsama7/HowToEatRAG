# 今天吃什么（HowToCook）· Agentic RAG 个人饮食助手

基于 HowToCook 菜谱知识库的 **Agentic RAG** 问答系统：混合检索（pgvector 向量 + zhparser 中文全文检索，归一化加权融合）→ 独立 MCP Tool Server → 主应用 Agent 自主两阶段检索决策 → SSE 流式回答（含可溯源引用）。

## 技术栈

- Java 17 / Spring Boot 3.5.x / Spring AI 1.1.x（OpenAI 兼容协议：chat 火山方舟、embedding 阿里云百炼）
- MyBatis-Plus / PostgreSQL 17（pgvector 1024 维 + zhparser 中文分词）
- RocketMQ（异步索引）/ Redis（Sa-Token 会话 + 限流）/ RustFS（S3 兼容对象存储）
- MCP（Spring AI MCP Server/Client，内部预共享 Token 鉴权）

## 模块结构

```
backend/
├── howtocook-common/       # 公共库：实体/Mapper/枚举、解析分块、Embedding、混合检索、S3 存储
├── howtocook-app/          # 主应用：认证 RBAC、文档管理、Agent 对话（SSE）、管理端运维
└── howtocook-mcp-server/   # MCP Tool Server：search_chunks / get_recipe_detail
```

## 本地启动步骤

### 0. 前置

- JDK 17、Maven 3.9+、Docker（含 compose）
- 菜谱知识库文件：仓库根的 `dishes/`、`tips/` 目录

### 1. 拉起基础设施

```bash
cd backend/docker
cp .env.example .env          # 填入真实值（PG/RustFS 凭据、MCP_INTERNAL_TOKEN、embedding key 等）
docker compose up -d          # 6 服务全部 healthy
```

### 2. 构建

```bash
cd backend
mvn clean package -DskipTests
```

### 3. 主应用配置

```bash
cp howtocook-app/src/main/resources/application-example.yml howtocook-app/src/main/resources/application.yml
cp howtocook-mcp-server/src/main/resources/application-example.yml howtocook-mcp-server/src/main/resources/application.yml
# 在两个 application.yml 中填入真实值；密钥一律走环境变量：
# PG_PASSWORD / RUSTFS_ACCESS_KEY / RUSTFS_SECRET_KEY / MCP_INTERNAL_TOKEN
# LLM_COOK_API_KEY（chat） / LLM_EMBEDDING_API_KEY（embedding，与 chat 不混用）
```

### 4. MCP Tool Server（容器）

```bash
cd backend/docker
docker compose build mcp-tool-server
docker compose up -d mcp-tool-server
# compose 将其映射到 127.0.0.1:18081（仅回环，供宿主主应用联调；工具调用需携带 X-Internal-Token）
```

### 5. 启动主应用

```bash
cd backend
java -jar howtocook-app/target/howtocook-app-0.0.1-SNAPSHOT.jar
# 默认端口 8080；健康检查：GET http://localhost:8080/actuator/health → UP
# 前端 vite 代理默认指向 http://localhost:8080（可用环境变量 VITE_API_TARGET 覆盖，
# 修改 frontend/vite.config.js 后需重启 npm run dev）
```

### 6. 批量导入菜谱知识库（管理端）

```
POST http://localhost:18080/api/admin/documents/batch-import
Authorization: <admin token>
Body: { "dirPath": "<仓库根目录>" }
```

- 遍历 `dishes/**/*.md`（排除 template）与 `tips/**/*.md`，逐个走 上传 → 解析分块 → 向量化 → 入库 链路
- 全量 187 个文件约 3-4 分钟（embedding 批次限速 1s/批，可配）
- 进度可用 `GET /api/admin/index-tasks` 观察，完成后文档 status=READY

### 7. 体验对话

```
POST /api/auth/register → POST /api/auth/login → 拿到 token
POST /api/conversations                        # 新建会话
POST /api/conversations/{id}/chat              # SSE 流式对话（见事件契约）
GET  /api/conversations/{id}/messages          # 历史（含引用/轨迹）
```

SSE 事件：`AGENT_START` → `TOOL_START`/`TOOL_RESULT`（工具实时事件）→ `ANSWER_DELTA`（回答片段）→ `REFERENCE`（引用，已过滤调试字段）→ `DONE`；异常发 `ERROR`。

### 8. 管理端

- 文档上传/批量导入/重索引/删除：`/api/admin/documents*`（自动审计落库）
- 索引任务与失败重试：`/api/admin/index-tasks`、`/api/admin/index-tasks/{taskNo}/retry`
- 审计日志：`/api/admin/audit-logs`
- 默认管理员：`admin`（密码走 `ADMIN_PASSWORD` 环境变量，缺省 `admin@12345` 并在启动日志告警，生产必须设置）

## 关键设计

- **混合检索融合**：两路各召回 20 条 → 各自 Min-Max 归一化 → `0.7×向量 + 0.3×关键词` → Top 5；仅命中单路的另一路按 0 计
- **版本一致性**：重索引 document.version+1，chunk 携带新 version，检索 SQL 仅取 `chunk.version = document.version` 且文档 READY 未删除
- **两阶段检索**：Agent 自主判断——search_chunks 片段不足时再 get_recipe_detail 取全文（引用标记 fullRecipeFetched）
- **防注入/不伪造**：知识库内容视为数据不执行指令；无足够信息如实说明
- **稳定性**：Agent 最大工具轮数 5、单工具超时 30s + 重试 1 次、每用户对话限流 10 次/分钟、并发 3 路（均可配）
