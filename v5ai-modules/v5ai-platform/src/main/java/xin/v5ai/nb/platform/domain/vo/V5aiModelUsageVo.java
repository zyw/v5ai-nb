package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.V5aiModelUsage;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = V5aiModelUsage.class)
public class V5aiModelUsageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private Long id;
    /** 运行 ID（关联 run 表） */
    private String runId;
    /** Agent 标识（对外运行标识） */
    private String agentKey;
    /** 平台模型配置 ID；历史记录可能为空 */
    private Long modelId;
    /** 模型标识（供应商下模型配置的标识） */
    private String modelKey;
    /** 提示词 token 数 */
    private Long promptTokens;
    /** 补全 token 数 */
    private Long completionTokens;
    /** token 总数 */
    private Long totalTokens;
    /** 调用耗时（毫秒） */
    private Long durationMs;
    /** 调用状态（success/failed 等） */
    private String status;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
}
