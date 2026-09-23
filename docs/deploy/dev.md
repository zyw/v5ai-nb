# v5ai-nb 开发环境部署文档

> 适用范围：本地/开发机部署 `v5ai-nb`（Phase 1 模型与 Agent 闭环、Phase 2 RAG、Phase 3 MCP、Phase 4 Skill、Phase 5 平台增强、Phase 6 对话门户；Workflow 进行中）。
> 技术栈：JDK 21 + Spring Boot 4.1.0（Servlet MVC，响应式仅用于运行时事件流/SSE）+ MyBatis-Plus + PostgreSQL/pgvector + Sa-Token(JWT) + AgentScope Java 2.0 + Vue 3 / Vite 7。
> 全部配置项与默认值速查见 README「配置项一览（v5ai.*）」；环境变量样例见仓库根 `.env.example`。

---

## 1. 前置要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21+ | 项目编译目标为 Java 21 |
| Maven | 3.9+ | 多模块构建 |
| PostgreSQL | 14+ | 业务数据库（含 pgvector 扩展） |
| pgvector | 0.5+ | 向量检索扩展 |
| Node.js | `^20.19` 或 `>=22.12` | Vite 7 的 Node 版本要求 |
| npm | 9+ | 随 Node 安装 |
| Redis | 6+ | **必需**：Sa-Token 会话、刷新令牌、登录验证码校验都存在 Redis，登录与运行接口都依赖它 |
| MinIO | 任意 | **可选**，仅当 `v5ai.storage.type=MINIO` 时需要；默认 `LOCAL` 用本地目录 |

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

### 2.2 Redis（必需）

```bash
brew install redis
brew services start redis
redis-cli ping   # 期望 PONG
```

> 连接信息在 profile 配置里（`application-dev.yml` 的 `spring.data.redis.*`），其默认值是内网地址
> `192.168.10.13:6379`；本机部署请用 `V5AI_REDIS_HOST` / `V5AI_REDIS_PORT` / `V5AI_REDIS_PASSWORD`
> / `V5AI_REDIS_DATABASE` 覆盖，并确认选用的库为空闲库。

### 2.3 MinIO（可选）

仅在 `v5ai.storage.type=MINIO` 时需要；默认 `LOCAL` 时不启动也不影响功能。

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
├── pom.xml                     # 父 POM（聚合 v5ai-common / v5ai-modules / v5ai-api / v5ai-starter）
├── v5ai-common/                # 公共能力：core / json / mybatis / redis / satoken / security / web /
│                              #   encrypt / storage / log / translation / sensitive / agentscope /
│                              #   elasticsearch / milvus
├── v5ai-modules/               # 业务模块（自包含 domain/mapper/service/controller）
│   ├── v5ai-model/             # 模型与 Provider 管理 + 运行时模型链
│   ├── v5ai-agent/             # Agent 管理、发布/版本/绑定
│   ├── v5ai-runtime/           # Agent 运行时装配、SSE、会话/消息/run 持久化
│   ├── v5ai-rag/               # 知识库、文档、检索
│   ├── v5ai-mcp/               # MCP Server 注册、工具发现、绑定与审计
│   ├── v5ai-skill/             # Skill 包上传、版本发布与绑定
│   ├── v5ai-workflow/          # 工作流（进行中）
│   ├── v5ai-worker/            # 文档索引 Worker（@Scheduled）
│   ├── v5ai-platform/          # 登录/RBAC/API Key/配额/审计 + Flyway 迁移（db/migration）
│   └── v5ai-code-generator/    # 代码生成器
├── v5ai-api/                   # 运行 API（/api/v1 SSE）与跨模块聚合装配
├── v5ai-starter/               # Spring Boot 启动模块（application.yml / application-*.yml 在此）
├── v5ai-ui/                    # Vue 3 + Vite 管理端
└── v5ai-ui-chat/               # Vue 3 + Vite 独立对话门户（终端用户）
```

---

## 4. 后端部署

### 4.1 环境变量

启动前设置（完整清单与说明见 `v5ai-nb/.env.example`）：

```bash
export V5AI_DATASOURCE_URL=jdbc:postgresql://localhost:5432/v5ai_nb
export V5AI_DATASOURCE_USERNAME=v5ai
export V5AI_DATASOURCE_PASSWORD=v5ai
export V5AI_JWT_SECRET=change-this-development-secret          # Sa-Token JWT 签名密钥
export V5AI_CREDENTIAL_CIPHER_KEY=0123456789abcdef0123456789abcdef  # 必须 32 字节（AES-256）

# Redis 必需（Sa-Token 会话、刷新令牌、登录验证码都存放在 Redis）
export V5AI_REDIS_HOST=localhost
export V5AI_REDIS_PORT=6379
export V5AI_REDIS_PASSWORD=
export V5AI_REDIS_DATABASE=11
export V5AI_REDIS_KEY_PREFIX=v5ai

# 以下均有默认值，按需覆盖：
# export V5AI_STORAGE_TYPE=LOCAL                   # LOCAL=本地磁盘（默认） / MINIO
# export V5AI_STORAGE_LOCAL_DIR=./v5ai-upload
# export V5AI_MINIO_ENDPOINT=http://localhost:9000
# export V5AI_MINIO_ACCESS_KEY=minioadmin
# export V5AI_MINIO_SECRET_KEY=minioadmin
# export V5AI_MINIO_BUCKET=v5ai
# export V5AI_AGENTSCOPE_WORKSPACE=.agentscope/workspace
# export V5AI_AGENTSCOPE_STATE_DIR=.agentscope/state
# export V5AI_TAVILY_API_KEY=                      # 联网搜索，空=不启用
# export V5AI_MCP_STDIO_COMMAND_WHITELIST=         # Stdio MCP 白名单，生产必配
```

> ⚠️ `V5AI_CREDENTIAL_CIPHER_KEY` 必须是 **32 字节**，否则启动即抛
> `credential cipher key must be 32 bytes for AES-256`。
> 该密钥用于加密模型 API Key 等凭据；**更换密钥后旧凭据无法解密**，需重新录入模型配置。

### 4.2 构建

```bash
# 完整构建（含测试）：根 pom 默认 maven.test.skip=true，不加 -Dmaven.test.skip=false 一个用例都不会跑
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dmaven.test.skip=false -Dsurefire.failIfNoSpecifiedTests=false
# 只打包（跳过测试，默认行为）
mvn clean package
```

预期：各模块 surefire 全绿时为 BUILD SUCCESS。**当前仓库存在 1 个模块的已知用例失败**
（`v5ai-agent` 的 `AgentServiceImplTest`，2 个用例），会让 `verify` 停在 v5ai-agent 并跳过其后模块；
只打包不受影响，详见第 7 节 FAQ。

### 4.3 管理员账号与默认客户端

平台表由 Flyway 迁移建表并种子数据（V18），**无需手工插数据**：

| 项 | 值 |
|---|---|
| 管理员账号 | `plm_user.user_name = 'admin'`，密码明文 `admin`（存 BCrypt 哈希） |
| 默认客户端 | `plm_client.client_id = 'e5cd7e4891bf95d1d19206ce24a7b32e'`，`grant_type = 'password'`，`status = '0'` |

> 登录请求必须同时携带 `clientId` 与 `grantType`（见 4.5），客户端行缺失会报「客户端id ... 异常」。
> 首次登录后请立即改密。

如需自定义密码，用 BCrypt 生成哈希后更新 `plm_user.password`：

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

方式 B：Maven 启动（先把各模块装进本地仓库）

```bash
mvn install -DskipTests
mvn -pl v5ai-starter spring-boot:run
```

方式 C：打包后启动

```bash
mvn -pl v5ai-starter -am package -DskipTests
java -jar v5ai-starter/target/v5ai-starter-0.1.0-SNAPSHOT.jar
```

### 4.5 启动自检

- 日志出现 `Flyway` 执行记录：`v5ai_flyway_schema_history` 中应有 `V1__phase1_schema` 起、到 `V45__message_metadata_citations` 的迁移记录（当前共 44 个迁移文件）；
- 日志出现 `Started V5aiApplication`；
- 默认监听 **8080**（`server.port`）。

> ⚠️ **登录请求体是加密的**：`POST /api/auth/login` 标了 `@ApiEncrypt`，而 `v5ai.api-decrypt.enabled`
> 默认 `true`——请求必须带 `encrypt-key` 头（前端用 RSA+AES 混合加密，见 `v5ai-ui/src/utils/crypto.ts`），
> 裸 `curl` 会被 CryptoFilter 以「请求非法」拒绝。

自检登录推荐两种方式：

**方式一（推荐）**：直接用管理端 UI 登录（`v5ai-ui` 已内置加密）：账号 `admin` / 密码 `admin`。

**方式二**：临时关闭接口加密，改用明文 curl：

```bash
# 启动时覆盖（不改配置文件）
java -jar v5ai-starter/target/v5ai-starter-0.1.0-SNAPSHOT.jar --v5ai.api-decrypt.enabled=false

curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"e5cd7e4891bf95d1d19206ce24a7b32e","grantType":"password","username":"admin","password":"admin"}'
# 期望（统一 R 包装 + LoginVo 字段 snake_case）：
# {"code":200,"msg":"操作成功","data":{"access_token":"<jwt>","expire_in":1800,
#  "refresh_token":"<refresh>","refresh_expire_in":43200,"client_id":"e5cd7e4891bf95d1d19206ce24a7b32e"}}
# 后续管理接口用 -H "Authorization: Bearer <access_token>"
```

> 验证码默认开启（`v5ai.captcha.enable=true`），但类型是滑块且阈值 `slider-threshold=3`：
> 同一账号连续输错达到 3 次后才要求先过滑块，正常登录无需 `code`/`uuid`。

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

- 访问 http://localhost:80（端口取自 `v5ai-ui/.env` 的 `VITE_APP_PORT=80`；若 80 端口绑定失败或无权限，改小 `.env` 里的值，例如 5173）。
- Vite 已配置代理：`/dev-api`（`VITE_APP_BASE_API`）→ `http://localhost:8080`，前缀会被 rewrite 掉，前端与后端同源访问。
- 后端不在 8080 时，改 `vite.config.ts` 顶部的 `DEV_PROXY_TARGET` 常量（当前为硬编码）。

### 5.3 构建产物（可选）

```bash
npm run build     # 生产模式，接口前缀取 .env.production 的 VITE_APP_BASE_API=/prod-api
npm run build -- --mode test   # 测试模式，前缀 /test-api
npx vite preview  # 本地预览（生产建议由 Nginx 托管 dist 并反代 /prod-api 到后端）
```

> 前端不含后端地址：`VITE_APP_BASE_API` 只是个前缀，部署时由网关/Nginx 把该前缀转发到后端；
> 也可以直接把它写成完整地址，如 `VITE_APP_BASE_API=https://api.example.com`。

### 5.4 对话门户（v5ai-ui-chat，终端用户用）

独立于管理端的第二个前端，终端用户用 Agent API Key 登录后对话：

```bash
cd v5ai-ui-chat
npm install
npm run dev      # http://localhost:5174（VITE_APP_PORT=5174，与管理端错开端口）
npm run build    # 产物 v5ai-ui-chat/dist
```

- 代理同上：`/dev-api` → `http://localhost:8080`（`vite.config.ts` 的 `DEV_PROXY_TARGET`）；
- 部署在子路径时用 `VITE_APP_BASE`（如 `/chat/`）；
- 管理端「系统信息」页在 `VITE_CHAT_UI_URL`（`v5ai-ui/.env.development`）配置后显示门户入口。

---

## 6. 端到端验证（推荐按序执行）

先登录管理端并取得 `$TOKEN`（`data.access_token`，见 4.5；下面的命令假设已按 4.5 方式二临时关闭接口加密）：

```bash
TOKEN=$(curl -s http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"clientId":"e5cd7e4891bf95d1d19206ce24a7b32e","grantType":"password","username":"admin","password":"admin"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["access_token"])')
```

1. **配置 Provider**

   ```bash
   curl -s http://localhost:8080/api/admin/providers -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"providerKey":"openai","name":"OpenAI","enabled":true}'
   # 记下响应 data.id，作为后续 providerId
   ```

2. **配置 Model**（凭据为 JSON 字符串，服务端加密存储）

   ```bash
   curl -s http://localhost:8080/api/admin/models -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"providerId":<providerId>,"modelKey":"gpt-4o-mini","modelName":"GPT-4o mini","modelType":"CHAT",
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
   curl -s -X POST http://localhost:8080/api/admin/api-keys \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"name":"本地验证","agentKeys":["demo"]}'
   # 返回 {"name":"本地验证","trackingId":"<uuid>","apiKey":"v5ai-...","agentKeys":["demo"]}，记下 $APP_KEY
   # 注意 agentKeys 只能是已发布（PUBLISHED）的 Agent；明文 Key 仅此一次返回
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

> UI 操作路径与上述一致：登录 → 模型管理（配置 Provider/Model、测试连接）→ Agent 管理
> （创建/发布/生成 Key/绑定知识库/MCP/Skill）→ 知识库管理（建库、上传、任务状态、重试）→
> 调试工具（流式对话调试）。其他页面：API Keys 管理、存储实例、资源存储、MCP 管理、
> Skill 管理、工作流、可观测性、系统信息，以及系统管理下的用户/角色/菜单/客户端/日志。

---

## 7. 常见问题（FAQ）

| 现象 | 原因 / 处理 |
|---|---|
| 对话/SSE 接口报 `operator does not exist: uuid = character varying` | 运行期表（v5ai_conversation/message/run/run_event/agent_state）的 id 列原为 UUID 类型，与应用全程使用 String id 的设计不符。已新增 V4 迁移将列改为 VARCHAR(36)（含外键重建），**重新构建并重启后 Flyway 自动应用** |
| 上传 PDF 报 `Content-Type 'application/pdf' is not supported` | 运行栈为 Servlet MVC（Sa-Token 传递引入 spring-boot-starter-web/Tomcat），上传已改用 Servlet 的 `MultipartFile`（原先 WebFlux `FilePart` 与 Servlet 不兼容），**重新构建并重启即可** |
| 上传文档报 `Maximum upload size exceeded` / `The field file exceeds its maximum permitted size of 1048576 bytes` | Tomcat/Spring 默认单文件上限 1MB。已在 `application.yml` 配置 `spring.servlet.multipart.max-file-size=50MB`（可用 `V5AI_UPLOAD_MAX_FILE_SIZE` 覆盖），**重新构建并重启即可**；超限时接口返回 400 `uploaded file exceeds the configured size limit` |
| `knowledge base does not exist: <19 位大数字>` 等按 ID 查询失败的报错 | 旧版本使用 MyBatis-Plus 默认雪花 ID（19 位），超出 JavaScript 安全整数范围，前端回传 ID 时精度丢失。已改为数据库 `BIGSERIAL` 自增 ID（小数值、JS 安全），**重新构建并重启后重新创建知识库/模型即可**（旧数据中的雪花 ID 在 UI 中无法可靠回传，列表会打"旧数据"标签） |
| `/admin/providers` 等管理接口返回 `INTERNAL_ERROR / internal server error` | 多为表/列与实体不一致。先看服务端日志（`Unhandled exception in request` 会打印真实堆栈），检查是否为旧版本构建：`provider_type` 列已由 V26 移除，旧实体/代码若仍引用会报错，请 `mvn clean verify` 后重启，Flyway 会自动应用 V26 |
| 启动报 `relation "v5ai_knowledge_task" does not exist` 等表不存在错误 | Flyway 迁移未执行。Spring Boot 4 已把 Flyway 自动装配移出核心，必须引入 `spring-boot-starter-flyway` 才会执行 `spring.flyway.*`；本项目已内置该依赖与 `flyway-database-postgresql`，**请重新 `mvn clean verify` 后重启**，首次启动会自动建表（检查 `v5ai_flyway_schema_history` 中有 V1…V49 的记录，当前 48 个迁移文件） |
| 启动报 `Unsupported Database: PostgreSQL` | Flyway 10+ 的数据库方言拆分为独立模块，缺少 `flyway-database-postgresql`（本项目已内置）；若使用旧 jar 请重新构建 |
| 启动报 `type "vector" does not exist` | pgvector 扩展未安装，见 2.1 |
| 启动报 `credential cipher key must be 32 bytes` | `V5AI_CREDENTIAL_CIPHER_KEY` 长度必须 32 字节 |
| 登录/刷新接口报「请求非法」或解密失败 | 这两个接口标了 `@ApiEncrypt`，请求必须带 `encrypt-key` 头（RSA+AES）；用 UI 登录，或按 4.5 方式二临时把 `v5ai.api-decrypt.enabled` 设为 `false` |
| 登录报「客户端id ... 异常」 | 请求缺少 `clientId` / `grantType`，或 `plm_client` 中无对应客户端（默认 `e5cd7e4891bf95d1d19206ce24a7b32e` / `password`，见 4.3） |
| `mvn verify -Dmaven.test.skip=false` 在 v5ai-agent 失败 | 该模块 `AgentServiceImplTest` 有 2 个用例当前失败（与本地环境无关），`verify` 会停在此处并跳过后续模块；只打包用默认的 `maven.test.skip=true` |
| Flyway 校验失败 / 迁移报错 | 数据库曾用旧版 schema；开发环境可 `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` 后重建，或直接重建库 |
| 登录返回 `invalid credentials` | `plm_user` 中无该账号（管理员种子见 4.3）或密码哈希非 BCrypt |
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
| 后端 HTTP | 8080（`server.port`） |
| 管理端 Dev Server | 80（`v5ai-ui/.env` 的 `VITE_APP_PORT`；接口前缀 `/dev-api` 代理到 8080） |
| 对话门户 Dev Server | 5174（`v5ai-ui-chat/.env` 的 `VITE_APP_PORT`） |
| 生产接口前缀 | `/prod-api`（`v5ai-ui/.env.production`，由网关/Nginx 转发到后端） |
| PostgreSQL | `jdbc:postgresql://localhost:5432/v5ai_nb`，用户 `v5ai`/`v5ai`（profile 里的默认值指向内网地址，本机请用 `V5AI_DATASOURCE_*` 覆盖） |
| Redis | 必需；dev profile 默认 `192.168.10.13:6379`、库 `11`、key 前缀 `v5ai` |
| Flyway 历史表 | `v5ai_flyway_schema_history`（迁移 V1 Phase1 … V49，当前 48 个文件，只增不改） |
| Sa-Token | Header `Authorization: Bearer <access_token>`，JWT，默认单会话；会话存 Redis |
| 管理员登录 | `admin` / `admin`（V18 种子），请求需带 `clientId` + `grantType=password` |
| 向量维度 | `vector(1536)`（未配置 EMBEDDING 模型时使用本地 Hash 嵌入，同样 1536 维） |
| Worker 扫描 | 每 5 秒（`v5ai.worker.scan-interval-ms`），每批 5 个任务，最多重试 3 次 |

---

## 9. 部署相关已知限制

- **管理接口鉴权**：`SecurityConfig` 的 `SaServletFilter` 拦截 `/**`（仅排除 `v5ai.security.excludes`，默认只放行
  `/api/v1/agents/**`），因此 `/api/admin/**` **强制登录**；控制器上另有 79 处
  `@SaCheckPermission` / `@SaCheckRole` 做菜单级权限校验，运行接口（`/api/v1/agents/**`）则由
  Agent API Key 鉴权并受配额/限流约束。
- **接口加密**：`v5ai.api-decrypt.enabled` 默认 `true`，`/api/auth/login`、`/api/auth/refresh` 等标了
  `@ApiEncrypt` 的接口强制要求 `encrypt-key` 头，裸请求会被拒（见 4.5）。
- **文档与会话存储**：文档正文存在数据库（BYTEA 列）；附件与通用资源走 `plm_resource`，
  由 `v5ai.storage.type` 决定落本地目录（`LOCAL`，默认）还是 MinIO。
- **Redis**：**必需**——Sa-Token 会话与刷新令牌、登录验证码、注解式限流（`@RateLimiter`）都存在 Redis。
- **MinIO**：可选，仅 `v5ai.storage.type=MINIO` 时参与业务链路。
- **多实例**：Sa-Token 会话已在 Redis，登录态天然共享；但应用级配额/限流
  （`InMemorySlidingWindowRateLimiter`）与运行取消注册表（`RunCancellationRegistry`）是进程内实现，
  多实例部署需要替换。
- **单会话限制**：`sa-token.is-concurrent=false`，同一账号新登录会使旧 Token 失效。
