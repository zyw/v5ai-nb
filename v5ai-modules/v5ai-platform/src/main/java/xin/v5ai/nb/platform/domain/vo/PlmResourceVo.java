package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.PlmResource;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源存储视图对象。
 *
 * @author ZYW
 * @since 2026-09-02
 */
@Data
@AutoMapper(target = PlmResource.class)
public class PlmResourceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 存储键（相对路径或对象Key）
     */
    private String storageKey;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件大小(bytes)
     */
    private Long fileSize;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 存储类型: LOCAL/MINIO
     */
    private String storageType;

    /**
     * 访问URL
     */
    private String accessUrl;

    /**
     * 业务类型: AVATAR/ATTACHMENT/DOCUMENT/GENERAL
     */
    private String bizType;

    /**
     * 关联业务ID
     */
    private Long bizId;

    /**
     * 创建者ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createDt;

    /**
     * 更新时间
     */
    private LocalDateTime updateDt;
}
