package xin.v5ai.nb.runtime.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = RuntimeMessage.class)
public class RuntimeMessageVo implements Serializable {

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
     * 该回答的输入 token 估算（仅助手消息有值）
     */
    private Integer promptTokens;
    /**
     * 该回答的输出 token 估算（仅助手消息有值）
     */
    private Integer completionTokens;
    /**
     * 该次运行的服务端耗时（毫秒，仅助手消息有值）
     */
    private Integer durationMs;
    /**
     * 作废时间；非空表示该消息已被「重新生成」替换
     */
    private OffsetDateTime supersededAt;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
