package xin.v5ai.nb.rag.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

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
     * 来源类型（UPLOAD=上传 / URL=网络）
     */
    private String sourceType;

    /**
     * 索引状态（0-待处理 1-解析中 2-处理中 3-处理完成 4-处理失败）
     */
    private Integer status;

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

    private String parseEngine;

    private String parseDiagnostics;

    /**
     * 文件内容 SHA-256 哈希，用于去重
     */
    private String contentHash;

    /**
     * 关联资源库 plm_resource.id
     */
    private Long resourceId;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
