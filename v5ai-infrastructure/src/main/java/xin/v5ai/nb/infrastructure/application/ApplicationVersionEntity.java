package xin.v5ai.nb.infrastructure.application;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_application_version")
public class ApplicationVersionEntity {
    @TableField("app_key")
    private String appKey;
    private Long version;
    @TableField("snapshot_json")
    private String snapshotJson;
    private String description;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
