package xin.v5ai.nb.platform.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * API Key 列表查询条件。
 *
 * <p>列表恒按「当前登录用户」过滤（每把 Key 只属于创建者），故没有 user_id 查询字段。</p>
 */
@Data
public class V5aiApiKeysBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * Key 名称（模糊匹配）
     */
    private String name;

    /**
     * 按可访问 Agent 过滤：命中的 Key 必须绑定了该 agentKey
     */
    private String agentKey;

    /**
     * 启用状态
     */
    private Boolean enabled;
}