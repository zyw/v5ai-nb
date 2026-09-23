package xin.v5ai.nb.rag.domain.bo;

import java.util.List;

/**
 * 文档批量操作请求体（批量重新解析 / 批量删除共用）。
 *
 * @param ids 文档 ID 集合
 */
public record DocumentIdsBo(List<Long> ids) {
}