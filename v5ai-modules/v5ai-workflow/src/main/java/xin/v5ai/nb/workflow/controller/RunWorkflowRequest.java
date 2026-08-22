package xin.v5ai.nb.workflow.controller;

import java.util.Map;

/**
 * Workflow 测试运行请求体。
 *
 * @param inputs 运行输入变量
 */
public record RunWorkflowRequest(Map<String, Object> inputs) {
}
