-- ============================================================
-- V24：v5ai_knowledge_base 扩展（RAG 创建/编辑配置）
-- 说明：
--   1) 按「创建 RAG」表单新增 12 列：嵌入模型/向量库实例/维度/
--      重排模型/混合检索/搜索引擎实例/分隔符/RAG增强/页面配置/
--      去重策略/冲突动作/图标；
--   2) NOT NULL 列（embedding_model_id / dimension_of_vector_model）
--      以 DEFAULT 0 兼容存量行，应用层创建/编辑时校验必须 > 0；
--   3) config 列存 RagConfigDO 序列化 JSON（检索/切片/解析参数）。
-- ============================================================

alter table v5ai_knowledge_base
    add column if not exists icon varchar(512),
    add column if not exists embedding_model_id bigint not null default 0,
    add column if not exists vector_store_instance_id bigint,
    add column if not exists dimension_of_vector_model int not null default 0,
    add column if not exists rerank_model_id bigint,
    add column if not exists search_engine_enable boolean default false,
    add column if not exists search_engine_instance_id bigint,
    add column if not exists delimiter varchar(32) default E'\n\n',
    add column if not exists rag_enhancement text,
    add column if not exists config text,
    add column if not exists dedup_strategy smallint not null default 2,
    add column if not exists dedup_action smallint not null default 0;

comment on column v5ai_knowledge_base.icon is '图标';
comment on column v5ai_knowledge_base.embedding_model_id is '向量模型id';
comment on column v5ai_knowledge_base.vector_store_instance_id is '向量存储实例id';
comment on column v5ai_knowledge_base.dimension_of_vector_model is '向量维度';
comment on column v5ai_knowledge_base.rerank_model_id is '重排序模型id';
comment on column v5ai_knowledge_base.search_engine_enable is '搜索引擎启用标志';
comment on column v5ai_knowledge_base.search_engine_instance_id is '搜索引擎实例id';
comment on column v5ai_knowledge_base.delimiter is '文档分割符';
comment on column v5ai_knowledge_base.rag_enhancement is 'RAG增强配置';
comment on column v5ai_knowledge_base.config is 'RAG检索和问答的页面配置参数（RagConfigDO JSON）';
comment on column v5ai_knowledge_base.dedup_strategy is '去重策略: 0=NONE 1=BY_NAME 2=BY_CONTENT 3=BY_NAME_OR_CONTENT';
comment on column v5ai_knowledge_base.dedup_action is '冲突动作: 0=REJECT 1=SKIP 2=OVERWRITE';
