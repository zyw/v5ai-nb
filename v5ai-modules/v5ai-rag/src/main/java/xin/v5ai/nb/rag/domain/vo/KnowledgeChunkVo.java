package xin.v5ai.nb.rag.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.ToString;
import xin.v5ai.nb.rag.domain.KnowledgeChunk;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author ZYW
 * @since 2026-09-03
 */
@Data
@ToString
@AutoMapper(target = KnowledgeChunk.class)
public class KnowledgeChunkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long knowledgeBaseId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private Object embedding;

    private Object metadata;

    /**
     * 段落索引
     */
    private Integer paragraphIndex;

    /**
     * 分片token数量
     */
    private Integer tokenCount;

    /**
     * 向量id
     */
    private String vectorId;

    /**
     * chunk内容SHA-256，用于向量去重
     */
    private String contentHash;

    /**
     * chunk来源类型: TEXT=文本 IMAGE=图片
     */
    private String sourceType;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
