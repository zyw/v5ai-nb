package xin.v5ai.nb.rag.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

/**
 * <p>
 * 知识库索引任务实体（v5ai_knowledge_task）：由 Worker 异步消费，
 * status 为 PENDING/PROCESSING/COMPLETED/FAILED，带重试计数。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_knowledge_task")
public class KnowledgeTask extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属知识库 ID
     */
    private Long knowledgeBaseId;

    /**
     * 关联文档 ID
     */
    private Long documentId;

    /**
     * 任务类型（如 PARSE_AND_INDEX）
     */
    private String taskType;

    /**
     * 任务状态（PENDING / PROCESSING / COMPLETED / FAILED）
     */
    private String status;

    /**
     * 已尝试次数
     */
    private Integer attemptCount;

    /**
     * 最大尝试次数
     */
    private Integer maxAttempts;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;
}
