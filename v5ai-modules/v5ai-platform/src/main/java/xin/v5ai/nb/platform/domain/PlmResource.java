package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 通用资源存储实体（plm_resource）：上传文件的元数据与存储位置。
 * </p>
 *
 * @author ZYW
 * @since 2026-09-02
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@TableName("plm_resource")
public class PlmResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
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
