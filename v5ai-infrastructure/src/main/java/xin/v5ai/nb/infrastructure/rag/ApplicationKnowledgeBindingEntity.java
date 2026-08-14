package xin.v5ai.nb.infrastructure.rag;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_application_knowledge")
public class ApplicationKnowledgeBindingEntity {
    @TableId(value = "app_key")
    private String appKey;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
}
