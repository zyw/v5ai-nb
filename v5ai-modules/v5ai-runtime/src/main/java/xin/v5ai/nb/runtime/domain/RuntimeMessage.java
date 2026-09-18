package xin.v5ai.nb.runtime.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("v5ai_message")
public class RuntimeMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
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
     * 作废时间；非空表示该消息已被「重新生成」替换（不参与展示与记忆回放，数据保留）
     */
    private OffsetDateTime supersededAt;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
