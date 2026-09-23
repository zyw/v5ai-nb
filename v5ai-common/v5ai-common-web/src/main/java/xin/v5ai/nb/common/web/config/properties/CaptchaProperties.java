package xin.v5ai.nb.common.web.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 验证码 配置属性
 *
 * @author Lion Li
 */
@Data
@ConfigurationProperties(prefix = "v5ai.captcha")
public class CaptchaProperties {

    /**
     * 是否启用验证码校验。
     */
    private Boolean enable;

    /**
     * 验证码类型
     */
    private String type;

    /**
     * 数字验证码位数
     */
    private Integer numberLength;

    /**
     * 字符验证码长度
     */
    private Integer charLength;

    /**
     * 登录错误次数达到该值后，再次登录需先通过滑块验证（需小于 user.password.maxRetryCount）。
     */
    private Integer sliderThreshold = 3;

    /**
     * 滑块拼图令牌有效期（分钟）。
     */
    private Integer expireMinutes = 2;
}
