package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.time.LocalDate;

@TableName("v5ai_app_usage")
public class AppUsage {
    @TableId(value = "agent_key")
    private String agentKey;
    @TableField("usage_date")
    private LocalDate usageDate;
    @TableField("model_calls")
    private Integer modelCalls;
    private Long tokens;
    private Instant updatedAt;

    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDate usageDate) { this.usageDate = usageDate; }
    public Integer getModelCalls() { return modelCalls; }
    public void setModelCalls(Integer modelCalls) { this.modelCalls = modelCalls; }
    public Long getTokens() { return tokens; }
    public void setTokens(Long tokens) { this.tokens = tokens; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
