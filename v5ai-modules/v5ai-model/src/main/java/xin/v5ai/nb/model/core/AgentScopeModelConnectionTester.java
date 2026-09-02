package xin.v5ai.nb.model.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;
import xin.v5ai.nb.model.domain.vo.TestModelConnectionVo;

import java.time.Duration;
import java.util.List;

/**
 * 模型连通性测试：经 {@link ModelChatClient} 发起一次最小请求，
 * 取到首个响应块即判定连通。失败消息由模型对话实现统一生成（含目标端点与超时/连接分类）。
 */
@Component
public class AgentScopeModelConnectionTester implements ModelConnectionTester {

    private final ModelChatClient modelChatClient;
    private final Duration timeout;

    public AgentScopeModelConnectionTester(
            ModelChatClient modelChatClient,
            @Value("${model.connection-test.timeout-millis:10000}") long timeoutMillis
    ) {
        this.modelChatClient = modelChatClient;
        this.timeout = Duration.ofMillis(timeoutMillis);
    }

    @Override
    public TestModelConnectionVo test(Long modelId) {
        var ping = Msg.builder().role(MsgRole.USER).textContent("ping").build();
        try {
            modelChatClient.firstResponse(modelId, List.of(ping),
                    GenerateOptions.builder().maxTokens(5).build(), timeout);
            return TestModelConnectionVo.ok(modelId);
        } catch (ModelChatException exception) {
            return TestModelConnectionVo.failed(modelId, exception.getMessage());
        }
    }
}
