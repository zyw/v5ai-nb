package xin.v5ai.nb.model.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.model.domain.V5aiModel;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Data
@AutoMapper(target = V5aiModel.class)
public class V5aiModelVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    private String modelKey;

    private String modelType;

    private String endpoint;

    private String credentialsCiphertext;

    private Boolean enabled;

    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
