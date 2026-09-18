package xin.v5ai.nb.platform.domain;

import lombok.Getter;

/**
 * 校验结果。
 */
@Getter
public enum VerifyEnum {
    /** 校验通过 */
    OK(1, "校验通过"),
    /** 令牌不存在或已过期（一次性使用） */
    EXPIRED(2, "令牌不存在或已过期（一次性使用）"),
    /** 拖动位置与缺口误差超出允许范围 */
    MISMATCH(3, "拖动位置与缺口误差超出允许范围");

    /**
     * 枚举值。
     */
    private final int value;
    /**
     * 枚举描述。
     */
    private final String message;

    VerifyEnum(int value, String message) {
        this.value = value;
        this.message = message;
    }

    public static VerifyEnum valueOf(int value) {
        return values()[value];
    }

}
