package xin.v5ai.nb.common.agentscope.core.domain.rag;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 引用载荷的唯一编解码处。
 *
 * <p>这些字节必须逐字一致地出现在三个地方：SSE 的 {@code RETRIEVAL} 事件载荷、
 * 知识库问答的引用载荷（同构约定见 CONTEXT.md「引用」），以及随助手消息落库的引用快照
 * （{@code v5ai_message.metadata}，见 docs/adr/0009-citations-persisted-on-assistant-message.md）。
 * 字段名、字段顺序与截断长度都收敛在这里，别再各写一份。</p>
 *
 * <p>形状：{@code [{"knowledgeBaseId":1,"documentId":7,"documentTitle":"…","chunkIndex":3,
 * "content":"…","score":0.83}, …]}；落库时外层再包一层 {@code {"citations":…}}。</p>
 */
@Slf4j
public final class CitationPayload {

    /** 单条引用内容的截断长度：避免单帧 SSE / 单条消息被一屏切片撑大（与知识库问答同口径）。 */
    public static final int CONTENT_CAP = 2_000;

    /** 消息元数据（{@code v5ai_message.metadata}）里承载引用的键名。 */
    public static final String METADATA_KEY = "citations";

    /** 空载荷：与「没有引用」同义。 */
    public static final String EMPTY = "[]";

    private CitationPayload() {
    }

    /**
     * 引用列表 → 载荷 JSON。
     *
     * <p>字段顺序固定（{@code LinkedHashMap}）：这段 JSON 会落库，两次序列化必须逐字节一致，
     * 否则比对与断言都会变成掷骰子。</p>
     *
     * @param hits 引用列表；null 或空列表一律编码为 {@link #EMPTY}
     */
    public static String toJson(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return EMPTY;
        }
        var items = new ArrayList<Map<String, Object>>(hits.size());
        for (RagHit hit : hits) {
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("knowledgeBaseId", hit.knowledgeBaseId());
            item.put("documentId", hit.documentId());
            item.put("documentTitle", hit.documentTitle());
            item.put("chunkIndex", hit.chunkIndex());
            item.put("content", StrUtil.maxLength(hit.content(), CONTENT_CAP));
            item.put("score", hit.score());
            items.add(item);
        }
        return JSONUtil.toJsonStr(items);
    }

    /**
     * 引用列表 → 助手消息的元数据列值：{@code {"citations":[…]}}。
     *
     * @return 无引用时返回 {@code null}——语义是「这一轮没有引用」，与「思考为空」一样不写列
     */
    public static String toMetadata(List<RagHit> hits) {
        return hits == null || hits.isEmpty() ? null : "{\"" + METADATA_KEY + "\":" + toJson(hits) + "}";
    }

    /**
     * 载荷 JSON（裸数组）→ 引用列表：运行期累积（{@code RETRIEVAL} 事件载荷）用。
     *
     * <p>宽容解析：NULL / 空串 / JSON 损坏一律回空列表，并留一条 warn——引用损坏不该把
     * 对话主链路带崩。</p>
     */
    public static List<RagHit> parse(String payload) {
        if (StrUtil.isBlank(payload)) {
            return List.of();
        }
        try {
            return toHits(JSONUtil.parseArray(payload));
        } catch (Exception exception) {
            log.warn("failed to parse citation payload: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 助手消息的元数据列值（{@code {"citations":[…]}}）→ 引用列表。
     *
     * <p>宽容解析：NULL / 空串 / 不是本形状 / JSON 损坏一律回空列表——历史数据里可能躺着别的
     * 语义或半截内容，读历史不该因此 500；真损坏时留一条 warn，免得静默吞掉。</p>
     */
    public static List<RagHit> parseMetadata(String metadata) {
        if (StrUtil.isBlank(metadata)) {
            return List.of();
        }
        try {
            return toHits(JSONUtil.parseObj(metadata).getJSONArray(METADATA_KEY));
        } catch (Exception exception) {
            log.warn("failed to parse citation metadata: {}", exception.getMessage());
            return List.of();
        }
    }

    private static List<RagHit> toHits(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        var hits = new ArrayList<RagHit>(array.size());
        for (int index = 0; index < array.size(); index++) {
            var item = array.getJSONObject(index);
            hits.add(new RagHit(item.getLong("knowledgeBaseId"), item.getLong("documentId"),
                    item.getStr("documentTitle"), item.getInt("chunkIndex"),
                    item.getStr("content"), item.getDouble("score")));
        }
        return hits;
    }
}
