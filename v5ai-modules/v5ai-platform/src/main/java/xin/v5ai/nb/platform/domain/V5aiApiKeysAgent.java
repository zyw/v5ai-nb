package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * API Key ↔ Agent 绑定（v5ai_api_keys_agent）：某把 Key 可以访问哪些 Agent。
 *
 * <p>agent_key 为跨模块引用，不建外键；删除 Agent 时由 {@code AgentCleanupMapper} 显式清理。
 * 表只有 created_at 且由数据库默认值填充，故不继承 BaseEntity（避免写入不存在的 updated_at）。</p>
 */
@Data
@TableName("v5ai_api_keys_agent")
public class V5aiApiKeysAgent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * v5ai_api_keys.id
     */
    private Long apiKeyId;

    /**
     * v5ai_agent.agent_key
     */
    private String agentKey;
}