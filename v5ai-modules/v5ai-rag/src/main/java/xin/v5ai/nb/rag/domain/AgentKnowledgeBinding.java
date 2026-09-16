package xin.v5ai.nb.rag.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * AgentDTO-知识库绑定实体（v5ai_agent_knowledge）：复合主键 (agent_key, knowledge_base_id)。
 * 表无 updated_at 列，故不继承 BaseEntity。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@TableName("v5ai_agent_knowledge")
public class AgentKnowledgeBinding {

    @TableId(value = "agent_key")
    private String agentKey;

    /**
     * 绑定的知识库 ID
     */
    private Long knowledgeBaseId;
}
