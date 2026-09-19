-- ============================================================
-- V43：补齐 schema 注释（列注释 + 表注释）
--
-- 背景：V1..V42 一路演进下来，注释是「谁顺手谁加」的状态——plm_* 那批（V18/V19 重写时补齐）
--   100% 有列注释与表注释，而 v5ai_* 的核心表（mcp/skill/run/workflow/agent_state…）几乎为空。
--   43 张表 420 列里，197 列有注释、223 列没有；半有半无比全无更糟：读者分不清「没注释」
--   是「无需说明」还是「漏了」。本迁移把缺口一次补齐。
--
-- 口径（每条注释都可追溯到证据，不编造）：
--   1) 第一语义源是实体字段的 JavaDoc（本迁移 141 列取自它）；
--   2) 第二语义源是写入/读取处的代码（如 daily_tokens 的「未参与拦截」取自 V5aiAppQuotaServiceImpl）；
--   3) 纯惯例列（id / created_at / updated_at / agent_key）按统一措辞，不逐列斟酌。
--
-- 边界：
--   · 纯 COMMENT ON，无一行 DDL —— 可以无脑信任，出错概率接近零；
--   · 只补缺失的列，已有注释（197 列）不重复声明、不重写措辞；
--   · COMMENT ON 幂等，重复执行结果一致。
--
-- 唯一例外：v5ai_agent_model 故意跳过。它是无实体类、无写入方、0 行的遗留表（V8 由
--   v5ai_application_model 改名而来），同批的 V44 会直接删掉整张表；给它写注释是纯粹的行将
--   就木。这里写明例外，是为了让「43 张表全覆盖」这个验收标准的缺口是写在明面上的。
-- ============================================================

-- ---- plm_resource ----
COMMENT ON COLUMN plm_resource.id IS '主键';

-- ---- v5ai_agent ----
COMMENT ON TABLE v5ai_agent IS 'Agent（智能体）：平台核心实体，agent_key 全局唯一；status 为 DRAFT/PUBLISHED/DISABLED；发布时把配置快照写入 v5ai_agent_version';
COMMENT ON COLUMN v5ai_agent.id IS '主键';
COMMENT ON COLUMN v5ai_agent.agent_key IS 'Agent 对外运行标识（全局唯一；运行时按它解析 Agent）';
COMMENT ON COLUMN v5ai_agent.name IS '名称';
COMMENT ON COLUMN v5ai_agent.description IS '描述';
COMMENT ON COLUMN v5ai_agent.model_id IS '绑定的模型（v5ai_model.id）';
COMMENT ON COLUMN v5ai_agent.status IS '状态（DRAFT / PUBLISHED / DISABLED）';
COMMENT ON COLUMN v5ai_agent.published_version IS '当前生效的已发布版本号（未发布为 null）';
COMMENT ON COLUMN v5ai_agent.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_agent.updated_at IS '更新时间';
COMMENT ON COLUMN v5ai_agent.system_prompt IS '系统提示词（随发布版本固化）';

-- ---- v5ai_agent_knowledge ----
COMMENT ON TABLE v5ai_agent_knowledge IS 'Agent ↔ 知识库绑定：复合主键 (agent_key, knowledge_base_id)，无 updated_at 列';
COMMENT ON COLUMN v5ai_agent_knowledge.agent_key IS 'Agent 对外运行标识（跨模块引用，不建外键）';
COMMENT ON COLUMN v5ai_agent_knowledge.knowledge_base_id IS '绑定的知识库 ID';
COMMENT ON COLUMN v5ai_agent_knowledge.created_at IS '绑定创建时间';

-- ---- v5ai_agent_mcp ----
COMMENT ON TABLE v5ai_agent_mcp IS 'Agent ↔ MCP Server 绑定：复合主键 (agent_key, mcp_server_id)';
COMMENT ON COLUMN v5ai_agent_mcp.agent_key IS 'Agent 对外运行标识（跨模块引用，不建外键）';
COMMENT ON COLUMN v5ai_agent_mcp.mcp_server_id IS '绑定的 MCP Server';
COMMENT ON COLUMN v5ai_agent_mcp.created_at IS '绑定创建时间';

-- ---- v5ai_agent_skill ----
COMMENT ON TABLE v5ai_agent_skill IS 'Agent ↔ Skill 绑定：复合主键 (agent_key, skill_id)，无自增主键列';
COMMENT ON COLUMN v5ai_agent_skill.agent_key IS 'Agent 对外运行标识（跨模块引用，不建外键）';
COMMENT ON COLUMN v5ai_agent_skill.skill_id IS '绑定的 Skill';
COMMENT ON COLUMN v5ai_agent_skill.created_at IS '绑定创建时间';

-- ---- v5ai_agent_state ----
COMMENT ON TABLE v5ai_agent_state IS 'Agent 运行状态：按会话保存的运行态 JSON，一会话一行';
COMMENT ON COLUMN v5ai_agent_state.conversation_id IS '所属会话（v5ai_conversation.id）；一会话一行';
COMMENT ON COLUMN v5ai_agent_state.state_json IS 'Agent 运行状态的 JSON 序列化结果（按会话滚动覆盖）';
COMMENT ON COLUMN v5ai_agent_state.updated_at IS '更新时间';

-- ---- v5ai_agent_version ----
COMMENT ON TABLE v5ai_agent_version IS 'Agent 发布版本：发布时固化的配置快照（snapshot_json），版本号在每个 Agent 内自增';
COMMENT ON COLUMN v5ai_agent_version.id IS '主键';
COMMENT ON COLUMN v5ai_agent_version.agent_key IS '所属 Agent 的对外标识（v5ai_agent.agent_key）';
COMMENT ON COLUMN v5ai_agent_version.version IS '版本号（每个 AgentDTO 内自增，从 1 起）';
COMMENT ON COLUMN v5ai_agent_version.snapshot_json IS '发布时固化的配置快照（JSON 文本）';
COMMENT ON COLUMN v5ai_agent_version.description IS '版本描述';
COMMENT ON COLUMN v5ai_agent_version.created_at IS '创建时间';

-- ---- v5ai_api_keys ----
COMMENT ON TABLE v5ai_api_keys IS 'Agent API Key：归属创建用户，可访问的 Agent 由 v5ai_api_keys_agent 绑定；库内只存摘要与密文（key_hash sha256 定位 / secret_hash BCrypt 校验）';
COMMENT ON COLUMN v5ai_api_keys.id IS '主键';
COMMENT ON COLUMN v5ai_api_keys.secret_hash IS 'API Key 密文（BCrypt）';
COMMENT ON COLUMN v5ai_api_keys.enabled IS '是否启用';
COMMENT ON COLUMN v5ai_api_keys.created_at IS '创建时间';

-- ---- v5ai_api_keys_agent ----
COMMENT ON COLUMN v5ai_api_keys_agent.id IS '主键';
COMMENT ON COLUMN v5ai_api_keys_agent.created_at IS '绑定创建时间（由数据库默认值 CURRENT_TIMESTAMP 填充，写入侧不赋值）';

-- ---- v5ai_app_quota ----
COMMENT ON TABLE v5ai_app_quota IS '按 Agent 维度的调用配额：每日调用次数、每日 token、每分钟限流；0 一律表示不限制';
COMMENT ON COLUMN v5ai_app_quota.agent_key IS 'Agent 对外运行标识（配额按 Agent 维度配置，本表主键）';
COMMENT ON COLUMN v5ai_app_quota.daily_model_calls IS '每日模型调用次数上限；0 = 不限制';
COMMENT ON COLUMN v5ai_app_quota.daily_tokens IS '每日 token 上限；0 = 不限制；当前未参与拦截（checkAllowed 只校验每分钟限流与每日调用次数）';
COMMENT ON COLUMN v5ai_app_quota.rate_per_minute IS '每分钟调用次数上限（进程内限流器）；0 = 不限制';
COMMENT ON COLUMN v5ai_app_quota.updated_at IS '更新时间';
COMMENT ON COLUMN v5ai_app_quota.created_at IS '创建时间';

-- ---- v5ai_app_usage ----
COMMENT ON TABLE v5ai_app_usage IS '按 Agent 的每日用量累计：复合主键 (agent_key, usage_date)，由 upsert 累加；勿使用 updateById/deleteById';
COMMENT ON COLUMN v5ai_app_usage.agent_key IS 'Agent 对外运行标识（复合主键之一）';
COMMENT ON COLUMN v5ai_app_usage.usage_date IS '统计自然日（按服务端 LocalDate.now() 切分；复合主键之一）';
COMMENT ON COLUMN v5ai_app_usage.model_calls IS '当日模型调用次数（每次调用 upsert 累加 1）';
COMMENT ON COLUMN v5ai_app_usage.tokens IS '当日累计 token（每次调用累加，负数按 0 计）';
COMMENT ON COLUMN v5ai_app_usage.updated_at IS '更新时间';
COMMENT ON COLUMN v5ai_app_usage.created_at IS '创建时间';

-- ---- v5ai_conversation ----
COMMENT ON TABLE v5ai_conversation IS '门户会话：归属主体是 API Key（api_key_id），user_id 只作展示冗余；调试入口产生的会话该列为 NULL';
COMMENT ON COLUMN v5ai_conversation.id IS '会话 ID（UUID 字符串）';
COMMENT ON COLUMN v5ai_conversation.agent_key IS 'Agent 对外运行标识（跨模块引用，不建外键；删除 Agent 时由 AgentCleanupMapper 显式清理）';
COMMENT ON COLUMN v5ai_conversation.user_id IS '归属用户（展示与审计冗余，只写不判；归属判定一律以 api_key_id 为准，见 ADR-0006）';
COMMENT ON COLUMN v5ai_conversation.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_conversation.updated_at IS '更新时间';

-- ---- v5ai_conversation_summary ----
COMMENT ON COLUMN v5ai_conversation_summary.conversation_id IS '所属会话（v5ai_conversation.id）；一会话一行';
COMMENT ON COLUMN v5ai_conversation_summary.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_conversation_summary.updated_at IS '更新时间';

-- ---- v5ai_knowledge_base ----
COMMENT ON TABLE v5ai_knowledge_base IS '知识库：status 为 ACTIVE/DISABLED';
COMMENT ON COLUMN v5ai_knowledge_base.id IS '主键';
COMMENT ON COLUMN v5ai_knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN v5ai_knowledge_base.description IS '知识库描述';
COMMENT ON COLUMN v5ai_knowledge_base.status IS '启用状态（ACTIVE / DISABLED）';
COMMENT ON COLUMN v5ai_knowledge_base.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_knowledge_base.updated_at IS '更新时间';

-- ---- v5ai_knowledge_chunk ----
COMMENT ON TABLE v5ai_knowledge_chunk IS '知识库分片：检索单元；正文在 content、向量在向量库（vector_id）、关键词分词在 keyword_tokens';
COMMENT ON COLUMN v5ai_knowledge_chunk.id IS '主键';
COMMENT ON COLUMN v5ai_knowledge_chunk.knowledge_base_id IS '所属知识库（v5ai_knowledge_base.id）';
COMMENT ON COLUMN v5ai_knowledge_chunk.document_id IS '所属文档（v5ai_knowledge_document.id）';
COMMENT ON COLUMN v5ai_knowledge_chunk.chunk_index IS '文档内分片序号（从 0 起）';
COMMENT ON COLUMN v5ai_knowledge_chunk.content IS '分片正文';
COMMENT ON COLUMN v5ai_knowledge_chunk.metadata IS '分片元数据 JSON：{"documentId","chunkIndex","title"}；由索引 Worker 写入，缺省 {}';
COMMENT ON COLUMN v5ai_knowledge_chunk.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_knowledge_chunk.keyword_tokens IS '关键词分词（jieba INDEX 模式，保留重复词；PG text[]）；仅服务关键词路 BM25：词频 tf 由数组内出现次数现算、文档长度 dl = 数组长度、df/avgdl 检索时按库集合现算；写入由 Worker 重建与手工切片增改维护';

-- ---- v5ai_knowledge_document ----
COMMENT ON TABLE v5ai_knowledge_document IS '知识库文档：content 存原始内容（BYTEA）、parsed_text 为解析后纯文本；status 为索引状态（0 待处理 / 1 解析中 / 2 处理中 / 3 完成 / 4 失败）';
COMMENT ON COLUMN v5ai_knowledge_document.id IS '主键';
COMMENT ON COLUMN v5ai_knowledge_document.knowledge_base_id IS '所属知识库（v5ai_knowledge_base.id）';
COMMENT ON COLUMN v5ai_knowledge_document.title IS '文档标题';
COMMENT ON COLUMN v5ai_knowledge_document.file_type IS '文件类型（TXT / MARKDOWN / PDF / DOCX / URL）';
COMMENT ON COLUMN v5ai_knowledge_document.content IS '原始内容（BYTEA）';
COMMENT ON COLUMN v5ai_knowledge_document.parsed_text IS '解析后的纯文本';
COMMENT ON COLUMN v5ai_knowledge_document.error_message IS '失败时的错误信息';
COMMENT ON COLUMN v5ai_knowledge_document.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_knowledge_document.updated_at IS '更新时间';

-- ---- v5ai_knowledge_task ----
COMMENT ON TABLE v5ai_knowledge_task IS '知识库索引任务：由 Worker 异步消费；status 为 PENDING/PROCESSING/COMPLETED/FAILED，带重试计数';
COMMENT ON COLUMN v5ai_knowledge_task.id IS '主键';
COMMENT ON COLUMN v5ai_knowledge_task.knowledge_base_id IS '所属知识库（v5ai_knowledge_base.id）';
COMMENT ON COLUMN v5ai_knowledge_task.document_id IS '关联文档（v5ai_knowledge_document.id）';
COMMENT ON COLUMN v5ai_knowledge_task.task_type IS '任务类型（如 PARSE_AND_INDEX）';
COMMENT ON COLUMN v5ai_knowledge_task.status IS '任务状态（PENDING / PROCESSING / COMPLETED / FAILED）';
COMMENT ON COLUMN v5ai_knowledge_task.attempt_count IS '已尝试次数';
COMMENT ON COLUMN v5ai_knowledge_task.max_attempts IS '最大尝试次数';
COMMENT ON COLUMN v5ai_knowledge_task.error_message IS '失败时的错误信息';
COMMENT ON COLUMN v5ai_knowledge_task.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_knowledge_task.updated_at IS '更新时间';

-- ---- v5ai_mcp_server ----
COMMENT ON TABLE v5ai_mcp_server IS 'MCP Server 注册：Streamable HTTP / SSE / Stdio；headers/env 为密文列';
COMMENT ON COLUMN v5ai_mcp_server.id IS '主键';
COMMENT ON COLUMN v5ai_mcp_server.name IS '平台内唯一标识名（也是 MCP 客户端名）';
COMMENT ON COLUMN v5ai_mcp_server.transport_type IS '传输类型（STREAMABLE_HTTP / SSE / STDIO）';
COMMENT ON COLUMN v5ai_mcp_server.endpoint IS 'HTTP 传输为 URL；Stdio 传输为可执行命令';
COMMENT ON COLUMN v5ai_mcp_server.args_json IS 'Stdio 命令参数（JSON 文本）';
COMMENT ON COLUMN v5ai_mcp_server.headers_ciphertext IS 'HTTP 请求头密文（明文 JSON 加密后落库）';
COMMENT ON COLUMN v5ai_mcp_server.env_ciphertext IS 'Stdio 环境变量密文（明文 JSON 加密后落库）';
COMMENT ON COLUMN v5ai_mcp_server.timeout_seconds IS '请求超时秒数';
COMMENT ON COLUMN v5ai_mcp_server.status IS '启用状态（ACTIVE / DISABLED）';
COMMENT ON COLUMN v5ai_mcp_server.last_test_status IS '最近一次连接测试结果（ok/failed，可为 null）';
COMMENT ON COLUMN v5ai_mcp_server.last_test_message IS '最近一次连接测试消息（可为 null）';
COMMENT ON COLUMN v5ai_mcp_server.last_tested_at IS '最近一次连接测试时间（可为 null）';
COMMENT ON COLUMN v5ai_mcp_server.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_mcp_server.updated_at IS '更新时间';

-- ---- v5ai_mcp_tool ----
COMMENT ON TABLE v5ai_mcp_tool IS 'MCP Tool 缓存：来自 Tool 发现结果；permission 为平台权限决策（ALLOW / APPROVE / DENY）';
COMMENT ON COLUMN v5ai_mcp_tool.id IS '主键';
COMMENT ON COLUMN v5ai_mcp_tool.server_id IS '所属 MCP Server';
COMMENT ON COLUMN v5ai_mcp_tool.tool_name IS '工具名（MCP 协议内唯一，与 serverId 组成唯一键）';
COMMENT ON COLUMN v5ai_mcp_tool.description IS '工具描述';
COMMENT ON COLUMN v5ai_mcp_tool.input_schema_json IS '输入 JSON Schema（JSON 文本）';
COMMENT ON COLUMN v5ai_mcp_tool.read_only IS '服务器声明的只读提示';
COMMENT ON COLUMN v5ai_mcp_tool.permission IS '平台权限决策（ALLOW / APPROVE / DENY）';
COMMENT ON COLUMN v5ai_mcp_tool.last_discovered_at IS '最近一次发现时间';
COMMENT ON COLUMN v5ai_mcp_tool.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_mcp_tool.updated_at IS '更新时间';

-- ---- v5ai_mcp_tool_call ----
COMMENT ON TABLE v5ai_mcp_tool_call IS 'MCP Tool 调用审计：每次工具调用落库，用于追溯与合规';
COMMENT ON COLUMN v5ai_mcp_tool_call.id IS '主键';
COMMENT ON COLUMN v5ai_mcp_tool_call.run_id IS '所属运行 ID';
COMMENT ON COLUMN v5ai_mcp_tool_call.server_id IS '所属 MCP Server';
COMMENT ON COLUMN v5ai_mcp_tool_call.server_name IS 'MCP Server 名称（冗余存储，便于审计查询）';
COMMENT ON COLUMN v5ai_mcp_tool_call.tool_name IS '工具名';
COMMENT ON COLUMN v5ai_mcp_tool_call.arguments_summary IS '参数摘要（截断，不落完整敏感参数）';
COMMENT ON COLUMN v5ai_mcp_tool_call.decision IS '平台权限决策（ALLOW / APPROVE / DENY）';
COMMENT ON COLUMN v5ai_mcp_tool_call.status IS '调用结果状态';
COMMENT ON COLUMN v5ai_mcp_tool_call.duration_ms IS '调用耗时（毫秒）';
COMMENT ON COLUMN v5ai_mcp_tool_call.message IS '错误信息（失败时）';
COMMENT ON COLUMN v5ai_mcp_tool_call.created_at IS '记录时间';

-- ---- v5ai_message ----
COMMENT ON TABLE v5ai_message IS '会话消息：role 为 USER/ASSISTANT；superseded_at 非空表示已被「重新生成」作废，不参与展示与记忆回放';
COMMENT ON COLUMN v5ai_message.id IS '主键';
COMMENT ON COLUMN v5ai_message.conversation_id IS '所属会话（v5ai_conversation.id）';
COMMENT ON COLUMN v5ai_message.agent_key IS 'Agent 对外运行标识（跨模块引用，不建外键）';
COMMENT ON COLUMN v5ai_message.role IS '消息角色（USER / ASSISTANT）';
COMMENT ON COLUMN v5ai_message.content IS '消息正文';
COMMENT ON COLUMN v5ai_message.created_at IS '创建时间';

-- ---- v5ai_message_attachment ----
COMMENT ON COLUMN v5ai_message_attachment.id IS '主键';
COMMENT ON COLUMN v5ai_message_attachment.message_id IS '所属消息（v5ai_message.id）';
COMMENT ON COLUMN v5ai_message_attachment.resource_id IS '资源标识（plm_resource.id，biz_type=ATTACHMENT）';
COMMENT ON COLUMN v5ai_message_attachment.created_at IS '创建时间';

-- ---- v5ai_model ----
COMMENT ON TABLE v5ai_model IS '模型配置：provider_id/model_key/model_type 为唯一组合；凭据加密存储，config 为扩展参数 JSON';
COMMENT ON COLUMN v5ai_model.id IS '主键';
COMMENT ON COLUMN v5ai_model.provider_id IS '提供商 ID';
COMMENT ON COLUMN v5ai_model.model_key IS '模型密钥';
COMMENT ON COLUMN v5ai_model.credentials_ciphertext IS '凭据加密存储';
COMMENT ON COLUMN v5ai_model.enabled IS '是否启用';
COMMENT ON COLUMN v5ai_model.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_model.updated_at IS '更新时间';

-- ---- v5ai_model_provider ----
COMMENT ON TABLE v5ai_model_provider IS '模型供应商：provider_key 全局唯一，可配置描述与图标';
COMMENT ON COLUMN v5ai_model_provider.id IS '主键';
COMMENT ON COLUMN v5ai_model_provider.provider_key IS '供应商对外标识（全局唯一）';
COMMENT ON COLUMN v5ai_model_provider.name IS '供应商名称';
COMMENT ON COLUMN v5ai_model_provider.enabled IS '是否启用';
COMMENT ON COLUMN v5ai_model_provider.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_model_provider.updated_at IS '更新时间';

-- ---- v5ai_model_usage ----
COMMENT ON TABLE v5ai_model_usage IS '模型用量明细账（可观测性统计）：每次模型调用一行；服务端未回报用量时按平台规则估算';
COMMENT ON COLUMN v5ai_model_usage.id IS '主键';
COMMENT ON COLUMN v5ai_model_usage.run_id IS '关联运行（v5ai_run.id；非运行期调用为 NULL）';
COMMENT ON COLUMN v5ai_model_usage.agent_key IS 'Agent 对外运行标识（跨模块引用，不建外键）';
COMMENT ON COLUMN v5ai_model_usage.model_key IS '被调用模型标识';
COMMENT ON COLUMN v5ai_model_usage.prompt_tokens IS '输入 token（真实用量优先，服务端未回报时按平台规则估算）';
COMMENT ON COLUMN v5ai_model_usage.completion_tokens IS '输出 token（口径同 prompt_tokens）';
COMMENT ON COLUMN v5ai_model_usage.total_tokens IS '总 token（输入 + 输出）';
COMMENT ON COLUMN v5ai_model_usage.duration_ms IS '本次调用耗时（毫秒）';
COMMENT ON COLUMN v5ai_model_usage.status IS '调用结果状态（默认 SUCCESS）';
COMMENT ON COLUMN v5ai_model_usage.created_at IS '创建时间';

-- ---- v5ai_run ----
COMMENT ON TABLE v5ai_run IS '一次 Agent 运行：id 为 UUID 字符串，status 记录运行终态';
COMMENT ON COLUMN v5ai_run.id IS '运行 ID（UUID 字符串）';
COMMENT ON COLUMN v5ai_run.conversation_id IS '所属会话（v5ai_conversation.id）';
COMMENT ON COLUMN v5ai_run.agent_key IS 'Agent 对外运行标识（跨模块引用，不建外键）';
COMMENT ON COLUMN v5ai_run.status IS '运行状态';
COMMENT ON COLUMN v5ai_run.error_message IS '错误信息';
COMMENT ON COLUMN v5ai_run.started_at IS '开始时间';
COMMENT ON COLUMN v5ai_run.completed_at IS '完成时间';

-- ---- v5ai_run_event ----
COMMENT ON TABLE v5ai_run_event IS '运行事件流水：SSE 事件的落库副本（不含思考过程，也不含内部用量事件 MODEL_USAGE）';
COMMENT ON COLUMN v5ai_run_event.id IS '主键';
COMMENT ON COLUMN v5ai_run_event.run_id IS '所属运行（v5ai_run.id）';
COMMENT ON COLUMN v5ai_run_event.event_type IS '事件类型（RuntimeEventType 枚举名：RUN_STARTED / MODEL_CALL / TEXT_DELTA / TOOL_CALL / TOOL_RESULT / RETRIEVAL / MESSAGE_COMPLETED / RUN_COMPLETED / RUN_FAILED / PERMISSION_REQUIRED）';
COMMENT ON COLUMN v5ai_run_event.payload IS '事件负载（JSON 文本；RUN_STARTED 为提问消息 id + 上下文窗口统计，RUN_COMPLETED 为本次用量汇总）';
COMMENT ON COLUMN v5ai_run_event.created_at IS '创建时间';

-- ---- v5ai_skill ----
COMMENT ON TABLE v5ai_skill IS 'Skill：技能名全局唯一（取自 SKILL.md frontmatter），status 为 ACTIVE/DISABLED';
COMMENT ON COLUMN v5ai_skill.id IS '主键';
COMMENT ON COLUMN v5ai_skill.name IS '技能名（来自 SKILL.md frontmatter，全局唯一）';
COMMENT ON COLUMN v5ai_skill.description IS '技能描述';
COMMENT ON COLUMN v5ai_skill.status IS '启用状态（ACTIVE / DISABLED）';
COMMENT ON COLUMN v5ai_skill.current_version_id IS '当前生效（注入运行时）的已发布版本 ID';
COMMENT ON COLUMN v5ai_skill.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_skill.updated_at IS '更新时间';

-- ---- v5ai_skill_file ----
COMMENT ON TABLE v5ai_skill_file IS 'Skill 包文件：发布版本的原始文件（SKILL.md、prompts/、resources/ 等），运行时据此注入 Workspace';
COMMENT ON COLUMN v5ai_skill_file.id IS '主键';
COMMENT ON COLUMN v5ai_skill_file.skill_id IS '所属 Skill';
COMMENT ON COLUMN v5ai_skill_file.version_id IS '所属版本';
COMMENT ON COLUMN v5ai_skill_file.file_path IS '包内相对路径（如 "SKILL.md"、"prompts/guide.md"）';
COMMENT ON COLUMN v5ai_skill_file.content IS '文本内容（UTF-8）';
COMMENT ON COLUMN v5ai_skill_file.created_at IS '创建时间';

-- ---- v5ai_skill_version ----
COMMENT ON TABLE v5ai_skill_version IS 'Skill 版本：DRAFT/PUBLISHED/OFFLINE；current_version_id 指向运行时注入的版本';
COMMENT ON COLUMN v5ai_skill_version.id IS '主键';
COMMENT ON COLUMN v5ai_skill_version.skill_id IS '所属 Skill';
COMMENT ON COLUMN v5ai_skill_version.version IS '版本号（每个 Skill 内自增）';
COMMENT ON COLUMN v5ai_skill_version.status IS '状态（DRAFT / PUBLISHED / OFFLINE）';
COMMENT ON COLUMN v5ai_skill_version.description IS '版本描述';
COMMENT ON COLUMN v5ai_skill_version.published_at IS '发布时间（未发布为 null）';
COMMENT ON COLUMN v5ai_skill_version.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_skill_version.updated_at IS '更新时间';

-- ---- v5ai_workflow ----
COMMENT ON TABLE v5ai_workflow IS 'Workflow：草稿与已发布定义分别以 JSON 文本存储';
COMMENT ON COLUMN v5ai_workflow.id IS '主键';
COMMENT ON COLUMN v5ai_workflow.workflow_key IS '对外标识（全局唯一）';
COMMENT ON COLUMN v5ai_workflow.name IS '名称';
COMMENT ON COLUMN v5ai_workflow.description IS '描述';
COMMENT ON COLUMN v5ai_workflow.status IS '状态（DRAFT / PUBLISHED / DISABLED）';
COMMENT ON COLUMN v5ai_workflow.draft_definition IS '草稿定义 JSON（{ nodes, edges }）';
COMMENT ON COLUMN v5ai_workflow.published_definition IS '已发布定义 JSON（{ nodes, edges }）';
COMMENT ON COLUMN v5ai_workflow.published_version IS '已发布版本号';
COMMENT ON COLUMN v5ai_workflow.published_at IS '发布时间';
COMMENT ON COLUMN v5ai_workflow.created_at IS '创建时间';
COMMENT ON COLUMN v5ai_workflow.updated_at IS '更新时间';

-- ---- v5ai_workflow_node_run ----
COMMENT ON TABLE v5ai_workflow_node_run IS 'Workflow 节点运行：每次节点执行的输入输出与耗时，无 updated_at 列';
COMMENT ON COLUMN v5ai_workflow_node_run.id IS '主键';
COMMENT ON COLUMN v5ai_workflow_node_run.run_id IS '所属 Workflow 运行（v5ai_workflow_run.run_id）';
COMMENT ON COLUMN v5ai_workflow_node_run.node_id IS '流程定义中的节点 ID（definition JSON 内的节点 key，非自增列）';
COMMENT ON COLUMN v5ai_workflow_node_run.node_type IS '节点类型（START / AGENT / CONDITION / END）';
COMMENT ON COLUMN v5ai_workflow_node_run.status IS '状态（PENDING / RUNNING / SUCCEEDED / FAILED / SKIPPED）';
COMMENT ON COLUMN v5ai_workflow_node_run.inputs IS '节点输入 JSON';
COMMENT ON COLUMN v5ai_workflow_node_run.outputs IS '节点输出 JSON';
COMMENT ON COLUMN v5ai_workflow_node_run.error IS '失败信息（成功为 NULL）';
COMMENT ON COLUMN v5ai_workflow_node_run.started_at IS '节点开始执行时间';
COMMENT ON COLUMN v5ai_workflow_node_run.finished_at IS '节点结束时间（未结束为 NULL）';
COMMENT ON COLUMN v5ai_workflow_node_run.created_at IS '创建时间';

-- ---- v5ai_workflow_run ----
COMMENT ON TABLE v5ai_workflow_run IS 'Workflow 运行：run_id 为 VARCHAR 主键，锁定运行时的已发布版本号';
COMMENT ON COLUMN v5ai_workflow_run.run_id IS '运行 ID（VARCHAR 主键，UUID 字符串）';
COMMENT ON COLUMN v5ai_workflow_run.workflow_key IS '被运行的 Workflow 对外标识（v5ai_workflow.workflow_key）';
COMMENT ON COLUMN v5ai_workflow_run.workflow_version IS '运行时锁定的已发布版本号（v5ai_workflow.published_version）';
COMMENT ON COLUMN v5ai_workflow_run.status IS '状态（RUNNING / SUCCEEDED / FAILED）';
COMMENT ON COLUMN v5ai_workflow_run.inputs IS '运行输入 JSON';
COMMENT ON COLUMN v5ai_workflow_run.outputs IS '运行输出 JSON';
COMMENT ON COLUMN v5ai_workflow_run.error IS '失败信息（成功为 NULL）';
COMMENT ON COLUMN v5ai_workflow_run.started_at IS '运行开始时间';
COMMENT ON COLUMN v5ai_workflow_run.finished_at IS '运行结束时间（未结束为 NULL）';
COMMENT ON COLUMN v5ai_workflow_run.created_at IS '创建时间';
