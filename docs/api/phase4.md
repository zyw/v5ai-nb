# v5ai-nb Phase 4 API（Skill）

## Skill 包格式

支持 `.zip`（`.skill` 为别名）上传，最小结构：

```text
skill-package/
├── SKILL.md        # 必填：YAML Front Matter（name、description 必填）+ 正文
├── metadata.yaml   # 可选：其余元数据（author/version 等，frontmatter 优先）
├── prompts/        # 可选：提示词文本资源
└── resources/      # 可选：文本资源
```

校验规则：

- 压缩包 ≤ 10MB、解压总量 ≤ 20MB、单文件 ≤ 512KB、文件数 ≤ 200；
- 禁止绝对路径与 `..` 路径穿越；路径统一为正斜杠；
- 仅支持 UTF-8 文本文件（二进制资源留待后续阶段）；
- `SKILL.md` 必须有 YAML Front Matter，且 `name`、`description` 非空、正文非空；
- 技能名不能包含路径分隔符。

## Skill 管理

```http
POST /api/admin/skills
Content-Type: multipart/form-data
Authorization: Bearer <admin-token>
# file=weather.zip, description=可选版本描述
# → 生成 Skill（若新）+ DRAFT 版本（版本号自动 +1）

GET    /api/admin/skills
GET    /api/admin/skills/options              # 可绑定 Skill 下拉选项（仅 ACTIVE 且有已发布版本）
PUT    /api/admin/skills/{id}/disable         # 禁用（有已发布版本时禁止；运行时跳过）
PUT    /api/admin/skills/{id}/enable          # 启用
GET    /api/admin/skills/{id}/versions        # 版本列表（按版本号降序）
```

`/options` 返回 `[{ value, label: "name (v<currentVersion>)" }]`，仅查 id/name/status/current_version 列，供 Agent 编辑/绑定弹窗按需加载。

## 在线新建与文件编辑

不依赖 zip 包，在线创建 Skill 并在文件编辑页直接维护文件（等价于包内文本文件，路径按 `SKILL.md`、`prompts/guide.md` 这种包内相对路径存储）。

```http
POST /api/admin/skills/online
Content-Type: application/json

{ "name": "weather", "description": "查询天气", "versionDescription": "v1 初版" }
# → 新建 Skill（ACTIVE）+ DRAFT v1 + SKILL.md 骨架（frontmatter 含 name/description）
#   description 写入 skill.description 与 SKILL.md frontmatter；versionDescription（可选）写入 DRAFT v1 的版本描述

GET /api/admin/skills/{id}/editor
# → { skill, versionId, version, files: [{ filePath, content }] }
#   自动确保存在一个 DRAFT 版本：已有则复用，否则新建并从当前已发布版本拷贝文件

POST /api/admin/skills/{id}/files
Content-Type: application/json

{ "filePath": "prompts/guide.md", "content": "# 指南" }   # 在 DRAFT 中新建文件（重名报错）

PUT /api/admin/skills/{id}/files
# 同请求体：更新 DRAFT 中已有文件（不存在报错）

DELETE /api/admin/skills/{id}/files?path=prompts/guide.md   # 从 DRAFT 删除（SKILL.md 禁止删除）
```

- 文件路径校验与上传包一致（禁止绝对路径/路径穿越，统一反斜杠与 `./` 前缀）；单文件上限 512KB；
- 文件编辑只作用于 DRAFT 版本，发布/下线在版本弹窗中操作；
- 编辑的 Skill 必须 ACTIVE，已禁用的 Skill 只读不可写。

## 版本发布、下线与 Skill 禁用

```http
POST /api/admin/skills/{id}/versions/{versionId}/publish    # DRAFT/OFFLINE → PUBLISHED，并设为当前版本
POST /api/admin/skills/{id}/versions/{versionId}/offline    # PUBLISHED → OFFLINE；若是当前版本则清除当前版本指针
GET  /api/admin/skills/{id}/usage                           # → { count: <绑定该 Skill 的 Agent 数> }
POST /api/admin/skills/{id}/rollback                        # 兼容保留：把当前版本指针指回旧版本
Content-Type: application/json

{ "versionId": <已发布的旧版本 id> }
```

- 版本状态三态：`DRAFT`（编辑中，唯一可编辑）/ `PUBLISHED`（已发布，注入「当前版本」）/ `OFFLINE`（已下线，内容冻结不可编辑，可重新发布或删除）；
- 上传 → DRAFT；发布（DRAFT 首次上线 / OFFLINE 重新上线）→ PUBLISHED 并成为当前版本；下线 → OFFLINE，**若下线的是当前版本会清除当前版本指针**：运行时停止注入，`/options` 不再可选；
- **禁用/删除校验**：Skill 存在**任何已发布（PUBLISHED）版本**（与是否 current 无关）时禁止禁用/删除，须先全部下线 —— 判定集中在纯规则模块 `SkillVersionLifecycle`（含 `canPublish/canOffline/canDeleteVersion/canDeleteSkill/latestDraft`）；
- 下线提示：前端先查 `/{id}/usage`，若已被 Agent 绑定（count > 0）提示「有使用，是否下线」，确认后仍可执行；
- 版本状态在实体层为枚举（`domain.enums.SkillVersionStatus`/`SkillStatus`，按 name 存库），VO/前端仍为字符串契约。

## 删除

```http
DELETE /api/admin/skills/{id}                        # 删除 Skill：级联删除其全部版本、文件与 Agent 绑定
DELETE /api/admin/skills/{id}/versions/{versionId}   # 删除某个版本（连同其文件）
```

- **Skill 删除**：存在任何已发布（PUBLISHED）版本时禁止删除，需先全部下线；删除同步级联清理全部版本、文件与 Agent 绑定，前端二次确认提醒；
- **版本删除**：已发布（PUBLISHED）版本禁止删除，DRAFT/OFFLINE 版本可删除（连同文件）。

## AI 生成 / 优化（编辑器）

```http
POST /api/admin/skills/{id}/ai/generate
Content-Type: application/json

{ "modelId": 1, "requirement": "查询 5 天天气预报" }
# → 生成的 SKILL.md 全文（非流式）；保留技能 name，description 用需求说明

POST /api/admin/skills/{id}/files/ai/optimize
Content-Type: application/json

{ "modelId": 1, "filePath": "prompts/guide.md", "requirement": "更简洁", "direction": "面向开发者" }
# → 重写后的文件全文（非流式）
```

- 模型链路复用平台已配置的 CHAT 模型（`ModelConfigRetrieve` → 凭据解密 → `AgentScopeModelFactory` → `model.stream().collectList()`）；编辑器内模型下拉取 `/api/admin/models/options?modelType=CHAT`（`OptionDTO` 新增 `isDefault` 字段），默认选择逻辑：localStorage 记忆 → `isDefault` 默认模型 → 第一个启用模型；
- 生成/优化**不落库**：返回全文由前端替换进编辑器（标记未保存），用户确认后走普通保存；
- 校验：生成的 SKILL.md 必须有合法 frontmatter（name 与技能名一致、description/正文非空）、单文件 ≤512KB，非法结果报错拒绝；
- 保存 SKILL.md 时会把 frontmatter `description` 同步到 Skill 实体（`name` 不变）；管理端 AI 调用暂不计入用量/配额。

## Agent 绑定

```http
POST /api/admin/agents/{agentKey}/skill-bindings
Content-Type: application/json

{ "skillIds": [1, 2] }                     # 全量替换

GET /api/admin/agents/{agentKey}/skill-bindings
```

绑定前校验：Skill 必须 ACTIVE 且已有当前发布版本。

## 运行时（Workspace 注入）

`POST /api/v1/agents/{agentKey}/chat/stream` 在 Agent 绑定了 Skill 时：

1. 解析已发布 Agent 的 Skill 绑定；
2. 读取每个 Skill 当前发布版本的完整文件；
3. **Workspace 注入**：把文件写入 `<workspace>/skills/<name>/`（SKILL.md + prompts/ + resources/）；
4. 注册只读 Skill 仓库（唯一技能来源，隔离其它应用/历史残留），
   HarnessSkillMiddleware 把 `<available_skills>` 注入系统提示并安装技能加载工具；
5. 单个 Skill 解析/注入失败时跳过，不中断整个运行。

## 典型使用流程

```text
打包 SKILL.md（frontmatter 含 name/description）→ 上传 → 发布版本
→ Agent 绑定 Skill → 发布 Agent → 对话时自动注入 → 回滚/禁用
```
