-- ============================================================
-- V40：会话名称自动生成（创建时兜底 + 首轮后模型改写）
--
-- 背景：门户会话列表原本对每个会话执行一次 DISTINCT ON 查 v5ai_message 取首条提问做
--   展示兜底（见 V39 注释里的读取侧清单第 2 条）。这条查询随消息表增长而变贵，
--   而列表是门户首屏最高频的请求。改为在**写入侧**命名：
--     1) 会话创建时（首条提问到达）就写 name = 首条提问的规则化截断（ConversationNaming.fromQuery）；
--     2) 首轮结束后异步调一次小模型把名字改写成短标题（ConversationTitleWriter）。
--   于是列表只读 v5ai_conversation 一张表。
--
-- name_source 是「谁写的这个名字」，模型改写不会盖掉用户输入：
--   AUTO = 系统生成（兜底名或模型标题），可被模型改写；
--   USER = 用户改名，此后任何自动命名都不再覆盖。
-- ============================================================

ALTER TABLE v5ai_conversation ADD COLUMN IF NOT EXISTS name_source VARCHAR(16);

COMMENT ON COLUMN v5ai_conversation.name_source IS '名称来源：AUTO=系统生成（首条提问兜底名/模型标题，可被模型改写），USER=用户改名（永不被覆盖）';

-- 存量已命名的会话：V40 之前不存在自动命名，所有非空 name 都来自用户改名 → 冻结，永不被模型改写
UPDATE v5ai_conversation SET name_source = 'USER' WHERE name IS NOT NULL AND name_source IS NULL;

-- 存量未命名的会话：用首条用户提问回填（规则与 ConversationNaming.fromQuery 对齐：
-- 取首行 → 连续空白压成单空格 → 去首尾空白 → 截到 100 字符），否则老会话在门户里会全变成「新会话」
UPDATE v5ai_conversation c
SET name = t.title,
    name_source = 'AUTO'
FROM (
    SELECT DISTINCT ON (m.conversation_id)
           m.conversation_id,
           left(btrim(regexp_replace(split_part(m.content, E'\n', 1), '\s+', ' ', 'g')), 100) AS title
    FROM v5ai_message m
    WHERE m.role = 'USER'
      AND m.superseded_at IS NULL
      AND m.content IS NOT NULL
    ORDER BY m.conversation_id, m.created_at, m.id
) t
WHERE c.id = t.conversation_id
  AND c.name IS NULL
  AND t.title <> '';

COMMENT ON COLUMN v5ai_conversation.name IS '会话名称：新建会话时由首条提问生成（ConversationNaming），首轮结束后可由模型改写成短标题；用户可改名（name_source=USER 后不再被自动覆盖）';
