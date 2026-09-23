package xin.v5ai.nb.model.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.model.domain.V5aiModelProvider;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * <p>
 * 模型供应商视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Data
@AutoMapper(target = V5aiModelProvider.class)
public class V5aiModelProviderVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String providerKey;

    private String name;

    private Boolean enabled;

    /**
     * 提供商描述
     */
    private String description;

    /**
     * LOGO 图标 URL
     */
    private String iconUrl;

    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
