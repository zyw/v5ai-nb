package xin.v5ai.nb.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.resolver.AgentModelResolver;
import xin.v5ai.nb.common.agentscope.utils.ChatResponseTexts;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.domain.KnowledgeChunk;
import xin.v5ai.nb.rag.domain.bo.KbChatBo;
import xin.v5ai.nb.rag.domain.bo.KbRetrieveBo;
import xin.v5ai.nb.rag.domain.vo.KbHitVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.mapper.KnowledgeChunkMapper;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;
import xin.v5ai.nb.rag.service.IKnowledgeQaService;
import xin.v5ai.nb.rag.service.IKnowledgeRetrievalService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库问答实现。
 * <p>
 * Prompt 模板语义：模板中以 {@code <Documents>} 为引用变量（点击即可插入），
 * 服务端把「命中切片 + 邻近补全」组装成的知识块替换进模板；
 * 模板不含该变量时，知识块以「参考知识」小节追加到提示末尾。
 * 「拼接邻近文本片数量 n」= 每个命中切片前后各补 {@code n/2} 片邻近文本
 * （不足按实际），补全行合计不超过 {@link #MAX_CONTEXT_LINES} 行，避免上下文超限。
 *
 * @author ZYW
 * @since 2026-09-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeQaServiceImpl implements IKnowledgeQaService {

    private static final int DEFAULT_NEARBY_SLICE_COUNT = 5;
    private static final int MAX_NEARBY_SLICE_COUNT = 20;
    private static final int MAX_CONTEXT_LINES = 150;
    private static final int HIT_CONTENT_CAP = 2_000;
    private static final int HISTORY_MESSAGE_LIMIT = 20;
    private static final String DOCUMENTS_VAR = "<Documents>";

    /**
     * 内置默认 Prompt 模板（与参考稿角色一致：人设 + 参考资料 + 回答框架）。
     */
    private static final String DEFAULT_PROMPT = """
            你是一位在线知识库问答助手。你的首要任务是根据『参考资料』回答用户问题，
            这些信息可以帮助你生成更准确的回复；如果参考资料没有覆盖用户问题，
            请明确说明不知道，不要编造。请使用与用户问题一致的语言作答。

            <Documents>""";

    private final IKnowledgeBaseService knowledgeBaseService;
    private final IKnowledgeRetrievalService retrievalService;
    private final KnowledgeChunkMapper chunkMapper;
    private final AgentModelResolver modelResolver;

    @Override
    public Flux<RuntimeRunEventDTO> chatStream(Long knowledgeBaseId, KbChatBo request) {
        String runId = UUID.randomUUID().toString();
        return Flux.defer(() -> {
            try {
                return streamEvents(runId, knowledgeBaseId, request);
            } catch (RuntimeException exception) {
                log.warn("kb {} qa failed before stream: {}", knowledgeBaseId, exception.getMessage());
                return Flux.just(RuntimeRunEventDTO.failed(runId, exception.getMessage()));
            }
        });
    }

    private Flux<RuntimeRunEventDTO> streamEvents(String runId, Long knowledgeBaseId, KbChatBo request) {
        KnowledgeBaseVo base = requireBase(knowledgeBaseId);
        RagConfigDO.ModelParams model = configModel(base);

        Long modelId = request.modelId() != null ? request.modelId()
                : (model == null ? null : model.getModelId());
        if (modelId == null) {
            throw new ServiceException("请先选择对话模型");
        }
        String query = currentQuery(request);
        int nearby = clamp(request.nearbySliceCount() != null ? request.nearbySliceCount()
                : (model != null && model.getNearbySliceCount() != null ? model.getNearbySliceCount()
                : DEFAULT_NEARBY_SLICE_COUNT), 0, MAX_NEARBY_SLICE_COUNT);
        String prompt = resolvePrompt(request, model);

        // 1) 检索（参数沿用搜索侧默认补齐与问题改写）
        List<KbHitVo> hits = retrievalService.retrieve(knowledgeBaseId, new KbRetrieveBo(
                query,
                request.resultCount(),
                request.questionRewrite(),
                request.thresholdEnabled(),
                request.threshold(),
                request.fusionStrategy(),
                request.rrfK(),
                modelId,
                request.denseWeight()));
        // 2) 邻近文本补全 + 组装知识块
        List<String> contextLines = new ArrayList<>();
        int order = 1;
        for (KbHitVo hit : hits) {
            contextLines.add("%d. (%s, 切片 %d): %s".formatted(order++, hit.documentTitle(), hit.chunkIndex(),
                    hit.content()));
        }
        contextLines.addAll(neighborLines(hits, nearby));
        String documents = contextLines.isEmpty()
                ? "（未检索到相关资料）"
                : contextLines.stream().collect(Collectors.joining("\n"));
        // 3) 提示词：<Documents> 替换 / 追加
        String systemPrompt = prompt.contains(DOCUMENTS_VAR)
                ? prompt.replace(DOCUMENTS_VAR, documents)
                : prompt + "\n\n参考知识（仅当与问题相关时使用）：\n" + documents;
        // 4) 组装消息并流式对话
        Model modelInstance = modelResolver.resolve(modelId);
        List<Msg> messages = buildMessages(systemPrompt, request.messages(), query);
        Flux<ChatResponse> responses = modelInstance.stream(
                messages, List.of(), GenerateOptions.builder().stream(true).build());

        String retrievalPayload = hitsPayload(hits);
        return Flux.concat(
                        Flux.just(RuntimeRunEventDTO.retrieval(runId, retrievalPayload)),
                        responses.flatMapIterable(response -> deltas(runId, response)))
                .concatWith(Flux.just(RuntimeRunEventDTO.completed(runId)))
                .onErrorResume(error -> Flux.just(RuntimeRunEventDTO.failed(runId, safeMessage(error))));
    }

    /**
     * 单个响应分片 → 事件列表：推理内容（ThinkingBlock）与回答（TextBlock）分流为
     * REASONING_DELTA / TEXT_DELTA，让前端把「思考过程」与「回答」分开展示。
     */
    private static List<RuntimeRunEventDTO> deltas(String runId, ChatResponse response) {
        List<RuntimeRunEventDTO> events = new ArrayList<>(2);
        String thinking = ChatResponseTexts.thinkingOf(response);
        if (thinking != null && !thinking.isEmpty()) {
            events.add(RuntimeRunEventDTO.reasoningDelta(runId, thinking));
        }
        String text = ChatResponseTexts.textOf(response);
        if (text != null && !text.isEmpty()) {
            events.add(RuntimeRunEventDTO.textDelta(runId, text));
        }
        return events;
    }

    // ---------- context & messages ----------

    /**
     * 取每条命中的邻近切片行（已命中行去重，超过行数上限截断）。
     * 邻近文本只在默认行内存储（PgVectorStore）时可按需取回；外部向量存储无单条取回路径时为空。
     */
    private List<String> neighborLines(List<KbHitVo> hits, int nearby) {
        int radius = Math.max(0, (nearby - 1) / 2);
        if (radius <= 0) {
            return List.of();
        }
        Long knowledgeBaseId = hits.isEmpty() ? null : hits.get(0).knowledgeBaseId();
        Map<Long, HitIndexes> byDocument = new LinkedHashMap<>();
        for (KbHitVo hit : hits) {
            byDocument.computeIfAbsent(hit.documentId(), id -> new HitIndexes(knowledgeBaseId, hit.documentTitle()))
                    .hitIndexes.add(hit.chunkIndex());
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Long, HitIndexes> entry : byDocument.entrySet()) {
            Long documentId = entry.getKey();
            HitIndexes indexes = entry.getValue();
            Set<Integer> neighbor = new HashSet<>();
            for (int hitIndex : indexes.hitIndexes) {
                for (int i = hitIndex - radius; i <= hitIndex + radius; i++) {
                    if (i != hitIndex) {
                        neighbor.add(i);
                    }
                }
            }
            neighbor.removeAll(indexes.hitIndexes);
            if (neighbor.isEmpty()) {
                continue;
            }
            int min = neighbor.stream().min(Integer::compareTo).orElse(0);
            int max = neighbor.stream().max(Integer::compareTo).orElse(0);
            List<KnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                    .eq(KnowledgeChunk::getKnowledgeBaseId, indexes.knowledgeBaseId)
                    .eq(KnowledgeChunk::getDocumentId, documentId)
                    .between(KnowledgeChunk::getChunkIndex, min, max)
                    .orderByAsc(KnowledgeChunk::getChunkIndex));
            for (KnowledgeChunk chunk : chunks) {
                if (!neighbor.contains(chunk.getChunkIndex())) {
                    continue;
                }
                if (lines.size() >= MAX_CONTEXT_LINES) {
                    return lines;
                }
                String title = indexes.documentTitle == null ? "doc-" + documentId : indexes.documentTitle;
                lines.add("(补全 %s, 切片 %d): %s".formatted(title, chunk.getChunkIndex(), chunk.getContent()));
            }
        }
        return lines;
    }

    private List<Msg> buildMessages(String systemPrompt, List<KbChatBo.Message> rawMessages, String query) {
        List<Msg> messages = new ArrayList<>();
        messages.add(Msg.builder().role(MsgRole.SYSTEM).textContent(systemPrompt).build());
        List<KbChatBo.Message> history = rawMessages == null ? List.of()
                : rawMessages.subList(0, Math.max(0, rawMessages.size() - 1));
        int from = Math.max(0, history.size() - HISTORY_MESSAGE_LIMIT);
        for (KbChatBo.Message message : history.subList(from, history.size())) {
            MsgRole role = "assistant".equalsIgnoreCase(message.role()) ? MsgRole.ASSISTANT : MsgRole.USER;
            if (StrUtil.isBlank(message.content())) {
                continue;
            }
            messages.add(Msg.builder().role(role).textContent(message.content()).build());
        }
        messages.add(Msg.builder().role(MsgRole.USER).textContent(query).build());
        return messages;
    }

    // ---------- helpers ----------

    private static String currentQuery(KbChatBo request) {
        List<KbChatBo.Message> messages = request.messages();
        if (messages == null || messages.isEmpty() || StrUtil.isBlank(messages.get(messages.size() - 1).content())) {
            throw new ServiceException("请输入问题");
        }
        return messages.get(messages.size() - 1).content().trim();
    }

    private static String resolvePrompt(KbChatBo request, RagConfigDO.ModelParams model) {
        String configured = request.prompt() != null && !request.prompt().isBlank()
                ? request.prompt()
                : (model != null && StrUtil.isNotBlank(model.getPrompt()) ? model.getPrompt() : null);
        return StrUtil.isBlank(configured) ? DEFAULT_PROMPT : configured.trim();
    }

    private KnowledgeBaseVo requireBase(Long knowledgeBaseId) {
        KnowledgeBaseVo base = knowledgeBaseId == null ? null : knowledgeBaseService.getKnowledgeBase(knowledgeBaseId);
        if (base == null) {
            throw new ServiceException("知识库不存在: " + knowledgeBaseId);
        }
        return base;
    }

    private static RagConfigDO.ModelParams configModel(KnowledgeBaseVo base) {
        return base.getConfig() == null || base.getConfig().getModelParams() == null
                ? null : base.getConfig().getModelParams();
    }

    /**
     * 命中载荷：引用展示用（内容截断，避免单条 SSE 过大）。
     */
    private static String hitsPayload(List<KbHitVo> hits) {
        List<Map<String, Object>> payload = hits.stream().map(hit -> {
            Map<String, Object> item = new HashMap<>();
            item.put("knowledgeBaseId", hit.knowledgeBaseId());
            item.put("documentId", hit.documentId());
            item.put("documentTitle", hit.documentTitle());
            item.put("chunkIndex", hit.chunkIndex());
            item.put("content", StrUtil.maxLength(hit.content(), HIT_CONTENT_CAP));
            item.put("score", hit.score());
            return item;
        }).toList();
        return JSONUtil.toJsonStr(payload);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (StrUtil.isBlank(message)) {
            message = error.getClass().getSimpleName();
        }
        return StrUtil.maxLength(message.replaceAll("\\s+", " ").trim(), 300);
    }

    /**
     * 每个文档的命中索引集合（供邻近补全按文档取窗口）。
     */
    private static final class HitIndexes {
        private final Long knowledgeBaseId;
        private final String documentTitle;
        private final Set<Integer> hitIndexes = new HashSet<>();

        private HitIndexes(Long knowledgeBaseId, String documentTitle) {
            this.knowledgeBaseId = knowledgeBaseId;
            this.documentTitle = documentTitle;
        }
    }
}
