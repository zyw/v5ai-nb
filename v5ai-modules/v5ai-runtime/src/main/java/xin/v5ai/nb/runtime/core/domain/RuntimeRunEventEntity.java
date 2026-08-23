package xin.v5ai.nb.runtime.core.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("v5ai_run_event")
public class RuntimeRunEventEntity implements Serializable {
    @TableField("run_id")
    private String runId;
    @TableField("event_type")
    private String eventType;
    private String payload;
    @TableField("created_at")
    private Instant createdAt;
}
