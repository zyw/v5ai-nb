package xin.v5ai.nb.workflow.domain.bo;

import java.util.Map;

/**
 * Workflow 测试运行请求体。
 *
 * @param inputs 运行输入变量
 */
public record RunWorkflowBo(Map<String, Object> inputs, boolean draft) {
    public RunWorkflowBo(Map<String, Object> inputs) { this(inputs, false); }
}
