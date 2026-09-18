package xin.v5ai.nb.runtime.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会话列表的展示兜底行：某个会话的首条用户消息内容。
 *
 * <p>废弃门户本地会话列表后，未命名会话（{@code name} 为 null）只能靠首条提问区分，
 * 否则列表里会出现一排同名条目。</p>
 */
@Data
public class RuntimeConversationPreviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 该会话首条用户消息的内容
     */
    private String content;
}
