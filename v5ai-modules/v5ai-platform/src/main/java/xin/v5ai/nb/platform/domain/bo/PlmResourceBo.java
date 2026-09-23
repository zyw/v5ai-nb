package xin.v5ai.nb.platform.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.platform.domain.PlmResource;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源存储请求体：列表查询条件 + 元数据编辑。
 *
 * @author ZYW
 * @since 2026-09-02
 */
@Data
@AutoMapper(target = PlmResource.class, reverseConvertGenerate = false)
public class PlmResourceBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，编辑时必填
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 原始文件名（模糊搜索）
     */
    private String originalName;

    /**
     * 业务类型: AVATAR/ATTACHMENT/DOCUMENT/GENERAL
     */
    private String bizType;

    /**
     * 关联业务ID
     */
    private Long bizId;

    /**
     * 存储类型: LOCAL/MINIO
     */
    private String storageType;

    /**
     * 请求参数（创建时间范围 beginTime/endTime 等）
     */
    private Map<String, Object> params = new HashMap<>();
}
