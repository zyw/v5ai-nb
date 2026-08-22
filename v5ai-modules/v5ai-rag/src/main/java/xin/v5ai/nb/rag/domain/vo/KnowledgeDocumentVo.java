package xin.v5ai.nb.rag.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 知识库文档视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = KnowledgeDocument.class)
public class KnowledgeDocumentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long knowledgeBaseId;

    private String title;

    /**
     * 文件类型（TXT / MARKDOWN / PDF / DOCX / URL）
     */
    private String fileType;

    /**
     * 来源（原始文件名或 URL）
     */
    private String source;

    /**
     * 索引状态（PENDING / PROCESSING / COMPLETED / FAILED）
     */
    private String status;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
