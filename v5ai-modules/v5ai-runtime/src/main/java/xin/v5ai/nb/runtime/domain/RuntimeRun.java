package xin.v5ai.nb.runtime.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("v5ai_run")
public class RuntimeRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private String id;
    /**
     * 关联的会话ID
     */
    private String conversationId;
    /**
     * agent key
     */
    private String agentKey;
    /**
     * 运行状态
     */
    private String status;
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 开始时间
     */
    private OffsetDateTime startedAt;
    /**
     * 完成时间
     */
    private OffsetDateTime completedAt;
}
