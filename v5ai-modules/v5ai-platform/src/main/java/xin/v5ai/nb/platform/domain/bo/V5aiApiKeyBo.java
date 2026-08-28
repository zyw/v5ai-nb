package xin.v5ai.nb.platform.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class V5aiApiKeyBo implements Serializable {

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
