package xin.v5ai.nb.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("v5ai_workflow_version")
public class WorkflowVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workflowKey;
    private Long version;
    private String definition;
    private Integer schemaVersion;
    private String changeSummary;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
}
