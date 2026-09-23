package xin.v5ai.nb.runtime.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.runtime.domain.AgentState;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = AgentState.class)
public class AgentStateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String conversationId;

    private String stateJson;

    private OffsetDateTime updatedAt;
}
