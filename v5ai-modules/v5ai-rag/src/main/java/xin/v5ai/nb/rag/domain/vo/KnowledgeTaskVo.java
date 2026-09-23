package xin.v5ai.nb.rag.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.rag.domain.KnowledgeTask;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * <p>
 * 知识库索引任务视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = KnowledgeTask.class)
public class KnowledgeTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long knowledgeBaseId;

    private Long documentId;

    /**
     * 任务类型（如 PARSE_AND_INDEX）
     */
    private String taskType;

    /**
     * 任务状态（PENDING / PROCESSING / COMPLETED / FAILED）
     */
    private String status;

    private Integer attemptCount;

    private Integer maxAttempts;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
