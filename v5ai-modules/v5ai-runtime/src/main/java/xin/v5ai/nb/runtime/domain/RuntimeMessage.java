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
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
