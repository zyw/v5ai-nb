package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Agent API Key（MyBatis 实体）。表含 created_at 无 updated_at，不继承 BaseEntity。
 */
@Data
@TableName("v5ai_api_key")
public class V5aiApiKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    @TableField("agent_key")
    private String agentKey;
    @TableField("secret_hash")
    private String secretHash;
    private Boolean enabled;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
