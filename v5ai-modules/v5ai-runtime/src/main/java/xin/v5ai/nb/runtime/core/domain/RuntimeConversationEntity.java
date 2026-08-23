package xin.v5ai.nb.runtime.core.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@TableName("v5ai_conversation")
public class RuntimeConversationEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * PK column is {@code id}; @TableId must carry the column name because a
     * {@code @TableField} on the same field is ignored by MyBatis-Plus and the
     * field name would be derived as {@code conversation_id} (which does not exist).
     */
    @TableId(value = "id")
    private String conversationId;
    @TableField("agent_key")
    private String agentKey;
}
