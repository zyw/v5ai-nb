package xin.v5ai.nb.common.log.enums;

import lombok.Getter;

/**
 * 操作人类别
 *
 * @author ruoyi
 */
@Getter
public enum OperatorType {
    /**
     * 其它
     */
    OTHER("0"),

    /**
     * 后台用户
     */
    MANAGE("1"),

    /**
     * 手机端用户
     */
    MOBILE("2");

    private final String code;

    OperatorType(String code) {
        this.code = code;
    }
}
