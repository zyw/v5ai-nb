# 编写与发布 Skill

Skill 是带有 `SKILL.md` 的文件包，可把团队的操作说明与参考资料作为运行时上下文交给 Agent。平台支持上传 zip，也支持在线新建和编辑文件。

## 准备文件

Skill 的入口是 `SKILL.md`。文件头需要合法的 frontmatter，包中可继续放参考资料和辅助文件。上传时平台会校验结构、文件大小、编码和路径穿越。

一个简化结构：

```text
my-skill/
├── SKILL.md
└── references/
    └── examples.md
```

在线编辑器提供文件树与文档编辑区，可新增、修改、删除文件；还提供 AI 生成和优化入口。变更先进入草稿版本，发布后才成为运行时的当前版本。

![Skill 文件编辑器](/images/skill-editor.png)

## 发布和绑定

1. 上传 zip 或在线创建 Skill。
2. 检查 `SKILL.md` 与参考文件，保存草稿。
3. 发布目标版本；必要时可以回滚到旧版本。
4. 将 Skill 绑定到 Agent，并开启 Agent 的 Skill 能力开关。
5. 在管理端调试 Agent，确认它能读取 Skill。

运行时把当前发布版本注入 `workspace/skills/<name>/`，同时提供只读 Skill 仓库清单。禁用 Skill 或关闭 Agent 的 Skill 开关后，不再加载该能力。

## 相关 API

```http
POST /api/admin/skills                 # multipart zip 上传
POST /api/admin/skills/online          # 在线创建
GET  /api/admin/skills/{id}/editor
PUT  /api/admin/skills/{id}/files
POST /api/admin/skills/{id}/versions/{versionId}/publish
POST /api/admin/skills/{id}/rollback
POST /api/admin/agents/{agentKey}/skill-bindings
```

完整包格式、版本与编辑器接口见仓库 `docs/api/phase4.md`。
