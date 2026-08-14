package xin.v5ai.nb.infrastructure.runtime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_conversation")
public class RuntimeConversationEntity {
    /**
     * PK column is {@code id}; @TableId must carry the column name because a
     * {@code @TableField} on the same field is ignored by MyBatis-Plus and the
     * field name would be derived as {@code conversation_id} (which does not exist).
     */
    @TableId(value = "id")
    private String conversationId;
    @TableField("app_key")
    private String appKey;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
}
