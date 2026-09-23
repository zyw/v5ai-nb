package xin.v5ai.nb.rag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.rag.domain.bo.KbChatBo;
import xin.v5ai.nb.rag.domain.bo.KbRetrieveBo;
import xin.v5ai.nb.rag.domain.vo.KbHitVo;
import xin.v5ai.nb.rag.service.IKnowledgeQaService;
import xin.v5ai.nb.rag.service.IKnowledgeRetrievalService;

import java.util.List;

/**
 * 知识库调试 API（知识检索 / 知识问答 tab 的后端）：
 * 检索预览与无 AgentDTO 的流式问答（页内调试会话，不落库）。
 *
 * @author ZYW
 * @since 2026-09-06
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/knowledge-bases/{kbId}")
public class KnowledgeDebugController {

    private final IKnowledgeRetrievalService retrievalService;
    private final IKnowledgeQaService qaService;

    @SaCheckPermission("rag:knowledge:retrieve")
    @PostMapping("/retrieve")
    public R<List<KbHitVo>> retrieve(@PathVariable("kbId") Long kbId,
                                     @RequestBody(required = false) KbRetrieveBo request) {
        return R.ok(retrievalService.retrieve(kbId, request));
    }

    @SaCheckPermission("rag:knowledge:retrieve")
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> chatStream(@PathVariable("kbId") Long kbId,
                                                                @RequestBody(required = false) KbChatBo request) {
        return qaService.chatStream(kbId, request)
                .map(event -> ServerSentEvent.builder(event)
                        .event(event.type().name())
                        .id(event.runId())
                        .build());
    }
}
