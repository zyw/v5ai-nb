# v5ai-nb Platform Context

The v5ai-nb 平台统一管理模型、Agent、知识库（RAG）、MCP Server、Skill、会话与运行记录。本上下文收录平台内独有的领域术语。

## Skill（技能）

**Skill**:
平台内可被 Agent 绑定并在运行时注入 Workspace 的可复用能力包。
_Avoid_: 技能包（指压缩包文件形态）、Application（历史遗留名）

**SKILL.md**:
每个 Skill 的入口定义文件，带 YAML Front Matter（`name`、`description` 必填非空）+ 正文；缺失时该 Skill 无法发布/注入。
_Avoid_: README、说明文件

**Skill 包**:
Skill 的 zip 文件形态（SKILL.md + prompts/ + resources/），通过上传或在线编辑产生。
_Avoid_: 技能压缩包（口语）

**版本（Version）**:
Skill 的可发布快照，状态为 DRAFT（草稿，可编辑/可发布）或 PUBLISHED（已发布，运行时注入「当前版本」）。
_Avoid_: 草稿版本号、发布版本（口语）

**当前版本（Current Version）**:
Skill 上指向运行时注入版本的指针；下线当前版本会清除该指针，Skill 随即停止注入、不可再被绑定。

**上线 / 下线**:
发布（DRAFT → PUBLISHED）与撤下（PUBLISHED → DRAFT）版本的操作；下线当前版本是删除/禁用 Skill 的前置条件。
_Avoid_: 回滚（回滚为兼容保留的旧接口，不再作为版本管理主流程）

**AI生成**:
按「需求说明」起草 SKILL.md 内容（含合法 frontmatter）的 AI 操作，只作用于 SKILL.md 文件。
_Avoid_: 自动生成（避免与其它自动生成混淆）

**AI优化**:
按「需求说明」（必填）+「优化方向」（选填）重写当前打开文件的 AI 操作。
_Avoid_: AI 改写（与优化同义，取其一）
