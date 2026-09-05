package xin.v5ai.nb.rag.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author ZYW
 * @since 2026-09-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeChunkDO extends KnowledgeChunk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分数（余弦相似度 0~1，由 SQL 计算列映射；关键词命中恒为 1.0）
     */
    private Double score;
}
