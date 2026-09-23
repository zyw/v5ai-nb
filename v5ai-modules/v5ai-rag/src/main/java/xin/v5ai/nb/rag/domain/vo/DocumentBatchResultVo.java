package xin.v5ai.nb.rag.domain.vo;

import java.util.List;

/**
 * 文档批量操作结果汇总：逐项处理、失败跳过，返回计数与失败明细供前端提示。
 *
 * @param succeeded 成功数量
 * @param skipped   跳过数量（仅重新解析：非「处理完成/失败」状态的文档）
 * @param failures  失败明细（文档不存在或处理异常）
 */
public record DocumentBatchResultVo(int succeeded, int skipped, List<Failure> failures) {

    /**
     * 单个文档的失败项。
     *
     * @param documentId 文档 ID
     * @param reason     失败原因
     */
    public record Failure(Long documentId, String reason) {
    }
}