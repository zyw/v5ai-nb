package xin.v5ai.nb.runtime.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.runtime.domain.RuntimeRun;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = RuntimeRun.class)
public class RuntimeRunVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
