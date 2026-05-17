# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

initDB is an AI-powered database query assistant. Users chat in natural language to query databases via a graph-based AI workflow (StateGraph + ReAct agent) that converts NL to SQL, validates it, and executes it against user-configured data sources.

## Build & Run

```bash
# Build from root (multi-module Maven, single module: initdb-ai)
mvn clean package

# Run the application (port 8081)
mvn spring-boot:run -pl initdb-ai

# Run a single test
mvn test -pl initdb-ai -Dtest=TestClassName

# DDL for app database (PostgreSQL)
# initdb-ai/deploy/db/ddl/postgres.sql
```

## Architecture

**Stack:** Java 17, Spring Boot 3.x (Jakarta), MyBatis-Plus, Spring AI (DashScope + Alibaba Agent Framework), PostgreSQL + pgvector, Thymeleaf + vanilla JS.

**Package root:** `cn.fish` under `initdb-ai/src/main/java/cn/fish/`

### Key Packages

- **`initDB/`** — Core AI workflow domain
  - `workflow/` — StateGraph config (`DBAgentStateGraphConfig`), workflow nodes under `node/`, ReAct agent under `agent/` with tools under `agent/tool/`
  - `service/` — `DBAgentService` (main chat), `ContextualizeService`, `ExportJobService`
  - `controller/` — Streaming chat at `/db/chat/stream`, export at `/db/export/jobs/*`

- **`database/`** — JDBC-based metadata retrieval and read-only SQL execution against target databases
  - `sql/` — `SqlDialect` / `SqlDialectResolver` for MySQL/PostgreSQL dialect handling
  - Controllers at `/dataBase/metadata/*` and `/dataBase/data/*`

- **`datasource/`** — CRUD for user-configured database connections (`agent_datasource` table, MyBatis-Plus)
  - Controller at `/datasource/*`

- **`knowledge/`** — RAG knowledge base with pgvector embeddings
  - Document upload, text splitting (`splitter/`), vector store operations
  - Controller at `/agentKnowledge/*`, `/document/*`

- **`chart/`** — Chat session management (`chat_session` table)
  - Controller at `/chat/*`

- **`common/`** — Shared config, `PostgresSaver` (workflow checkpoint persistence), prompt templates, cache config

- **`web/`** — `PageController` serving Thymeleaf pages (`/datasource`, `/chat`, `/knowledge`)

### AI Workflow — Two Execution Paths

1. **Direct Path** (high-confidence intent): `DbIntentClassificationNode` → `DbDirectTableCatalogNode` → `DbDirectNl2SqlNode` → `DbDirectSqlGuardNode` → `DbDirectExecuteQueryNode`
2. **ReAct Agent Path** (general queries): `DbAgentInputBridgeNode` → `ReactAgent` sub-graph with tools (`GetAllTablesTool`, `GetTableSchemaTool`, `GetTableDataTool`, `KnowledgeRetrievalTool`, `QuerySqlCheckTool`)

Routing is decided by `DbIntentClassificationNode` with heuristic pre-filtering and LLM classification.

### Database

- **App DB:** PostgreSQL at `127.0.0.1:5432/init_db` (MyBatis-Plus ORM)
- **Target DBs:** Arbitrary user-configured MySQL/PostgreSQL via raw JDBC (`JdbcTemplate`)
- **DDL:** `initdb-ai/deploy/db/ddl/postgres.sql` (and `mysql.sql`)
- **Tables:** `agent_datasource`, `chat_session`, `agent_knowledge`, `export_job`

### Configuration

- `initdb-ai/src/main/resources/application.yaml` — LLM (DashScope `qwen-plus-2025-07-28`), DB connection, custom `initdb.*` properties (metadata caching TTL, intent heuristics, SQL pre-check, export limits)
- Logback: `initdb-ai/src/main/resources/conf/logback.xml`

### Data Source Connection Management (HikariCP + Caffeine)

Target database connections are managed by `DataBaseRepositoryImpl` with a two-tier strategy:

**Connection test** (`test()` method): Creates a temporary `HikariDataSource` inside try-with-resources, calls `isRunning()` to validate JDBC URL / credentials / network reachability, then the pool is auto-closed. This is a real connection pool initialization, not a single-connection probe — `HikariDataSource.isRunning()` internally acquires a connection from the pool to verify end-to-end connectivity. The throwaway design means test failures don't pollute the query-time cache.

**Query-time connection pool** (`add()` / `get()`): Live `HikariDataSource` instances are stored in a Caffeine cache (max 128 entries, keyed by datasource ID). When a chat session executes SQL, it looks up the cached pool by datasource ID. `remove()` explicitly calls `HikariDataSource.close()` (shuts down the pool's threads and connections) before invalidating the cache entry.

```
test flow:  HikariConfig → try(HikariDataSource) → isRunning() → auto-close
query flow: add(id, url, user, pass) → HikariDataSource → Caffeine.put(id, ds)
            get(id) → Caffeine.getIfPresent(id) → DataSource
            remove(id) → ds.close() → Caffeine.invalidate(id)
```

This pattern separates validation (ephemeral, throwaway pools) from production use (cached, long-lived pools), avoiding connection leaks while keeping query execution fast.

**`isRunning()` 原理：** 不是简单的 ping，HikariDataSource 初始化时会：
1. 解析 JDBC URL，加载对应数据库驱动（MySQL/PostgreSQL）
2. 根据 `maxPoolSize(5)` + `minIdle(1)` 创建连接池，预先建立 1 个连接
3. `isRunning()` 从池中获取一个真实连接，执行驱动握手、认证、网络可达性验证
4. try-with-resources 退出时调用 `close()`，关闭池内所有连接和后台线程

所以 test 方法验证的是 **JDBC 驱动 → 网络 → 数据库认证** 整条链路。

**Caffeine 缓存管理的是连接池实例，不是连接本身。** 每个数据源对应一个独立的 `HikariDataSource`（内含最多 5 个连接），key 是 datasource ID。`remove()` 时先 `ds.close()` 确保池内连接和后台清理线程都被正确释放，再 `invalidate` 从缓存移除。测试用的临时池和查询用的持久池完全隔开。

## Features

### 数据源管理

记录下已创建过的数据库连接，会话创建时直接引用，减少重复创建工作。

**代码位置：** `cn.fish.datasource` 包（Controller / Service / Repository / Mapper 四层），`DataBaseRepositoryImpl` 负责真实连接管理。

**技术要点：**

- **MyBatis-Plus 零 SQL CRUD：** Mapper 为空（继承 `BaseMapper`），所有增删改查通过 `LambdaQueryWrapper` 链式 API 完成，分页查询条件动态拼装，无需手写 XML/SQL
- **HikariCP 连接测试：** `DataBaseRepositoryImpl.test()` 用 try-with-resources 创建临时 `HikariDataSource`，`isRunning()` 验证 JDBC 驱动 → 网络 → 数据库认证整条链路，测试完即销毁，不污染查询缓存
- **Caffeine 连接池缓存：** 查询时的 `HikariDataSource` 实例按 datasource ID 缓存（max 128），`remove()` 先 `ds.close()` 释放池内连接和后台线程再 `invalidate`，避免连接泄漏
- **JDBC URL 自动拼装：** 前端根据数据库类型 + host/port/databaseName 自动构建 JDBC URL，支持手动覆盖（`datasourceUrlManualOverride` 标记防止自动同步覆盖用户输入）
- **数据源与会话绑定：** `chat_session` 表通过 `datasource_id` 关联数据源，创建会话时前端校验 `status == 1 && testStatus == 1`，确保绑定的是已验证可用的数据源

### 会话创建

创建一个对话框，可以引用数据源，也可以无数据源对话。

**代码位置：** `cn.fish.chart` 包（ChatController / ChatSessionService），前端 `chat.js`。

**有数据源对话 — 端到端流程：**

```
前端                                    后端
------                                  ------
1. createModal()
   -> GET /datasource/query/page        -> 返回 status=1 & testStatus=1 的数据源
   -> 填充 <select> 下拉框

2. createNewSession()
   -> POST /chat/create                 -> ChatSessionServiceImpl.add()
      {sessionName, datasourceId}          a) 生成 UUID sessionId
                                          b) 校验 datasourceId：存在、已启用、连接测试通过
                                          c) 创建 HikariDataSource 并缓存到 Caffeine (key=sessionId)
                                          d) INSERT chat_session(session_id, session_name, datasource_id)

3. sendMessage()
   -> POST /db/chat/stream              -> DBAgentServiceImpl.chatStream()
      {message, sessionId}                -> Workflow 收到 {db_session_id: sessionId}
                                          -> 工作流节点读取 sessionId
                                          -> DataBaseServiceImpl.getDataSource(sessionId)
                                             缓存命中：直接用已有的连接池
                                             缓存未命中：重新加载 ChatSession.datasourceId
                                                -> 查 AgentDatasource -> 重建连接池
                                          -> 用解析出的 DataSource 执行 SQL
```

**关键技术点：**

- **连接池以 sessionId 为 key，而非 datasourceId：** `DataBaseRepositoryImpl` 的 Caffeine 缓存 key 是 `sessionId`。创建会话时预创建连接池，查询时按 sessionId 命中。缓存未命中（如服务重启）时，通过 `ChatSession.datasourceId` → `AgentDatasource` 重新解析并重建连接池
- **前端数据源过滤：** `loadChatDatasourceOptions()` 只加载 `status=1 && testStatus=1` 的数据源，确保下拉框中都是可用的数据源
- **服务端二次校验：** `validateDataSource()` 在创建会话时重新校验数据源状态、连接 URL 和用户名完整性，防止前端校验被绕过
- **无数据源对话：** 目前 `datasourceId` 在服务端为必填项（`validateDataSource` 中 blank 检查），所有会话都需要绑定数据源

### 流式问答

流式输出对话减少用户等待时间。

**代码位置：** 后端 `DBAgentController` → `DBAgentServiceImpl` → `DbChatGraphStream`，前端 `chat.js` 的 `sendMessage()`。

**协议：NDJSON over chunked HTTP**（不是 SSE / WebSocket）。Content-Type 为 `application/x-ndjson;charset=UTF-8`，每行一个 JSON 对象：

```json
{"p":"answer","t":"文本片段"}
{"p":"trace","t":"意图识别完成，路由：直连数据"}
```

`p` 取值：`answer`（回答文本）、`trace`（工作流进度/思考过程）、`contextualize`（上下文改写后的提问）。

**后端流式生成链路：**

```
DBAgentController.chatStream()          返回 Flux<String>，每个 String 是一行 NDJSON
  -> DBAgentServiceImpl.chatStream()    调用 CompiledGraph.stream() 得到 Flux<NodeOutput>
     -> 过滤掉 START / END 节点输出
     -> concatMap(DbChatGraphStream.concatChatStreamNdjsonLines)
     -> 整条订阅运行在 Schedulers.boundedElastic()
        （因为节点内的 chatModel.call() 是阻塞的，会死锁事件循环线程）
```

**两种流式数据源：**

1. **Direct 路径 — SQL 结果分块流：** `DbDirectExecuteQueryNode` 将 Markdown 表格结果拆分为 ~900 字符的块（`chunkMarkdown`，按行边界切割），每个块包装为 `StreamingOutput<String>`（`OutputType.GRAPH_NODE_STREAMING`），以 `Flux<GraphResponse<NodeOutput>>` 嵌入状态。框架自动将这些块作为独立 `NodeOutput` 发射到 graph 的输出流中
2. **Agent 路径 — LLM token 流：** ReAct Agent 子图在调用 ChatModel 时，框架自动将模型 token 流式输出为 `StreamingOutput` 帧（`OutputType.AGENT_MODEL_STREAMING`），同时工具调用信息作为 trace NDJSON 行发出

**NDJSON 行组装（`DbChatGraphStream.concatChatStreamNdjsonLines`）：** 每个 `NodeOutput` 拆为三部分拼接输出：
1. 结构化 trace — 非流式节点完成时发出进度描述（如"意图识别完成"、"SQL 通过校验"），带去重 key 防重复
2. 流式 trace — Agent 工具调用描述、流式段切换描述（如"正在生成回答"）
3. answer 文本 — 流式帧的 `streamingTextDelta()` 或非流式节点 bundle 中的 `direct_answer`

每行通过 `streamLineSafe()` 序列化为 `{"p":"answer","t":"text"}\n`。

**前端流式接收与渲染：**

```
fetch() -> response.body.getReader()     ReadableStream API
  -> TextDecoder 解码 Uint8Array
  -> lineBuffer 累积，按 \n 分割，解析完整 JSON 行
  -> 按 p 字段分流：traceAccum / answerAccum
  -> requestAnimationFrame 批量更新 DOM（scheduleStreamFlush 合并帧）
  -> 流式中：setBotMessageStreamingStructured() 渲染为 <pre> 纯文本
  -> 流结束后：setBotMessageStructured() 用 marked.parse() 重新渲染为 Markdown HTML
```

**关键技术点：**

- **Schedulers.boundedElastic 避免死锁：** Graph 节点内的 LLM 调用是阻塞的，必须在 boundedElastic 线程池执行，否则会死锁 WebFlux 事件循环
- **Markdown 分块而非逐 token：** Direct 路径不逐 token 流式，而是将完整 Markdown 结果按 ~900 字符切割成块，平衡了流式体验和实现复杂度
- **渲染两阶段切换：** 流式中用 `<pre>` 纯文本（因为 Markdown 不完整），流结束后用 `marked.parse()` 重新渲染为格式化 HTML（代码高亮、表格等）
- **requestAnimationFrame 合并帧：** 前端用 `scheduleStreamFlush()` 合并同一动画帧内的多次 chunk 更新，避免高频 DOM 操作导致卡顿

### Custom Parent POM

Uses `cn.fish.cloud:serva-dependencies:1.0.0` as parent BOM (from the author's [serva](https://github.com/onethefish/serva) framework). Core web and MyBatis starters come from this.
