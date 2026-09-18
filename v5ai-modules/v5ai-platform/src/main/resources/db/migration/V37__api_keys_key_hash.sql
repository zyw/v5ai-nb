-- ============================================================
-- V37-1：API Key 明文格式简化 + tracking_id 与明文解耦
--
-- 背景：V35/V36 阶段明文 Key 内嵌 UUID（v5ai_<UUID><32 随机>），tracking_id 直接取自明文。
--   现改为：
--     明文 Key  = v5ai- + 32 位大小写字母与数字（唯一秘密，不含任何跟踪信息；前缀是代码常量，
--                 库表不约束格式，这里只负责为它提供唯一索引定位列）；
--     tracking_id = 独立生成的 UUID，仅用于页面展示与日志/审计对账，与明文无字符关系。
--   两者在库里只是同一行的两个字段，因此运行时无法再靠 tracking_id 定位密钥行，
--   新增 key_hash（明文 SHA-256 十六进制）唯一索引负责定位，secret_hash（BCrypt）继续做二次校验。
--
-- 为什么追加而不是改 V35/V36：二者可能已在环境执行，Flyway 会校验已应用迁移的校验和，
--   改动历史文件会导致 checksum mismatch（启动即失败）。历史迁移只增不改。
--
-- key_hash 无法为历史行回填（库内没有明文），故 key_hash IS NULL 的行一律作废删除——
--   这些 Key 在新明文格式下已不可能通过鉴权；v5ai_api_keys_agent 的绑定经外键级联删除。
-- tracking_id 的列宽（36）与唯一索引由 V36 负责，Flyway 保证 V36 先于本迁移执行。
-- ============================================================

ALTER TABLE v5ai_api_keys ADD COLUMN IF NOT EXISTS key_hash VARCHAR(64);

-- 旧格式 Key 作废（无法回填摘要，且明文格式已变更，使用方需重新签发）
DELETE FROM v5ai_api_keys WHERE key_hash IS NULL;

ALTER TABLE v5ai_api_keys ALTER COLUMN key_hash SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_v5ai_api_keys_key_hash ON v5ai_api_keys (key_hash);

COMMENT ON COLUMN v5ai_api_keys.key_hash IS '明文 Key 的 SHA-256 摘要（小写十六进制），运行时唯一索引定位密钥行';
COMMENT ON COLUMN v5ai_api_keys.tracking_id IS '跟踪 ID：独立 UUID，与明文 Key 无关联，仅供展示与日志/审计对账';

-- ============================================================
-- V37-2：API Key 名称在用户维度唯一
--
-- 背景：此前名称允许重复（同一用户在列表里可能出现两把同名 Key，无法区分）。
--   现约束为「同一用户下名称唯一」（不同用户仍可同名——列表本就按用户隔离）。
--
-- 处理顺序：先给历史重复名加后缀（用主键做后缀，保证互不相同），再建唯一索引，
--   这样本迁移在任何存量数据下都不会因重复而失败；全新库/已清空的库则两步都无副作用。
--
-- 说明：/api/admin/api-keys 的服务端校验（requireNameAvailable）负责给出可读错误，
--   本索引是并发写入下的最终兜底。
-- ============================================================

-- 历史重复名（本约束之前允许）：同 (user_id, name) 组内第 2 条起改成「原名 #<id>」
UPDATE v5ai_api_keys k
SET name = LEFT(k.name, 80) || ' #' || k.id::text
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id, name ORDER BY id) AS rn
    FROM v5ai_api_keys
    ) r
WHERE k.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_v5ai_api_keys_user_name ON v5ai_api_keys (user_id, name);

COMMENT ON COLUMN v5ai_api_keys.name IS 'Key 名称（页面主标识，同一用户内唯一）';