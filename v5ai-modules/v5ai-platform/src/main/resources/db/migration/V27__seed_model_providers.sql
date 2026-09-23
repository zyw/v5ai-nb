-- 初始化常用模型供应商（含 LOGO 图标）。
-- 图标为前端静态资源 /icons/providers/<provider_key>.svg，由 v5ai-ui/public/icons/providers/ 提供
-- （来源 LobeHub icons-static-svg，MIT 协议），供 <img> 直接加载，不依赖外网热链。
-- 运行时适配器：anthropic / gemini / dashscope 走原生适配器，其余走 openai-compatible
-- （AgentScopeModelFactory 按 providerKey 分发）。幂等：已存在同 key 的供应商不覆盖、不重复插入。
INSERT INTO v5ai_model_provider (provider_key, name, enabled, description, icon_url)
VALUES
    ('openai',      'OpenAI',             TRUE, '提供 GPT / o 系列模型（OpenAI 兼容接口）',   '/icons/providers/openai.svg'),
    ('anthropic',   'Anthropic',          TRUE, '提供 Claude 系列模型',                       '/icons/providers/anthropic.svg'),
    ('gemini',      'Google Gemini',      TRUE, '谷歌多模态 Gemini 系列模型',                 '/icons/providers/gemini.svg'),
    ('dashscope',   '阿里云百炼·通义千问',  TRUE, '阿里云大模型服务平台，提供通义千问系列模型',   '/icons/providers/dashscope.svg'),
    ('deepseek',    'DeepSeek',           TRUE, '深度求索，提供 DeepSeek V3 / R1 系列模型',    '/icons/providers/deepseek.svg'),
    ('moonshot',    '月之暗面 Kimi',       TRUE, '提供 Kimi 长文本系列模型',                   '/icons/providers/moonshot.svg'),
    ('zhipu',       '智谱 AI',            TRUE, '提供 GLM 系列模型',                          '/icons/providers/zhipu.svg'),
    ('minimax',     'MiniMax',            TRUE, '提供 MiniMax 系列模型',                      '/icons/providers/minimax.svg'),
    ('tencent',     '腾讯混元',            TRUE, '腾讯混元 Hunyuan 系列模型',                  '/icons/providers/tencent.svg'),
    ('volcengine',  '火山方舟·豆包',        TRUE, '字节跳动大模型服务平台，提供豆包系列模型',      '/icons/providers/volcengine.svg'),
    ('siliconflow', '硅基流动',            TRUE, '一站式大模型 API 平台，聚合开源模型',         '/icons/providers/siliconflow.svg'),
    ('ollama',      'Ollama（本地）',       TRUE, '本地运行开源模型的工具（OpenAI 兼容接口）',    '/icons/providers/ollama.svg')
ON CONFLICT (provider_key) DO NOTHING;
