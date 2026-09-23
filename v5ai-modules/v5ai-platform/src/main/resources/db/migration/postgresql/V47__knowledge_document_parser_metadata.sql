-- 背景：保存结构化解析的实际引擎和文档级诊断，支持外部引擎失败回退后的运维定位。
-- 边界：不保存源文件内容、API Key，也不引入图片资源表；图片资源仍由后续多模态迭代处理。
ALTER TABLE v5ai_knowledge_document
    ADD COLUMN IF NOT EXISTS parse_engine VARCHAR(32),
    ADD COLUMN IF NOT EXISTS parse_diagnostics TEXT;

COMMENT ON COLUMN v5ai_knowledge_document.parse_engine IS '实际使用的文档解析引擎，外部服务失败时为 default';
COMMENT ON COLUMN v5ai_knowledge_document.parse_diagnostics IS '文档级解析结构化结果或诊断原文';
