package xin.v5ai.nb.common.core.domain;

/**
 * 下拉选项通用响应（value + label），供 Agent 编辑/绑定等弹窗按需加载。
 */
public record OptionDTO(Long value, String label) {
}
