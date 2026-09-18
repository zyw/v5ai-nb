-- ============================================================
-- V36：tracking_id 改为 UUID（36 位）
--
-- 背景：V35 已在环境执行，其 tracking_id 为 VARCHAR(32)、取值是明文 Key 第 6–17 位（12 位随机串）。
--   运行时契约随后改为「明文 Key 内嵌一个 UUID 作为定位手柄」——
--   明文形如 v5ai_<UUID 36 位><32 位随机主体>，32 位随机主体仍是唯一秘密（UUID 明文入库只为定位）；
--   解析器按定长 36 截取并校验 UUID 形态，因此 12 位旧值再也无法被解析。
--   本迁移把列宽放宽到 36，并把已不可能通过鉴权的旧行作废删除。
--
-- 为什么是追加而不是改 V35：V35 已应用，Flyway 会校验已应用迁移的文件校验和，
--   改动历史文件会导致 checksum mismatch（应用启动即失败）。历史迁移只增不改。
--
-- 细节：VARCHAR(32) → VARCHAR(36) 属同类定长字符串放宽，PostgreSQL 无需 USING、无需重写表。
--   删除带 UUID 形态守卫，只删「新解析器不可能接受」的行；已存在的合法 UUID 行不受影响，
--   迁移可重复执行。v5ai_api_keys_agent 的绑定经外键 ON DELETE CASCADE 一并清理。
-- ============================================================

ALTER TABLE v5ai_api_keys ALTER COLUMN tracking_id TYPE VARCHAR(36);

COMMENT ON COLUMN v5ai_api_keys.tracking_id IS '跟踪 ID：明文 Key 内嵌的 UUID（36 位），运行时唯一索引定位密钥行，日志/审计可追溯';

-- 旧格式（12 位）的 Key 已无法鉴权（使用方需重新签发），直接作废
DELETE FROM v5ai_api_keys
WHERE tracking_id !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';