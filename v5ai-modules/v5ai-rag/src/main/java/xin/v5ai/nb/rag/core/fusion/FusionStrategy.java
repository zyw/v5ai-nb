package xin.v5ai.nb.rag.core.fusion;

import xin.v5ai.nb.common.core.exception.ServiceException;

/**
 * 检索结果融合策略：决定「向量 + 关键词」两路命中如何合成一份结果。
 * <ul>
 *   <li>{@link #RRF}（默认）：倒数排名融合，按名次计分 {@code sum(1/(rrfK + rank))}；</li>
 *   <li>{@link #WEIGHTED_SUM}：加权求和，合并分 = denseWeight×向量相似度 + (1-denseWeight)×关键词分；</li>
 *   <li>{@link #VECTOR} / {@link #KEYWORD}：不融合的退化选择，仅取单路。</li>
 * </ul>
 * 调试/问答检索与运行时注入共用同一取值集合；非法取值由 {@link #parse} 直接拒绝，不静默降级。
 *
 * @author ZYW
 * @since 2026-09-19
 */
public enum FusionStrategy {

    RRF,
    WEIGHTED_SUM,
    VECTOR,
    KEYWORD;

    /** 未配置时的默认策略。 */
    public static final FusionStrategy DEFAULT = RRF;

    /**
     * 解析（大小写不敏感、容忍首尾空白）用户/配置传入的策略名；
     * 空白按默认策略 {@link #RRF} 处理，未知取值抛 {@link ServiceException}（400 语义）。
     */
    public static FusionStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (FusionStrategy strategy : values()) {
            if (strategy.name().equals(normalized)) {
                return strategy;
            }
        }
        throw new ServiceException("不支持的融合策略: " + value);
    }
}
