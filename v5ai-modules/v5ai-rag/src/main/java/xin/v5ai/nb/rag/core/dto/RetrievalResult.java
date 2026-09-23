package xin.v5ai.nb.rag.core.dto;

import java.util.List;

/**
 * 一次运行期检索的结果。
 *
 * @param context   拼好待注入系统提示的上下文文本
 * @param citations 结构化命中（带文档标题），供 RETRIEVAL 事件的引用展示使用
 */
public record RetrievalResult(String context, List<RetrievalCitation> citations) {
    public static RetrievalResult empty() {
        return new RetrievalResult("", List.of());
    }

    public boolean isEmpty() {
        return context == null || context.isBlank();
    }
}
