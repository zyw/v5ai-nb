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
Skill 的可发布快照，状态三态：DRAFT（编辑中，唯一可编辑）/ PUBLISHED（已发布，运行时注入「当前版本」）/ OFFLINE（已下线，内容冻结不可编辑，可重新发布或删除）。
_Avoid_: 草稿版本号、发布版本（口语）

**当前版本（Current Version）**:
Skill 上指向运行时注入版本的指针；下线当前版本会清除该指针，Skill 随即停止注入、不可再被绑定。

**上线 / 下线**:
发布（DRAFT/OFFLINE → PUBLISHED）与撤下（PUBLISHED → OFFLINE）版本的操作；Skill 存在任何已发布版本时不可禁用/删除，须先全部下线。
_Avoid_: 回滚（回滚为兼容保留的旧接口，不再作为版本管理主流程）

**AI生成**:
按「需求说明」起草 SKILL.md 内容（含合法 frontmatter）的 AI 操作，只作用于 SKILL.md 文件。
_Avoid_: 自动生成（避免与其它自动生成混淆）

**AI优化**:
按「需求说明」（必填）+「优化方向」（选填）重写当前打开文件的 AI 操作。
_Avoid_: AI 改写（与优化同义，取其一）

**模型对话（Model Chat）**:
平台按已配置的 Chat 模型发起的一次文本补全调用（输入消息列表，返回完整文本或首个响应块）。
_Avoid_: 大模型调用、LLM 请求（口语）

## Resource（资源）

**访问URL（access_url）**:
资源的稳定逻辑引用，用于访问/展示资源内容；始终为应用内鉴权路径，与存储后端（本地磁盘 / MinIO）、桶私有策略或签名有效期无关。
_Avoid_: 对象直链、MinIO 裸 URL、预签名 URL（「具体定位符」是与稳定引用不同的概念）

**图标（Icon）**:
供应商的可选视觉标识；取值为资源 `access_url`（应用内路径）或外部网络 URL，通过「上传 / 资源库单选（AVATAR）/ 网络地址」三种方式设置。
_Avoid_: Logo 文件、图标文件（指存储介质，而非「选择后的值」）
