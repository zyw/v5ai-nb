package xin.v5ai.nb.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 新建 API Key 的响应：{@code apiKey} 为明文，仅此一次返回，之后只能重新签发。
 */
@Data
@Builder
public class ApiKeysRespVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String trackingId;

    /**
     * 明文 API Key（仅创建时返回一次）
     */
    private String apiKey;

    /**
     * 创建时绑定的可访问 Agent
     */
    private List<String> agentKeys;
}