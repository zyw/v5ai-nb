# Phase 4 Verification（Skill）

验证时间：2026-08-15

## 命令

```bash
set JAVA_HOME=D:\softwares\jdk\bellsoft-jdk-21.0.8+12
mvn clean verify -Dsurefire.failIfNoSpecifiedTests=false
cd v5ai-ui && npx vue-tsc --noEmit
```

结果：后端 `verify` exit 0（新增 23 个测试，全部通过），前端 `vue-tsc --noEmit` exit 0。

## 本轮完成范围（Skill Phase 4）

### 新模块 v5ai-skill（领域层）

- 领域模型：`SkillDTO`（name 唯一、status、当前发布版本指针）、`SkillVersion`（DRAFT/PUBLISHED、
  版本号自增）、`SkillFileDTO`（发布版本文件）、`ResolvedSkill`（运行时注入载体）。
- 端口：`SkillRepository`、`SkillVersionRepository`、`SkillFileRepository`、
  `ApplicationSkillBindingRepository`、`SkillWorkspaceResolver`。
- `SkillPackageParser`：zip 包解析与校验（大小/数量/路径穿越/绝对路径/UTF-8 文本/SKILL.md
  结构与必填字段/技能名安全）；支持 `metadata.yaml` 合并（frontmatter 优先）。
- `SkillManagementService`：上传（生成 DRAFT 版本）、版本发布、回滚（当前版本指针）、
  Agent 绑定（要求已发布 + ACTIVE）、禁用、当前版本文件解析。

### 基础设施

- `V6__skill_schema.sql`：`v5ai_skill`（current_version_id 不建外键避免循环引用）、
  `v5ai_skill_version`（skill_id+version 唯一）、`v5ai_skill_file`（version_id+file_path 唯一）、
  `v5ai_application_skill`（agent_key 绑定）。
- 实体/Mapper/仓储：Skill 列表 JOIN 带出当前版本号；版本列表按版本号降序。
- `ApplicationSkillWorkspaceResolver`：按已发布 Agent 绑定解析 ACTIVE + 已发布 Skill
  的当前版本文件，单 Skill 失败跳过。

### 运行时（Workspace 注入）

- `ModelStreamTextExecutor` 扩展：无 MCP/Skill 走轻量路径；有任一扩展走 HarnessAgent 路径；
- Workspace 注入：把当前发布版本文件写入 `<workspace>/skills/<name>/`；
- `ResolvedSkillAgentSkillRepository`：平台已发布 Skill 到 AgentScope `AgentSkillRepository`
  的只读适配（经 `SkillUtil.createFrom` 构建），作为唯一技能来源注册进 HarnessAgent，
  `HarnessSkillMiddleware` 注入 `<available_skills>` 到系统提示；无 Skill 时保持原有
  disableDynamicSkills/disableDefaultWorkspaceSkills 行为。

### Admin API + 前端

- `SkillController`（上传 multipart、列表、禁用、版本列表、发布、回滚）、
  `ApplicationSkillBindingController`（skill-bindings）。
- 前端：`SkillsView`（上传 zip、版本表发布/回滚、Agent 绑定）、router 与菜单注册；
  `client.ts` 补齐 Skill API。

## 说明 / 限制

- 运行时注入的是「当前版本」：发布新版本会改变所有已绑定应用的运行时内容（版本固定
  留待后续增强）；回滚即改变当前版本指针。
- Skill 文件仅支持 UTF-8 文本（二进制资源、非文本 prompts 留待后续阶段）。
- 绑定为 skillId 粒度（始终使用当前发布版本）；若需要"按版本固定"，可在后续阶段引入
  应用版本快照。
- 上传即生成 DRAFT 版本；DRAFT 不注入运行时，必须发布。
