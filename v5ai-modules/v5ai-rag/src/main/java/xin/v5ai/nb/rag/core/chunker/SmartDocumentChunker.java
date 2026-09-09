package xin.v5ai.nb.rag.core.chunker;

import cn.hutool.json.JSONUtil;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 智能切片：由所选对话模型（config.chunkParams.chunkModelId）将全文切分为语义候选片段
 * （JSON 字符串数组），再经共享流水线按最大长度递归切分。
 * 模型未配置、调用失败或输出无法解析时，回退为按空行划分段落（与按长度切片的一级切分一致）。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Slf4j
@Component
public class SmartDocumentChunker implements ChunkingStrategy {

    private static final String SYSTEM_PROMPT = """
            你是文档语义切片助手。将用户提供的文档按语义边界切分为若干连续片段：
            1. 尽量在语义完整处（段落/主题/句子边界）切分，不要丢弃原文内容；
            2. 各片段按原文顺序排列，并尽量保持原文措辞；
            3. 只输出一个 JSON 字符串数组，数组的每个元素是一个片段，不要输出任何解释、编号或代码围栏。
            """;

    private final ChunkPipeline pipeline;
    private final ModelChatClient modelChatClient;

    @Value("${v5ai.rag.smart-chunk.timeout-seconds:60}")
    private long timeoutSeconds;

    public SmartDocumentChunker(ChunkPipeline pipeline, ModelChatClient modelChatClient) {
        this.pipeline = pipeline;
        this.modelChatClient = modelChatClient;
    }

    @Override
    public ChunkMode supportedMode() {
        return ChunkMode.SMART;
    }

    @Override
    public List<String> chunk(String text) {
        return chunk(text, ChunkingOptions.DEFAULT);
    }

    @Override
    public List<String> chunk(String text, ChunkingOptions options) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        List<String> segments = semanticSegments(text, options.chunkModelId());
        return pipeline.run(segments, options);
    }

    /**
     * 取得用于二级切分的语义片段：优先调用对话模型，任何失败/缺失都回退按段落切分。
     */
    private List<String> semanticSegments(String text, Long modelId) {
        if (modelId == null) {
            log.warn("smart chunk skipped: no chunk model configured, fallback to paragraph split");
            return splitByParagraphs(text);
        }
        try {
            List<Msg> messages = List.of(
                    Msg.builder().role(MsgRole.SYSTEM).textContent(SYSTEM_PROMPT).build(),
                    Msg.builder().role(MsgRole.USER).textContent(text).build());
            String raw = modelChatClient.chatText(modelId, messages, Duration.ofSeconds(timeoutSeconds));
            List<String> segments = parseSemanticSegments(raw);
            if (segments.isEmpty()) {
                log.warn("smart chunk got empty result for model {}, fallback to paragraph split", modelId);
                return splitByParagraphs(text);
            }
            return segments;
        } catch (Exception exception) {
            log.warn("smart chunk failed for model {}: {}, fallback to paragraph split",
                    modelId, exception.getMessage());
            return splitByParagraphs(text);
        }
    }

    /**
     * 宽容解析模型输出：剥离 Markdown 代码围栏后按 JSON 数组解析，仅保留字符串元素、过滤空白；
     * 数字/null/嵌套结构直接丢弃。非 JSON（含非数组 JSON）由 {@link JSONUtil#parseArray} 抛异常，
     * 交由调用方回退。
     */
    static List<String> parseSemanticSegments(String raw) {
        String cleaned = stripCodeFence(raw);
        if (cleaned.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (Object element : JSONUtil.parseArray(cleaned)) {
            if (!(element instanceof CharSequence cs)) {
                continue;
            }
            String value = cs.toString().trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline < 0) {
                return "";
            }
            s = s.substring(firstNewline + 1);
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence);
            }
        }
        return s.trim();
    }

    private List<String> splitByParagraphs(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
                .filter(s -> !s.isBlank())
                .toList();
    }
}