package xin.v5ai.nb.common.core.domain.dto;

/**
 * 下拉选项通用响应（value + label，模型类可携带 isDefault），供 Agent 编辑/绑定等弹窗按需加载。
 *
 * @param value     选项值（业务 ID）
 * @param label     展示文本
 * @param isDefault 是否为默认项（仅模型类下拉使用；其它业务为 null）
 */
public record OptionDTO(Long value, String label, Boolean isDefault) {

    public OptionDTO(Long value, String label) {
        this(value, label, null);
    }
}
