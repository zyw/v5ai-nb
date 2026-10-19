# Workflow 工作流

工作流以节点和边定义执行路径。当前仓库已有可视化编辑器、定义校验、版本发布、管理 API 和基础节点执行能力；完整运行时闭环仍在持续完善。生产采用 Python 节点前还需完成独立 Runner 的 Linux gVisor 隔离验收。

![工作流编辑器：节点库、画布与属性面板](/images/workflow-editor.png)

## 编辑器里的工作方式

1. 创建工作流并进入画布。
2. 从左侧节点库加入开始、Agent、条件、HTTP 请求、Python 脚本、JSON 转换等节点。
3. 连线并在右侧属性面板配置节点输入、输出和条件。
4. 使用 **校验** 检查定义，保存草稿，再运行预览。
5. 确认后发布版本；历史版本可查看与恢复。

节点间可以引用前序节点输出。发布使用定义快照，运行记录包含工作流 Run 与节点 Run 状态。

## Python 节点

Python 脚本通过独立 FastAPI Runner 执行。Runner 为每次执行启动一次性 OCI 沙箱；未配置 Runner URL 或 Token 时，节点会拒绝执行。部署样例与安全要求见仓库 `docs/deploy/workflow-python-runner.md`。本地 Docker Desktop 调通协议不等于生产隔离验收。

## 当前边界

此处展示的是项目当前源码中的管理与基础执行能力。可视化编排的体验、长任务处理与完整运行闭环仍在演进。上生产前应按实际部署版本执行端到端验收，尤其检查外部 HTTP 访问白名单、密钥引用、Python Runner 的 gVisor 隔离和资源限制。

API 入口包括 `POST /api/admin/workflows/{key}/validate`、`POST /api/admin/workflows/{key}/publish`、`POST /api/admin/workflows/{key}/run` 和 `GET /api/admin/workflows/runs/{runId}`。定义与节点规范见仓库 `docs/workflow实现设计文档.md`。
