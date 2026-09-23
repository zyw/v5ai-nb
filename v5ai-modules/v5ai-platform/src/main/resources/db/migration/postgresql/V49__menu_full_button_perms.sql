-- ============================================================
-- V49：菜单树补齐到按钮级（一级目录 -> 二级菜单 -> 按钮）
--
-- 背景：V18 重建 RBAC 时只种了目录（M）与页面（C），V21 仅给「系统管理」
--   下的用户/角色/菜单补了 perms 与按钮（客户端/日志管理同期新增），
--   V22/V23/V24/V35 各自只补了自己新增的那一页。结果是「资源」组
--   （模型/Agent/知识库/MCP/Skill/工作流）与「运维」组（可观测性/
--   调试工具/系统信息）共 9 个二级菜单 perms 为 NULL、且一个按钮（F）
--   都没有——菜单管理页里这些菜单展开后是空的，权限码也无法作为
--   前端按钮显隐与后端 @SaCheckPermission 的依据。
--
-- 本次改动（只动 plm_menu / plm_role_menu，不改表结构）：
--   1) 幂等确保 4 个一级节点存在：总览（C，顶级页面）、资源/系统管理/
--      运维（M，目录；目录本身不带 perms）；
--   2) 给 9 个缺 perms 的二级菜单补 `域:实体:list`；
--   3) 按「查询/新增/修改/删除」五件套 + 各页面真实存在的特殊动作
--      （发布/启停/连通性测试/发现工具/版本回滚/运行/召回测试…）
--      补齐 F 按钮，权限码与后端 @SaCheckPermission 一一对应；
--   4) 顺带修正 API Keys 与资源存储 order_num 撞号（都曾是 18）；
--   5) 授权：ADMIN 拿全部；USER 拿「总览 + 资源组整组」（沿用 V18/V35
--      「USER 可见总览与资源组」的口径，也保证加了后端注解后普通角色
--      不会被 403）。
--
-- 本迁移**不做**：不新增表/列；不动「系统管理」下 V21 已补好的按钮；
--   不给「系统信息」加按钮（该页是纯静态展示，没有任何后端调用）。
--
-- 幂等：菜单以 menu_name 防重，按钮以 perms 防重，授权以 (role_id,menu_id)
--   防重；重复执行不产生重复行。历史迁移只增不改。
-- ============================================================

-- ---------- 1. 一级节点（目录 M + 顶级页面 C） ----------

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '总览', 0, 1, 'dashboard', 'dashboard/index', 'C', '0', '0', null, 'dashboard'
where not exists (select 1 from plm_menu where menu_name = '总览' and parent_id = 0);

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源', 0, 10, 'resource', null, 'M', '0', '0', null, 'resource'
where not exists (select 1 from plm_menu where menu_name = '资源' and parent_id = 0);

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '系统管理', 0, 20, 'system', null, 'M', '0', '0', null, 'system'
where not exists (select 1 from plm_menu where menu_name = '系统管理' and parent_id = 0);

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '运维', 0, 30, 'ops', null, 'M', '0', '0', null, 'ops'
where not exists (select 1 from plm_menu where menu_name = '运维' and parent_id = 0);

-- ---------- 2. 二级菜单补 perms（9 个） ----------
-- 目录（M）不带 perms，只有页面（C）带；与 V21 的 system:user:list 等口径一致。

update plm_menu set perms = 'model:model:list'     where menu_name = '模型管理'   and menu_type = 'C' and perms is null;
update plm_menu set perms = 'agent:agent:list'     where menu_name = 'Agent 管理' and menu_type = 'C' and perms is null;
update plm_menu set perms = 'rag:knowledge:list'   where menu_name = '知识库管理' and menu_type = 'C' and perms is null;
update plm_menu set perms = 'mcp:server:list'      where menu_name = 'MCP 管理'   and menu_type = 'C' and perms is null;
update plm_menu set perms = 'skill:skill:list'     where menu_name = 'Skill 管理' and menu_type = 'C' and perms is null;
update plm_menu set perms = 'workflow:workflow:list' where menu_name = '工作流'   and menu_type = 'C' and perms is null;
update plm_menu set perms = 'monitor:usage:list'   where menu_name = '可观测性'   and menu_type = 'C' and perms is null;
update plm_menu set perms = 'monitor:debug:list'   where menu_name = '调试工具'   and menu_type = 'C' and perms is null;
update plm_menu set perms = 'monitor:settings:list' where menu_name = '系统信息'  and menu_type = 'C' and perms is null;

-- ---------- 3. 按钮（F）：模型管理 ----------
-- 对应 V5aiModelController（/api/admin/models）与 V5aiModelProviderController
-- （/api/admin/providers，同一页面下的供应商表格）。

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型查询', id, 1, '', '', 'F', '0', '0', 'model:model:query', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型新增', id, 2, '', '', 'F', '0', '0', 'model:model:add', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型修改', id, 3, '', '', 'F', '0', '0', 'model:model:edit', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型删除', id, 4, '', '', 'F', '0', '0', 'model:model:remove', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:remove');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型启停', id, 5, '', '', 'F', '0', '0', 'model:model:changeStatus', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:changeStatus');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型连通性测试', id, 6, '', '', 'F', '0', '0', 'model:model:test', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:test');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型设为默认', id, 7, '', '', 'F', '0', '0', 'model:model:setDefault', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:setDefault');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '供应商查询', id, 8, '', '', 'F', '0', '0', 'model:provider:query', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:provider:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '供应商新增', id, 9, '', '', 'F', '0', '0', 'model:provider:add', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:provider:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '供应商修改', id, 10, '', '', 'F', '0', '0', 'model:provider:edit', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:provider:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '供应商删除', id, 11, '', '', 'F', '0', '0', 'model:provider:remove', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:provider:remove');

-- ---------- 4. 按钮（F）：Agent 管理 ----------
-- 对应 AgentController（/api/admin/agents）与 V5aiAppQuotaController
-- （/api/admin/apps/{agentKey}/quota，Agent 列表行的「配额」弹窗）。

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 查询', id, 1, '', '', 'F', '0', '0', 'agent:agent:query', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 新增', id, 2, '', '', 'F', '0', '0', 'agent:agent:add', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 修改', id, 3, '', '', 'F', '0', '0', 'agent:agent:edit', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 删除', id, 4, '', '', 'F', '0', '0', 'agent:agent:remove', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:remove');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 发布', id, 5, '', '', 'F', '0', '0', 'agent:agent:publish', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:publish');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 禁用', id, 6, '', '', 'F', '0', '0', 'agent:agent:disable', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:disable');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 配置生成', id, 7, '', '', 'F', '0', '0', 'agent:agent:generate', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:generate');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 版本查询', id, 8, '', '', 'F', '0', '0', 'agent:agent:version', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:version');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '应用配额查询', id, 9, '', '', 'F', '0', '0', 'agent:quota:query', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:quota:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '应用配额修改', id, 10, '', '', 'F', '0', '0', 'agent:quota:edit', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:quota:edit');

-- ---------- 5. 按钮（F）：知识库管理 ----------
-- 对应 KnowledgeBaseController / KnowledgeDocumentController /
-- KnowledgeChunkController / KnowledgeDebugController（/api/admin/knowledge-bases…）。

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库查询', id, 1, '', '', 'F', '0', '0', 'rag:knowledge:query', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库新增', id, 2, '', '', 'F', '0', '0', 'rag:knowledge:add', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库修改', id, 3, '', '', 'F', '0', '0', 'rag:knowledge:edit', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库删除', id, 4, '', '', 'F', '0', '0', 'rag:knowledge:remove', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:remove');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库启停', id, 5, '', '', 'F', '0', '0', 'rag:knowledge:changeStatus', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:changeStatus');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档查询', id, 6, '', '', 'F', '0', '0', 'rag:document:list', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:list');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档上传', id, 7, '', '', 'F', '0', '0', 'rag:document:add', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档删除', id, 8, '', '', 'F', '0', '0', 'rag:document:remove', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:remove');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档重新解析', id, 9, '', '', 'F', '0', '0', 'rag:document:reparse', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:reparse');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档下载', id, 10, '', '', 'F', '0', '0', 'rag:document:download', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:download');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '分片查询', id, 11, '', '', 'F', '0', '0', 'rag:chunk:list', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:chunk:list');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '分片新增', id, 12, '', '', 'F', '0', '0', 'rag:chunk:add', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:chunk:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '分片修改', id, 13, '', '', 'F', '0', '0', 'rag:chunk:edit', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:chunk:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '分片删除', id, 14, '', '', 'F', '0', '0', 'rag:chunk:remove', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:chunk:remove');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '召回测试', id, 15, '', '', 'F', '0', '0', 'rag:knowledge:retrieve', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:retrieve');

-- ---------- 6. 按钮（F）：MCP 管理 ----------
-- 对应 McpServerController / McpToolCallController（/api/admin/mcp-servers、
-- /api/admin/mcp-tool-calls）。MCP Server 没有物理删除接口，只有启停，
-- 故不设「服务删除」按钮。

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '服务查询', id, 1, '', '', 'F', '0', '0', 'mcp:server:query', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '服务新增', id, 2, '', '', 'F', '0', '0', 'mcp:server:add', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '服务修改', id, 3, '', '', 'F', '0', '0', 'mcp:server:edit', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '服务启停', id, 4, '', '', 'F', '0', '0', 'mcp:server:changeStatus', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:changeStatus');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '连接测试', id, 5, '', '', 'F', '0', '0', 'mcp:server:test', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:test');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工具发现', id, 6, '', '', 'F', '0', '0', 'mcp:server:discover', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:discover');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工具查询', id, 7, '', '', 'F', '0', '0', 'mcp:tool:list', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:tool:list');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工具权限修改', id, 8, '', '', 'F', '0', '0', 'mcp:tool:edit', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:tool:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '调用记录查询', id, 9, '', '', 'F', '0', '0', 'mcp:toolCall:list', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:toolCall:list');

-- ---------- 7. 按钮（F）：Skill 管理 ----------
-- 对应 SkillController（/api/admin/skills），含版本发布/下线/回滚与文件编辑。

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 查询', id, 1, '', '', 'F', '0', '0', 'skill:skill:query', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 新增', id, 2, '', '', 'F', '0', '0', 'skill:skill:add', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 修改', id, 3, '', '', 'F', '0', '0', 'skill:skill:edit', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 删除', id, 4, '', '', 'F', '0', '0', 'skill:skill:remove', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:remove');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 启停', id, 5, '', '', 'F', '0', '0', 'skill:skill:changeStatus', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:changeStatus');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本查询', id, 6, '', '', 'F', '0', '0', 'skill:version:list', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:list');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本删除', id, 7, '', '', 'F', '0', '0', 'skill:version:remove', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:remove');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本发布', id, 8, '', '', 'F', '0', '0', 'skill:version:publish', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:publish');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本下线', id, 9, '', '', 'F', '0', '0', 'skill:version:offline', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:offline');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本回滚', id, 10, '', '', 'F', '0', '0', 'skill:version:rollback', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:rollback');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 文件编辑', id, 11, '', '', 'F', '0', '0', 'skill:file:edit', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:file:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 内容生成', id, 12, '', '', 'F', '0', '0', 'skill:skill:ai', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:ai');

-- ---------- 8. 按钮（F）：工作流 ----------
-- 对应 WorkflowController（/api/admin/workflows）。删除接口语义是「禁用」，
-- 权限码仍按写操作命名为 remove，与后端注解保持一致。

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流查询', id, 1, '', '', 'F', '0', '0', 'workflow:workflow:query', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流新增', id, 2, '', '', 'F', '0', '0', 'workflow:workflow:add', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流编排', id, 3, '', '', 'F', '0', '0', 'workflow:workflow:edit', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流删除', id, 4, '', '', 'F', '0', '0', 'workflow:workflow:remove', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:remove');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流发布', id, 5, '', '', 'F', '0', '0', 'workflow:workflow:publish', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:publish');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流运行', id, 6, '', '', 'F', '0', '0', 'workflow:workflow:run', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:run');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '运行记录查询', id, 7, '', '', 'F', '0', '0', 'workflow:run:list', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:run:list');

-- ---------- 9. 按钮（F）：运维组 ----------
-- 可观测性 = V5aiModelUsageController（/api/admin/usage）；
-- 调试工具 = AgentDebugController（/api/admin/agents/{agentKey}/chat/stream）；
-- 系统信息是纯静态页（无后端调用），只保留菜单本身、不加按钮。

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '用量查询', id, 1, '', '', 'F', '0', '0', 'monitor:usage:query', '#'
from plm_menu where menu_name = '可观测性' and not exists (select 1 from plm_menu where perms = 'monitor:usage:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '调试对话', id, 1, '', '', 'F', '0', '0', 'monitor:debug:chat', '#'
from plm_menu where menu_name = '调试工具' and not exists (select 1 from plm_menu where perms = 'monitor:debug:chat');

-- ---------- 10. 修正 order_num 撞号 ----------
-- V24 把「资源存储」放在 18，V35 又把「API Keys」也放在 18，两者同级同序。
-- 这里把 API Keys 顺延到 19，保持资源组顺序：…工作流16 / 存储实例17 / 资源存储18 / API Keys19。

update plm_menu set order_num = 19 where menu_name = 'API Keys' and menu_type = 'C' and order_num = 18;

-- ---------- 11. 授权 ----------

-- ADMIN：本迁移涉及的全部菜单与按钮。
insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and (m.menu_name in ('总览', '资源', '系统管理', '运维')
    or m.parent_id in (select id from plm_menu where menu_name in ('模型管理', 'Agent 管理', '知识库管理', 'MCP 管理', 'Skill 管理'))
    or m.parent_id in (select id from plm_menu where menu_name in ('工作流', '可观测性', '调试工具', '系统信息')))
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);

-- USER：总览 + 「资源」目录及其整组页面与按钮。
-- 口径沿用 V18（USER 可见总览与资源组）与 V35（API Keys 同时授权 ADMIN/USER）。
-- 目录行本身 V18 漏授过，这里一并补上，否则侧边栏拿不到父级。
insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'USER'
  and (m.menu_name = '总览'
    or m.menu_name = '资源'
    or m.parent_id = (select id from plm_menu where menu_name = '资源' and parent_id = 0)
    or m.parent_id in (select id from plm_menu where parent_id = (select id from plm_menu where menu_name = '资源' and parent_id = 0)))
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);
