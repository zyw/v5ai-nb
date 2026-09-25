package xin.v5ai.nb.platform.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class ModelUsageListBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String agentKey;

    private OffsetDateTime from;

    private OffsetDateTime to;
}
