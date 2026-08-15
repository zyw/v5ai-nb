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
DELETE /api/admin/skills/{id}              # 禁用（禁止新上传/新绑定，运行时跳过）
GET    /api/admin/skills/{id}/versions     # 版本列表（按版本号降序）
```

## 版本发布与回滚

```http
POST /api/admin/skills/{id}/versions/{versionId}/publish   # DRAFT → PUBLISHED，并设为当前版本
POST /api/admin/skills/{id}/rollback
Content-Type: application/json

{ "versionId": <已发布的旧版本 id> }        # 把当前版本指针回滚到该版本
```

- 上传 → DRAFT；发布 → 成为「当前版本」；回滚 → 改变当前版本指针；
- 运行时只注入「当前版本」的不可变文件内容；
- 只能发布 DRAFT 版本、只能回滚到已发布的版本。

## Application 绑定

```http
POST /api/admin/applications/{appKey}/skill-bindings
Content-Type: application/json

{ "skillIds": [1, 2] }                     # 全量替换

GET /api/admin/applications/{appKey}/skill-bindings
```

绑定前校验：Skill 必须 ACTIVE 且已有当前发布版本。

## 运行时（Workspace 注入）

`POST /api/v1/apps/{appKey}/chat/stream` 在 Application 绑定了 Skill 时：

1. 解析已发布 Application 的 Skill 绑定；
2. 读取每个 Skill 当前发布版本的完整文件；
3. **Workspace 注入**：把文件写入 `<workspace>/skills/<name>/`（SKILL.md + prompts/ + resources/）；
4. 注册只读 Skill 仓库（唯一技能来源，隔离其它应用/历史残留），
   HarnessSkillMiddleware 把 `<available_skills>` 注入系统提示并安装技能加载工具；
5. 单个 Skill 解析/注入失败时跳过，不中断整个运行。

## 典型使用流程

```text
打包 SKILL.md（frontmatter 含 name/description）→ 上传 → 发布版本
→ Application 绑定 Skill → 发布 Application → 对话时自动注入 → 回滚/禁用
```
