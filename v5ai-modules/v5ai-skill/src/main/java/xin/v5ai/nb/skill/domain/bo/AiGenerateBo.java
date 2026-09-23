package xin.v5ai.nb.skill.domain.bo;

/**
 * AI 生成 SKILL.md 请求体。
 *
 * @param modelId     使用的 Chat 模型 ID
 * @param requirement 需求说明（必填，将作为 SKILL.md frontmatter 的 description）
 */
public record AiGenerateBo(Long modelId, String requirement) {
}
