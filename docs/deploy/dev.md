# v5ai-nb 开发环境部署文档

> 适用范围：本地/开发机部署 `v5ai-nb`（Phase 1 模型与 Agent 闭环 + Phase 2 RAG 知识库）。
> 技术栈：JDK 21 + Spring Boot 4.1.0（WebFlux）+ MyBatis-Plus + PostgreSQL/pgvector + Sa-Token(JWT) + AgentScope Java 2.0 + Vue 3 / Vite。

---

## 1. 前置要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21+ | 项目编译目标为 Java 21 |
| Maven | 3.9+ | 多模块构建 |
| PostgreSQL | 14+ | 业务数据库（含 pgvector 扩展） |
| pgvector | 0.5+ | 向量检索扩展 |
| Node.js | 18+（推荐 20 LTS） | 前端构建（Vite 7 需要 18+） |
| npm | 9+ | 随 Node 安装 |
| Redis / MinIO | 任意 | **可选**。当前阶段仅提供配置与 Client Bean，业务尚未强制使用；不启动也不影响服务启动与验证 |

---

## 2. 基础设施准备

### 2.1 PostgreSQL + pgvector

以 macOS（Homebrew）为例；其他平台等价。

```bash
# 安装 PostgreSQL 与 pgvector
brew install postgresql@16 pgvector
brew services start postgresql@16

# 创建数据库与专用账号（默认账号 postgres）
psql -U postgres -h localhost <<'SQL'
CREATE USER v5ai WITH PASSWORD 'v5ai';
CREATE DATABASE v5ai_nb OWNER v5ai;
\c v5ai_nb
CREATE EXTENSION IF NOT EXISTS vector;
GRANT ALL ON SCHEMA public TO v5ai;
SQL
```

> 说明：
> - `CREATE EXTENSION vector` 需要超级用户权限；若 DBA 已预装扩展，则普通用户可直接执行。
> - 应用启动时 Flyway 会再次执行 `CREATE EXTENSION IF NOT EXISTS vector`（幂等）。
> - 若 `vector` 类型不存在，报错形如 `type "vector" does not exist`，请先执行上面的扩展安装。

### 2.2 Redis（可选）

```bash
brew install redis
brew services start redis
# 默认 redis://localhost:6379，无需配置即可被默认值识别
```

### 2.3 MinIO（可选）

```bash
brew install minio/stable/minio
minio server ~/minio-data --console-address :9001
# 默认 access/secret: minioadmin/minioadmin
```

---

## 3. 获取代码与目录结构

```bash
git clone <your-repo>/v5ai-nb.git
cd v5ai-nb
```

```
v5ai-nb/
├── pom.xml                     # 父 POM（模块：common/domain/infrastructure/model/
│                              #  runtime/application/rag/worker/api/starter）
├── v5ai-api/                   # 管理 API + 运行 API（SSE）
├── v5ai-starter/               # Spring Boot 启动模块（application.yml 在此）
├── v5ai-infrastructure/        # MyBatis-Plus/pgvector/加密/各端口实现
├── v5ai-rag/                   # 知识库/文档/任务领域与检索
├── v5ai-worker/                # 文档索引 Worker（@Scheduled）
├── v5ai-runtime/               # Agent 运行时边界与事件
├── v5ai-model/ v5ai-agent/ v5ai-common/ v5ai-domain/
└── v5ai-ui/                    # Vue 3 + Vite 管理前端
```

---

## 4. 后端部署

### 4.1 环境变量

启动前设置（也可见 `v5ai-nb/.env.example`）：

```bash
export V5AI_DATASOURCE_URL=jdbc:postgresql://localhost:5432/v5ai_nb
export V5AI_DATASOURCE_USERNAME=v5ai
export V5AI_DATASOURCE_PASSWORD=v5ai
export V5AI_JWT_SECRET=change-this-development-secret          # Sa-Token JWT 签名密钥
export V5AI_CREDENTIAL_CIPHER_KEY=0123456789abcdef0123456789abcdef  # 必须 32 字节（AES-256）

# 以下均有默认值，可选：
# export V5AI_REDIS_ADDRESS=redis://localhost:6379
# export V5AI_REDIS_PASSWORD=
# export V5AI_REDIS_DATABASE=0
# export V5AI_MINIO_ENDPOINT=http://localhost:9000
# export V5AI_MINIO_ACCESS_KEY=minioadmin
# export V5AI_MINIO_SECRET_KEY=minioadmin
# export V5AI_MINIO_BUCKET=v5ai
# export V5AI_AGENTSCOPE_WORKSPACE=.agentscope/workspace
# export V5AI_AGENTSCOPE_STATE_DIR=.agentscope/state
```

> ⚠️ `V5AI_CREDENTIAL_CIPHER_KEY` 必须是 **32 字节**，否则启动即抛
> `credential cipher key must be 32 bytes for AES-256`。
> 该密钥用于加密模型 API Key 等凭据；**更换密钥后旧凭据无法解密**，需重新录入模型配置。

### 4.2 构建

```bash
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dsurefire.failIfNoSpecifiedTests=false
# 或使用默认本地仓库：mvn clean verify -Dsurefire.failIfNoSpecifiedTests=false
```

预期：BUILD SUCCESS，112 个测试全部通过（0 失败/0 错误）。

### 4.3 初始化管理员账号

Flyway 只建表（`v5ai_user`），**不写入任何用户**，需要手工插入管理员（密码为 BCrypt 哈希，与登录校验一致）：

```sql
INSERT INTO v5ai_user (username, password_hash, enabled)
VALUES ('admin', '$2a$10$muicVGDQ5Nvw70.tEnPOnuLjM8lftXnTZ0e.61azAiV6QRuiK7WT6', TRUE);
-- 上例明文密码：admin
```

如需自定义密码，用 BCrypt 生成哈希（例如用项目内已有的依赖）：

```bash
# 借助 spring-security-crypto 生成（或用任意在线 BCrypt 工具，强度 10）
cat > /tmp/HashGen.java <<'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashGen {
    public static void main(String[] a) { System.out.println(new BCryptPasswordEncoder().encode(a[0])); }
}
EOF
M2=<你的本地 m2 仓库>
CRYPTO=$(find $M2/org/springframework/security/spring-security-crypto -name '*.jar' | sort | tail -1)
CORE=$(find $M2/org/springframework/spring-core -name '*.jar' | sort | tail -1)
JCL=$(find $M2/org/springframework/spring-jcl -name '*.jar' | sort | tail -1)
javac -cp "$CRYPTO:$CORE:$JCL" /tmp/HashGen.java
java -cp "/tmp:$CRYPTO:$CORE:$JCL" HashGen '你的密码'
```

### 4.4 启动

方式 A：IDE 直接运行 `xin.v5ai.nb.starter.V5aiApplication`（注意设置 4.1 的环境变量）。

方式 B：Maven 启动

```bash
mvn -pl v5ai-starter spring-boot:run
```

方式 C：打包后启动

```bash
mvn -pl v5ai-starter -am package -DskipTests
java -jar v5ai-starter/target/v5ai-starter-0.1.0-SNAPSHOT.jar
```

### 4.5 启动自检

- 日志出现 `Flyway` 执行记录：`v5ai_flyway_schema_history` 中应有 `V1__phase1_schema` 与 `V2__rag_schema`；
- 日志出现 `Started V5aiApplication`；
- 默认监听 **8080**（未配置 `server.port`）。

```bash
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'
# 期望：{"tokenType":"Bearer","token":"<jwt>"}
```

---

## 5. 前端部署

### 5.1 依赖安装

```bash
cd v5ai-ui
npm install
```

### 5.2 开发模式（推荐）

```bash
npm run dev
```

- 访问 http://localhost:5173
- Vite 已配置代理：`/api` → `http://localhost:8080`（见 `v5ai-ui/vite.config.ts`），前端与后端同源访问，无需 CORS 配置。
- 若后端不在 8080，请修改 `vite.config.ts` 的 proxy 目标。

### 5.3 构建产物（可选）

```bash
npm run build     # 产物在 v5ai-ui/dist
npx vite preview  # 本地预览（生产部署建议由 Nginx 托管 dist 并反代 /api 到后端）
```

> 若前端与后端不同域部署，构建时可用 `VITE_API_BASE` 指定后端地址：
> `VITE_API_BASE=http://localhost:8080 npm run build`。

---

## 6. 端到端验证（推荐按序执行）

登录管理端（第 4.5 步拿到 Token，记为 `$TOKEN`）：

1. **配置 Provider**

   ```bash
   curl -s http://localhost:8080/api/admin/providers -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"providerKey":"openai","name":"OpenAI","providerType":"OPENAI_COMPATIBLE","enabled":true}'
   ```

2. **配置 Model**（凭据为 JSON 字符串，服务端加密存储）

   ```bash
   curl -s http://localhost:8080/api/admin/models -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"providerKey":"openai","modelKey":"gpt-4o-mini","type":"CHAT",
          "credentials":"{\"apiKey\":\"sk-...\",\"baseUrl\":\"https://api.openai.com/v1\",\"temperature\":0.7,\"maxTokens\":1024}"}'
   # 记下返回的 model id
   ```

3. **连通性测试**（可选，会真实调用模型）

   ```bash
   curl -s -X POST http://localhost:8080/api/admin/models/<modelId>/test -H "Authorization: Bearer $TOKEN"
   ```

4. **创建并发布 Agent**

   ```bash
   curl -s http://localhost:8080/api/admin/agents -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"agentKey":"demo","name":"Demo App","modelId":<modelId>}'
   curl -s -X POST http://localhost:8080/api/admin/agents/demo/publish \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"description":"v1"}'
   ```

5. **生成运行 API Key**（仅返回一次）

   ```bash
   curl -s -X POST http://localhost:8080/api/admin/agents/demo/api-keys -H "Authorization: Bearer $TOKEN"
   # 返回 {"agentKey":"demo","apiKey":"v5ai_..."}，记下 $APP_KEY
   ```

6. **配置知识库并上传文档**（Worker 异步索引）

   ```bash
   curl -s http://localhost:8080/api/admin/knowledge-bases -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"Docs","description":"product docs"}'
   curl -s -X POST http://localhost:8080/api/admin/knowledge-bases/1/documents \
     -H "Authorization: Bearer $TOKEN" -F "file=@guide.md"
   # 轮询状态：GET /api/admin/knowledge-bases/1/documents → COMPLETED
   # 未配置 EMBEDDING 模型时自动使用本地 Hash 嵌入，同样可完成索引
   ```

7. **绑定知识库到 Agent**

   ```bash
   curl -s -X POST http://localhost:8080/api/admin/agents/demo/knowledge-bindings \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"knowledgeBaseIds":[1]}'
   ```

8. **流式对话（带 RAG 上下文）**

   ```bash
   curl -N http://localhost:8080/api/v1/agents/demo/chat/stream \
     -H "Authorization: Bearer $APP_KEY" -H 'Content-Type: application/json' \
     -d '{"query":"根据知识库回答：v5ai 是什么？"}'
   # SSE 事件顺序示例：
   #   event: RUN_STARTED
   #   event: RETRIEVAL      （命中知识库时，payload 为引用上下文）
   #   event: MODEL_CALL
   #   event: TEXT_DELTA     （逐字返回）
   #   event: MESSAGE_COMPLETED / RUN_COMPLETED（失败时为 RUN_FAILED）
   ```

9. **会话历史与恢复**

   ```bash
   curl -s http://localhost:8080/api/v1/agents/demo/chat/conversations/<conversationId> \
     -H "Authorization: Bearer $APP_KEY"
   curl -N -X POST http://localhost:8080/api/v1/agents/demo/chat/conversations/<conversationId>/resume \
     -H "Authorization: Bearer $APP_KEY" -H 'Content-Type: application/json' -d '{"query":"继续"}'
   ```

> UI 操作路径与上述一致：登录 → Models（配置 Provider/Model、测试连接）→ Applications
> （创建/发布/生成 Key/绑定知识库）→ Knowledge Bases（建库、上传、任务状态、重试）→ Chat Debug。

---

## 7. 常见问题（FAQ）

| 现象 | 原因 / 处理 |
|---|---|
| 对话/SSE 接口报 `operator does not exist: uuid = character varying` | 运行期表（v5ai_conversation/message/run/run_event/agent_state）的 id 列原为 UUID 类型，与应用全程使用 String id 的设计不符。已新增 V4 迁移将列改为 VARCHAR(36)（含外键重建），**重新构建并重启后 Flyway 自动应用** |
| 上传 PDF 报 `Content-Type 'application/pdf' is not supported` | 运行栈为 Servlet MVC（Sa-Token 传递引入 spring-boot-starter-web/Tomcat），上传已改用 Servlet 的 `MultipartFile`（原先 WebFlux `FilePart` 与 Servlet 不兼容），**重新构建并重启即可** |
| 上传文档报 `Maximum upload size exceeded` / `The field file exceeds its maximum permitted size of 1048576 bytes` | Tomcat/Spring 默认单文件上限 1MB。已在 `application.yml` 配置 `spring.servlet.multipart.max-file-size=50MB`（可用 `V5AI_UPLOAD_MAX_FILE_SIZE` 覆盖），**重新构建并重启即可**；超限时接口返回 400 `uploaded file exceeds the configured size limit` |
| `knowledge base does not exist: <19 位大数字>` 等按 ID 查询失败的报错 | 旧版本使用 MyBatis-Plus 默认雪花 ID（19 位），超出 JavaScript 安全整数范围，前端回传 ID 时精度丢失。已改为数据库 `BIGSERIAL` 自增 ID（小数值、JS 安全），**重新构建并重启后重新创建知识库/模型即可**（旧数据中的雪花 ID 在 UI 中无法可靠回传，列表会打"旧数据"标签） |
| `/admin/providers` 等管理接口返回 `INTERNAL_ERROR / internal server error` | 多为表/列与实体不一致（如旧库缺 `v5ai_model_provider.provider_type`）。先看服务端日志（`Unhandled exception in request` 会打印真实堆栈）；对应 schema 修正已由 V3 迁移补齐，**重新构建并重启后 Flyway 会自动应用**；若日志提示其他缺失列，检查是否使用了旧版本构建 |
| 启动报 `relation "v5ai_knowledge_task" does not exist` 等表不存在错误 | Flyway 迁移未执行。Spring Boot 4 已把 Flyway 自动装配移出核心，必须引入 `spring-boot-starter-flyway` 才会执行 `spring.flyway.*`；本项目已内置该依赖与 `flyway-database-postgresql`，**请重新 `mvn clean verify` 后重启**，首次启动会自动建表（检查 `v5ai_flyway_schema_history` 中有 V1、V2、V3 记录） |
| 启动报 `Unsupported Database: PostgreSQL` | Flyway 10+ 的数据库方言拆分为独立模块，缺少 `flyway-database-postgresql`（本项目已内置）；若使用旧 jar 请重新构建 |
| 启动报 `type "vector" does not exist` | pgvector 扩展未安装，见 2.1 |
| 启动报 `credential cipher key must be 32 bytes` | `V5AI_CREDENTIAL_CIPHER_KEY` 长度必须 32 字节 |
| Flyway 校验失败 / 迁移报错 | 数据库曾用旧版 schema；开发环境可 `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` 后重建，或直接重建库 |
| 登录返回 `invalid credentials` | `v5ai_user` 未初始化（见 4.3）或密码哈希非 BCrypt |
| 运行接口返回 401 | 未携带 `Authorization: Bearer <application-api-key>`，或该 app 尚未生成 Key（见 6.5） |
| 对话返回 `application is not published` | Agent 未发布，先调 `publish`（见 6.4） |
| 对话返回 `application requires an enabled model` | 绑定模型的 provider/model 未启用或 model id 不存在 |
| 知识库文档一直是 PENDING/FAILED | Worker 每 5 秒扫描一次；FAILED 可查看 `errorMessage` 并通过 `POST /api/admin/documents/{id}/retry` 重置 |
| 流式对话没有 RETRIEVAL 事件 | 未绑定知识库、检索无命中，或文档索引未完成（见 6.6/6.7） |
| 修改了 `V5AI_CREDENTIAL_CIPHER_KEY` 后模型调用失败 | 密钥变更导致旧凭据无法解密，需重新创建 Model 配置 |
| 8080 被占用 | 设置 `SERVER_PORT=xxx`（Spring Boot 标准属性），并同步修改 `v5ai-ui/vite.config.ts` 的 proxy 目标 |

---

## 8. 端口与默认值速查

| 项 | 默认值 |
|---|---|
| 后端 HTTP | 8080 |
| 前端 Dev Server | 5173（`/api` 代理到 8080） |
| PostgreSQL | `jdbc:postgresql://localhost:5432/v5ai_nb`，用户 `v5ai`/`v5ai` |
| Flyway 历史表 | `v5ai_flyway_schema_history`（迁移：V1 Phase1、V2 RAG） |
| Sa-Token | Header `Authorization: Bearer <token>`，JWT，默认单会话 |
| 向量维度 | `vector(1536)`（未配置 EMBEDDING 模型时使用本地 Hash 嵌入，同样 1536 维） |
| Worker 扫描 | 每 5 秒，每批 5 个任务，最多重试 3 次 |

---

## 9. 部署相关已知限制

- **管理接口鉴权**：目前只有运行接口（`/api/v1/agents/**`）强制校验 Agent API Key；
  `/api/admin/**` 尚未接入服务端 Token 校验（前端已携带，但服务端未强制）。生产部署前需补
  Sa-Token 拦截器/网关鉴权。
- **文档存储**：文档内容当前存入数据库（BYTEA）；MinIO 对象存储归档留待后续阶段。
- **Redis / MinIO**：当前仅为预留 Bean，未参与业务链路；启用后不影响现有功能。
- **多实例**：Sa-Token 会话默认内存存储，多实例部署需接入 Redis 会话共享（后续阶段）。
- **单会话限制**：`sa-token.is-concurrent=false`，同一账号新登录会使旧 Token 失效。
