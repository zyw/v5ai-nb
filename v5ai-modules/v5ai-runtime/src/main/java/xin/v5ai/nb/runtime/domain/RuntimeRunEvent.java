package xin.v5ai.nb.runtime.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("v5ai_run_event")
public class RuntimeRunEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

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
