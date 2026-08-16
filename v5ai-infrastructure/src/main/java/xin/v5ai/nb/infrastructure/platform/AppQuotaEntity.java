package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("v5ai_app_quota")
public class AppQuotaEntity {
    @TableId
    private String appKey;
    private Integer dailyModelCalls;
    private Long dailyTokens;
    private Integer ratePerMinute;
    private Instant updatedAt;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public Integer getDailyModelCalls() { return dailyModelCalls; }
    public void setDailyModelCalls(Integer dailyModelCalls) { this.dailyModelCalls = dailyModelCalls; }
    public Long getDailyTokens() { return dailyTokens; }
    public void setDailyTokens(Long dailyTokens) { this.dailyTokens = dailyTokens; }
    public Integer getRatePerMinute() { return ratePerMinute; }
    public void setRatePerMinute(Integer ratePerMinute) { this.ratePerMinute = ratePerMinute; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
