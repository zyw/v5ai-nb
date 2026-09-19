-- ============================================================
-- V44：删除遗留死表 v5ai_agent_model
--
-- 背景：该表由 V1 的 v5ai_application_model 经 V8 改名而来，是「应用 ↔ 模型」的早期绑定表；
--   后来模型绑定改为 v5ai_agent.model_id 单列，这张表就再没有任何写入方。
--
-- 删除依据（已逐条核实）：
--   · 现网 0 行；
--   · 全仓无实体类（没有 @TableName("v5ai_agent_model") 的类）；
--   · 无任何 INSERT/UPDATE；
--   · 唯一的引用是 AgentCleanupMapper.deleteAgentModels —— 一条删除 0 行的死 SQL，
--     已随本提交一并从 Mapper 接口、XML 与 AgentDeletionServiceImpl 调用处移除；
--   · 无测试依赖。
--
-- 顺序要求：删代码与删表必须同一个提交。若只删表不删代码，级联删除会在运行期报
--   relation "v5ai_agent_model" does not exist；若只删代码不删表，就留下「表还在但没人管」。
-- ============================================================

DROP TABLE IF EXISTS v5ai_agent_model;
