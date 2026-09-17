package xin.v5ai.nb.platform.domain.vo;

import cn.hutool.core.util.ReflectUtil;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.json.enhance.JsonValueEnhancer;
import xin.v5ai.nb.common.sensitive.core.SensitiveService;
import xin.v5ai.nb.common.sensitive.core.SensitiveStrategy;
import xin.v5ai.nb.common.sensitive.handler.SensitiveJsonFieldProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 用户列表响应脱敏链路测试：{@code @Sensitive} 注解 → 处理器 → 响应 JSON。
 */
class PlmUserVoDesensitizeTest {

    private static final String EMAIL = "zhangsan@example.com";
    private static final String PHONE = "13800138000";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 需要脱敏时，邮箱与手机号被策略替换，其余字段保持不变。
     */
    @Test
    void masksAnnotatedFieldsWhenSensitive() {
        JsonNode body = enhance((roleKey, perms) -> true);

        assertEquals(SensitiveStrategy.EMAIL.desensitizer().apply(EMAIL), body.at("/data/rows/0/email").asString());
        assertEquals(SensitiveStrategy.PHONE.desensitizer().apply(PHONE), body.at("/data/rows/0/phoneNumber").asString());
        assertNotEquals(EMAIL, body.at("/data/rows/0/email").asString());
        assertEquals("zhangsan", body.at("/data/rows/0/userName").asString());
    }

    /**
     * 不需要脱敏时，注解字段返回原值。
     */
    @Test
    void keepsOriginalValueWhenNotSensitive() {
        JsonNode body = enhance((roleKey, perms) -> false);

        assertEquals(EMAIL, body.at("/data/rows/0/email").asString());
        assertEquals(PHONE, body.at("/data/rows/0/phoneNumber").asString());
    }

    /**
     * 按给定脱敏判断渲染用户分页响应。
     *
     * @param sensitiveService 脱敏判断
     * @return 渲染后的响应 JSON
     */
    private JsonNode enhance(SensitiveService sensitiveService) {
        SensitiveJsonFieldProcessor processor = new SensitiveJsonFieldProcessor();
        ReflectUtil.setFieldValue(processor, "sensitiveService", sensitiveService);
        JsonValueEnhancer enhancer = new JsonValueEnhancer(jsonMapper, List.of(processor));

        PlmUserVo user = new PlmUserVo();
        user.setUserName("zhangsan");
        user.setEmail(EMAIL);
        user.setPhoneNumber(PHONE);

        return (JsonNode) enhancer.enhance(R.ok(PageResult.build(List.of(user), 1L)));
    }

}