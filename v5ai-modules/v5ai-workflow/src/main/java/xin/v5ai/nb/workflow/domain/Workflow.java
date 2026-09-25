package xin.v5ai.nb.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * <p>
 * Workflow 实体（v5ai_workflow）：草稿/发布定义以 JSON 文本存储。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_workflow")
public class Workflow extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 对外标识（全局唯一）
     */
    private String workflowKey;

    private String name;

    private String description;

    /**
     * 状态（DRAFT / PUBLISHED / DISABLED）
     */
    private String status;

    /**
     * 草稿定义 JSON（{ nodes, edges }），列名 draft_definition
     */
    @TableField("draft_definition")
    private String draftDefinitionJson;

    /**
     * 已发布定义 JSON（{ nodes, edges }），列名 published_definition
     */
    @TableField("published_definition")
    private String publishedDefinitionJson;

    /**
     * 已发布版本号
     */
    private Long publishedVersion;

    /**
     * 发布时间
     */
    private OffsetDateTime publishedAt;

    private Long draftRevision;
}
