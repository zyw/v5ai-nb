package xin.v5ai.nb.rag.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_knowledge_chunk")
public class KnowledgeChunk extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
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
}
