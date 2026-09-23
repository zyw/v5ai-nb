package xin.v5ai.nb.rag.domain.bo;

import java.util.List;

/**
 * 知识库问答（调试会话）请求体（不可变 record）。
 * <p>
 * 会话状态由前端页内维护（多轮 history + 当前问题），后端无状态；
 * 空参数回退知识库已存配置（RagConfigDO.SearchParams / ModelParams），仅本次生效不回写。
 *
 * @param modelId          对话模型 ID（空则回退 ModelParams.modelId）
 * @param nearbySliceCount 拼接邻近文本片数量：每个命中切片前后补足的邻近切片数（默认 5，0=不补）
 * @param prompt           Prompt 模板，可含 <Documents> 变量（默认内置模板）
 * @param resultCount      检索结果数量（默认 20）
 * @param questionRewrite  问题改写开关
 * @param thresholdEnabled 阈值过滤开关
 * @param threshold        相似度阈值
 * @param fusionStrategy   融合策略
 * @param rrfK             RRF K 值
 * @param denseWeight      WEIGHTED_SUM 融合时稠密向量路权重（0~1，默认 0.5）
 * @param messages         会话消息（按序：历史 + 最后一条为当前用户问题）
 */
public record KbChatBo(
        Long modelId,
        Integer nearbySliceCount,
        String prompt,
        Integer resultCount,
        Boolean questionRewrite,
        Boolean thresholdEnabled,
        Double threshold,
        String fusionStrategy,
        Integer rrfK,
        Double denseWeight,
        List<Message> messages
) {

    /**
     * 会话消息（不可变 record）。
     *
     * @param role    角色：user / assistant
     * @param content 消息内容
     */
    public record Message(String role, String content) {
    }
}
