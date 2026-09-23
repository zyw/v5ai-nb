package xin.v5ai.nb.rag.service;

import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.rag.domain.bo.KbChatBo;

/**
 * 知识库问答（页内调试会话）：每轮 = 检索 → 邻近文本补全 → Prompt 注入 → 对话模型流式回答。
 * <p>
 * 无状态：会话历史由前端随请求携带，回答不落库、不占运行配额；
 * 事件流复用 {@link RuntimeRunEventDTO} 语义：RETRIEVAL（引用命中）→ TEXT_DELTA* → RUN_COMPLETED / RUN_FAILED。
 *
 * @author ZYW
 * @since 2026-09-06
 */
public interface IKnowledgeQaService {

    /**
     * 发起一轮流式问答。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param request         本轮完整会话与调试参数
     * @return SSE 事件流
     */
    Flux<RuntimeRunEventDTO> chatStream(Long knowledgeBaseId, KbChatBo request);
}
