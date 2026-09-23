package xin.v5ai.nb.runtime.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("v5ai_agent_state")
public class AgentState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "conversation_id")
    private String conversationId;

    private String stateJson;

    private OffsetDateTime updatedAt;
}
