package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("v5ai_app_usage")
public class AppUsageEntity {
    @TableId(value = "app_key")
    private String appKey;
    @TableField("usage_date")
    private java.time.LocalDate usageDate;
    @TableField("model_calls")
    private Integer modelCalls;
    private Long tokens;
    private Instant updatedAt;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public java.time.LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(java.time.LocalDate usageDate) { this.usageDate = usageDate; }
    public Integer getModelCalls() { return modelCalls; }
    public void setModelCalls(Integer modelCalls) { this.modelCalls = modelCalls; }
    public Long getTokens() { return tokens; }
    public void setTokens(Long tokens) { this.tokens = tokens; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
