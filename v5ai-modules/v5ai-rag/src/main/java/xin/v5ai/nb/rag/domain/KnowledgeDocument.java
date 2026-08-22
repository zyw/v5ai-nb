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
 * 知识库文档实体（v5ai_knowledge_document）：原始内容以 BYTEA 存储在 content 列，
 * parsed_text 保存解析后的纯文本，status 为 PENDING/PROCESSING/COMPLETED/FAILED。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_knowledge_document")
public class KnowledgeDocument extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属知识库 ID
     */
    private Long knowledgeBaseId;

    /**
     * 文档标题
     */
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
     * 原始内容（BYTEA）
     */
    private byte[] content;

    /**
     * 解析后的纯文本
     */
    private String parsedText;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;
}
