package xin.v5ai.nb.rag.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.rag.domain.KnowledgeBase;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 知识库视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = KnowledgeBase.class)
public class KnowledgeBaseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String description;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
