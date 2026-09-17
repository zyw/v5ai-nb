package xin.v5ai.nb.runtime.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.runtime.domain.RuntimeRunEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Data
@AutoMapper(target = RuntimeRunEvent.class)
public class RuntimeRunEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    /**
     * 运行ID
     */
    private String runId;

    /**
     * 事件类型
     * RUN_STARTED 运行开始
     * RETRIEVAL   检索
     * MODEL_CALL  模型调用
     * TEXT_DELTA  文本增量
     * RUN_COMPLETED 运行完成
     */
    private String eventType;

    /**
     * 事件负载
     */
    private  String payload;
    /**
     * 创建时间
     */
    private Instant createdAt;
}
