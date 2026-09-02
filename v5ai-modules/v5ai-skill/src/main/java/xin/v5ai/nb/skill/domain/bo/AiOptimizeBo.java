package xin.v5ai.nb.skill.domain.bo;

/**
 * AI 优化 Skill 文件请求体。
 *
 * @param modelId     使用的 Chat 模型 ID
 * @param filePath    要优化的包内相对路径（DRAFT 版本中必须存在）
 * @param requirement 优化要求（必填）
 * @param direction   优化方向（选填，如"更简洁""更贴合规范"）
 */
public record AiOptimizeBo(Long modelId, String filePath, String requirement, String direction) {
}
