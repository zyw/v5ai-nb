# 工作流批量删除实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为工作流列表增加真正的批量物理删除，仅允许删除 DRAFT/DISABLED 工作流，并原子清理其版本与运行历史。

**Architecture:** 管理 API 新增批量删除 DTO 与 DELETE 端点；服务层在事务中锁定并验证完整批次后，按节点运行、运行、版本、工作流的顺序删除。前端用已勾选工作流的 Key 调用该端点，在刷新操作旁显示带数量的删除按钮，并在确认后清空选择、刷新列表。

**Tech Stack:** Java 21、Spring MVC、Spring Transaction、MyBatis-Plus、JUnit 5、Mockito、Vue 3、TypeScript、Naive UI、Vite。

**Spec:** `docs/superpowers/specs/2026-09-25-workflow-batch-delete-design.md`

## Global Constraints

- 单条 `DELETE /api/admin/workflows/{key}` 保持现有“禁用”语义。
- 批量物理删除只接受 DRAFT 与 DISABLED；任一工作流不存在或为其他状态时整批失败且不产生部分删除。
- 任一所选工作流有 RUNNING 状态的运行时，整批拒绝删除。
- 运行记录准入与批量删除必须锁定同一工作流行；运行记录插入在持锁事务内完成，避免“检查后新运行插入”的竞态。
- 删除主记录前，必须在同一事务内删除节点运行记录、运行记录与发布版本。
- 使用现有 `workflow:workflow:remove` 权限；批量最多 100 个工作流 Key。
- 不添加第三方依赖、不新增数据库迁移；保持 PostgreSQL 与 MySQL 兼容。
- 不提交 Git commit，除非用户明确要求。

## Review Focus

- 混合批次中含 PUBLISHED 工作流：确保没有任何 DRAFT/DISABLED 项被部分删除；归 Task 1/2 测试。
- 所选工作流包含 RUNNING 的执行记录：整批拒绝且不触碰任何历史；归 Task 1/2 测试。
- 一个 workflow 有零个、一个或多个 runs：节点运行与版本数据被完整清理且没有空 `IN` 查询；归 Task 1/2 测试。
- 并发发布、运行准入与删除：锁定后状态重新验证，已发布项不能在检查/删除竞态中被移除；运行准入锁定同一行并在持锁事务内插入运行记录；归 Task 2 测试或 mapper 验证。
- 请求包含重复、空白、不存在或超过 100 个 Key：请求校验与服务端防御行为一致；归 Task 1/3 测试。
- 前端删除确认取消、成功、失败：取消不请求；成功清选择并刷新；失败保留选择；归 Task 4 手动验收。

---

### Task 1: 批量删除请求契约与服务测试

**Files:**
- Create: `v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/domain/bo/WorkflowBatchDeleteBo.java`
- Modify: `v5ai-modules/v5ai-workflow/src/test/java/xin/v5ai/nb/workflow/service/impl/WorkflowServiceImplTest.java`
- Modify: `v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/service/IWorkflowService.java`

**Interfaces:**
- Consumes: Existing workflow status values `DRAFT`, `PUBLISHED`, `DISABLED` and three workflow mappers.
- Produces: `WorkflowBatchDeleteBo.workflowKeys: List<String>` and `IWorkflowService.deleteBatch(List<String> workflowKeys): void`.

- [ ] **Step 1: Write failing service tests** in `WorkflowServiceImplTest` for: deleting a DRAFT and a DISABLED item in one batch; rejecting a batch containing a PUBLISHED item without deleting any selected workflow; rejecting a batch containing a RUNNING execution without deleting any selected workflow; rejecting an unknown Key; rejecting an empty list, blank Key, and more than 100 distinct Keys; and accepting duplicate Keys while deleting the workflow only once. Keep test fixtures for workflow, runs, node runs, and versions in local lists and verify mapper delete calls and arguments.
- [ ] **Step 2: Run the focused test and verify it fails because `deleteBatch` is absent.**

Run: `mvn -pl v5ai-modules/v5ai-workflow -am -Dmaven.test.skip=false -DskipTests=false -Dtest=WorkflowServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation/test failure identifying the missing service method.
- [ ] **Step 3: Add the request carrier and service interface signature.** Define `WorkflowBatchDeleteBo` with `List<String> workflowKeys`, `@NotEmpty`, and element `@NotBlank`; leave duplicate and maximum-size validation to service normalization so non-controller callers are protected too.
- [ ] **Step 4: Run the focused test again.**

Run: `mvn -pl v5ai-modules/v5ai-workflow -am -Dmaven.test.skip=false -DskipTests=false -Dtest=WorkflowServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: test source compiles; behavioral tests remain red until Task 2.

### Task 2: 事务服务与锁定读取

**Files:**
- Modify: `v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/mapper/WorkflowMapper.java`
- Modify: `v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/service/impl/WorkflowServiceImpl.java`
- Modify: `v5ai-modules/v5ai-workflow/src/test/java/xin/v5ai/nb/workflow/service/impl/WorkflowServiceImplTest.java`

**Interfaces:**
- Consumes: `IWorkflowService.deleteBatch(List<String>)` and `WorkflowBatchDeleteBo.workflowKeys`.
- Produces: `WorkflowMapper.selectBatchForUpdate(List<String>): List<Workflow>`; `WorkflowServiceImpl.deleteBatch` transactionally removes associated data.

- [ ] **Step 1: Add mapper-level test coverage or wrapper-capture verification** proving the selected workflow query filters the submitted keys and appends a `FOR UPDATE` lock clause. Use MyBatis-Plus `LambdaQueryWrapper` and preserve both dialects’ supported `SELECT ... FOR UPDATE` syntax.
- [ ] **Step 2: Run the focused mapper/service tests and verify the lock/query assertion fails before implementation.**
- [ ] **Step 3: Implement `WorkflowMapper.selectBatchForUpdate(List<String>)` using `selectList(wrapper.in(Workflow::getWorkflowKey, keys).last("FOR UPDATE"))`; only call it with a non-empty normalized key list.**
- [ ] **Step 4: Implement `deleteBatch`: trim and distinct keys; reject empty, blank, or >100 inputs; load locked rows; reject if found count differs from requested distinct keys; reject if any status is `PUBLISHED`; gather all run IDs; delete node runs by `run_id`, then runs by `workflow_key`, versions by `workflow_key`, and finally workflows by `workflow_key`. Skip node-run deletion when there are no run IDs. Annotate with `@Transactional(rollbackFor = Exception.class)`.**
- [ ] **Step 5: Run the focused service and mapper tests and verify all pass.**

Run: `mvn -pl v5ai-modules/v5ai-workflow -am -Dmaven.test.skip=false -DskipTests=false -Dtest=WorkflowServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all focused tests pass, including atomic rejection and cleanup scope.

### Task 3: 管理端批量 API 与权限

**Files:**
- Modify: `v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/controller/WorkflowController.java`
- Create: `v5ai-modules/v5ai-workflow/src/test/java/xin/v5ai/nb/workflow/controller/WorkflowControllerTest.java`
- Modify: `v5ai-modules/v5ai-workflow/src/test/java/xin/v5ai/nb/workflow/service/impl/WorkflowServiceImplTest.java`

**Interfaces:**
- Consumes: `WorkflowBatchDeleteBo` and `IWorkflowService.deleteBatch(List<String>)`.
- Produces: `DELETE /api/admin/workflows/batch`, guarded by `workflow:workflow:remove`, returning `R<Void>`.

- [ ] **Step 1: Add a controller test** asserting the batch endpoint mapping, body forwarding, and `@SaCheckPermission("workflow:workflow:remove")`; assert legacy `DELETE /{key}` still delegates to `disable`.
- [ ] **Step 2: Run the controller test and verify it fails because the endpoint is absent.**
- [ ] **Step 3: Add the endpoint before `@DeleteMapping("/{key}")` so the literal `batch` route cannot be captured as a Key route. Add `@Validated` body validation and `@Log(title = "批量删除工作流", businessType = BusinessType.DELETE)`; delegate to `deleteBatch` and return `R.ok()`.**
- [ ] **Step 4: Run module tests.**

Run: `mvn -pl v5ai-modules/v5ai-workflow -am -Dmaven.test.skip=false -DskipTests=false test`

Expected: module test suite passes; any pre-existing unrelated failure is reported separately with its test name.

### Task 4: 前端批量删除交互

**Files:**
- Modify: `v5ai-ui/src/api/client.ts`
- Modify: `v5ai-ui/src/views/WorkflowsView.vue`

**Interfaces:**
- Consumes: `DELETE /api/admin/workflows/batch` with JSON `{ workflowKeys: string[] }`.
- Produces: `deleteWorkflows(adminToken: string, workflowKeys: string[]): Promise<void>` and conditional “删除所选（N）” action.

- [ ] **Step 1: Implement `deleteWorkflows` next to existing workflow API methods** using `requestJson<void>('/api/admin/workflows/batch', { method: 'DELETE', body: JSON.stringify({ workflowKeys }) }, adminToken)`.
- [ ] **Step 2: Add `handleBatchDelete` to `WorkflowsView.vue`.** Map `selectedWorkflowIds` to `workflowKey` using current table rows, confirm permanent removal of workflow versions and run histories, call the API only after positive confirmation, and on success clear `selectedWorkflowIds` and await `reload()`. On error show `message.error` and retain selected IDs.
- [ ] **Step 3: Render a danger-style “删除所选（N）” button adjacent to the existing refresh action only when `selectedWorkflowIds.length > 0`.** Do not alter row-selection state or existing horizontal scrolling behavior.
- [ ] **Step 4: Run `npm run build` in `v5ai-ui`.**

Expected: Vue type checking and Vite production build pass.

### Task 5: 最终验收

**Files:**
- Verify: `docs/superpowers/specs/2026-09-25-workflow-batch-delete-design.md`
- Verify: `v5ai-modules/v5ai-workflow` and `v5ai-ui`

- [ ] **Step 1: Run complete workflow-module tests.**

Run: `mvn -pl v5ai-modules/v5ai-workflow -am -Dmaven.test.skip=false -DskipTests=false test`

Expected: pass, or record any unrelated pre-existing failing test by exact name.
- [ ] **Step 2: Run frontend production build.**

Run: `npm run build` from `v5ai-ui/`.

Expected: exit code 0; existing large-chunk warning may remain.
- [ ] **Step 3: Review `git diff --check` and verify no migration or dependency files changed.**
- [ ] **Step 4: Manual API/UI acceptance:** select a DRAFT and DISABLED workflow, cancel then confirm deletion; verify cancel makes no request, confirm removes rows and histories, and selection clears. Select a PUBLISHED workflow or mixed PUBLISHED/non-PUBLISHED batch; verify the API rejects the entire batch, all records remain, and the UI retains selection and displays the backend error.
