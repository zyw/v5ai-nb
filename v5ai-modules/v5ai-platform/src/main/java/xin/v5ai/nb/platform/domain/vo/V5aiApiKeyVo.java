package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.V5aiApiKey;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = V5aiApiKey.class)
public class V5aiApiKeyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String agentKey;

//    private String secretHash;
    private Boolean enabled;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
