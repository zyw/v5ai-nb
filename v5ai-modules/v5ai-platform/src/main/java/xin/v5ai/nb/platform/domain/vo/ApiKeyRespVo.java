package xin.v5ai.nb.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class ApiKeyRespVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String agentKey;

    private String apiKey;
}
