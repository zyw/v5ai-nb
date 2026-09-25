ALTER TABLE `v5ai_workflow` ADD COLUMN `draft_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '草稿修订号';

CREATE TABLE `v5ai_workflow_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_key` VARCHAR(100) NOT NULL COMMENT '工作流 Key',
    `version` BIGINT NOT NULL COMMENT '单工作流内递增版本',
    `definition` LONGTEXT NOT NULL COMMENT '不可变工作流定义 JSON 快照',
    `schema_version` INT NOT NULL DEFAULT 1 COMMENT '定义格式版本',
    `change_summary` TEXT NULL COMMENT '发布说明',
    `published_by` BIGINT NULL COMMENT '发布人',
    `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_workflow_version_key_version` (`workflow_key`, `version`),
    KEY `idx_v5ai_workflow_version_key` (`workflow_key`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='工作流不可变发布版本快照';

INSERT INTO `v5ai_workflow_version` (`workflow_key`, `version`, `definition`, `schema_version`, `published_at`, `created_at`)
SELECT `workflow_key`, `published_version`, `published_definition`, 1,
       COALESCE(`published_at`, CURRENT_TIMESTAMP(3)), CURRENT_TIMESTAMP(3)
FROM `v5ai_workflow`
WHERE `published_definition` IS NOT NULL AND `published_version` IS NOT NULL
ON DUPLICATE KEY UPDATE `workflow_key` = VALUES(`workflow_key`);

ALTER TABLE `v5ai_workflow_run`
    ADD COLUMN `source` VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED' COMMENT '运行来源（PUBLISHED / DRAFT_TEST）',
    ADD COLUMN `draft_revision` BIGINT NULL COMMENT '草稿测试所测修订号',
    ADD COLUMN `definition_snapshot` LONGTEXT NULL COMMENT '草稿测试运行定义快照';

ALTER TABLE `v5ai_workflow_node_run`
    ADD COLUMN `attempt` INT NOT NULL DEFAULT 1 COMMENT '节点执行尝试次数',
    ADD COLUMN `duration_ms` BIGINT NULL COMMENT '节点耗时毫秒',
    ADD COLUMN `error_code` VARCHAR(100) NULL COMMENT '稳定错误码',
    ADD COLUMN `executor_type` VARCHAR(100) NULL COMMENT '节点执行器类型',
    ADD COLUMN `agent_run_id` VARCHAR(36) NULL COMMENT '关联 Agent 运行 ID';
