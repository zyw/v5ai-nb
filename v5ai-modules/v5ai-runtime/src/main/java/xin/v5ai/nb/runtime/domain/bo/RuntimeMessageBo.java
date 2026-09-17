package xin.v5ai.nb.runtime.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = RuntimeMessage.class)
public class RuntimeMessageBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    /**
     * 会话ID
     */
    private String conversationId;
    /**
     * agent key
     */
    private String agentKey;
    /**
     * 角色role USER or ASSISTANT
     */
    private String role;
    /**
     * 内容
     */
    private String content;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
