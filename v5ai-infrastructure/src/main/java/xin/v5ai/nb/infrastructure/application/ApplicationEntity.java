package xin.v5ai.nb.infrastructure.application;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_application")
public class ApplicationEntity {
    @TableId
    private Long id;
    @TableField("app_key")
    private String appKey;
    private String name;
    private String status;
    @TableField("model_id")
    private Long modelId;
    @TableField("published_version")
    private Long publishedVersion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Long getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(Long publishedVersion) { this.publishedVersion = publishedVersion; }
}
