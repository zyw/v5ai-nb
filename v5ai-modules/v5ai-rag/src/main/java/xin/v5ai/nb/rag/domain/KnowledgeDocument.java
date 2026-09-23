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
 * parsed_text 保存解析后的纯文本；source_type 标识来源（UPLOAD/URL），
 * status 为 SMALLINT 索引状态（0-待处理 1-解析中 2-处理中 3-处理完成 4-处理失败）。
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
     * 来源类型（UPLOAD=上传 / URL=网络）
     */
    private String sourceType;

    /**
     * 索引状态（0-待处理 1-解析中 2-处理中 3-处理完成 4-处理失败）
     */
    private Integer status;

    /**
     * 原始内容（BYTEA）
     */
    private byte[] content;

    /**
     * 解析后的纯文本
     */
    private String parsedText;

    /** 实际使用的解析引擎（外部引擎失败时记录 default）。 */
    private String parseEngine;

    /** 外部解析器返回的文档级结构化结果/诊断原文。 */
    private String parseDiagnostics;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;

    /**
     * 存储类型（LOCAL=本地 / MINIO=minio）
     */
    private String storageType;

    /**
     * 存储路径
     */
    private String storagePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 分片数量
     */
    private Integer chunkCount;

    /**
     * 解析耗时（毫秒）
     */
    private Integer parseTime;

    /**
     * 文件内容 SHA-256 哈希，用于去重
     */
    private String contentHash;

    /**
     * 关联资源库 plm_resource.id
     */
    private Long resourceId;
}
