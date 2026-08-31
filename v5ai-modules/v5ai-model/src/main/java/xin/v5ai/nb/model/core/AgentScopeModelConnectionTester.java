package xin.v5ai.nb.model.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelRuntimeConfigGateway;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.factory.AgentScopeModelFactory;
import xin.v5ai.nb.common.agentscope.core.factory.config.AgentModelCredentialConfig;
import xin.v5ai.nb.common.encrypt.utils.CipherUtils;
import xin.v5ai.nb.model.domain.vo.TestModelConnectionVo;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentScopeModelConnectionTester implements ModelConnectionTester {
    private static final int MESSAGE_MAX_LENGTH = 200;

    @Value("${model.connection-test.timeout-millis:10000}")
    private long timeoutMillis;

    private final ModelRuntimeConfigGateway runtimeConfigGateway;
    private final AgentScopeModelFactory modelFactory;
    private final Duration timeout = Duration.ofMillis(timeoutMillis);

//    @Autowired
//    public AgentScopeModelConnectionTester(
//            ModelRuntimeConfigGateway runtimeConfigGateway,
//            CredentialCipher credentialCipher,
//            AgentScopeModelFactory modelFactory
//    ) {
//        this(runtimeConfigGateway, credentialCipher, modelFactory, Duration.ofMillis(timeoutMillis));
//    }
//
//    AgentScopeModelConnectionTester(
//            ModelRuntimeConfigGateway runtimeConfigGateway,
//            CredentialCipher credentialCipher,
//            AgentScopeModelFactory modelFactory,
//            Duration timeout
//    ) {
//        this.runtimeConfigGateway = runtimeConfigGateway;
//        this.credentialCipher = credentialCipher;
//        this.modelFactory = modelFactory;
//        this.timeout = timeout;
//    }

    @Override
    public TestModelConnectionVo test(Long modelId) {
        var config = runtimeConfigGateway.findRuntimeConfigByModelId(modelId);
        if (config == null) {
            throw new IllegalArgumentException("model runtime config not found: " + modelId);
        }
        var plaintextCredentials = CipherUtils.decrypt(config.credentialsCiphertext());
        var credentials = AgentModelCredentialConfig.parse(plaintextCredentials);
        var model = modelFactory.create(config, plaintextCredentials);
        var ping = Msg.builder().role(MsgRole.USER).textContent("ping").build();
        try {
            var first = model.stream(
                            List.of(ping),
                            List.of(),
                            GenerateOptions.builder().maxTokens(5).build())
                    .blockFirst(timeout);
            return first != null
                    ? TestModelConnectionVo.ok(modelId)
                    : TestModelConnectionVo.failed(modelId,
                            "model returned no response within " + timeout.toSeconds() + "s");
        } catch (Exception exception) {
            return TestModelConnectionVo.failed(modelId,friendlyMessage(config, credentials, exception));
        }
    }

    /**
     * Human-readable failure including the target endpoint so misconfigured
     * baseUrl/apiKey/modelKey are immediately visible.
     */
    private String friendlyMessage(ModelRuntimeConfigDTO config, AgentModelCredentialConfig credentials,
                                   Exception exception) {
        var target = describeTarget(config, credentials);
        var detail = sanitize(exception.getMessage(), exception.getClass().getSimpleName());
        if (detail.contains("Timeout on blocking read")) {
            return "timed out after " + timeout.toSeconds() + "s contacting " + target
                    + " (check baseUrl / network / firewall)";
        }
        if (detail.contains("Request cancelled")) {
            return "request to " + target + " was cancelled (client or server closed the connection)";
        }
        return "failed contacting " + target + ": " + detail;
    }

    private String describeTarget(ModelRuntimeConfigDTO config, AgentModelCredentialConfig credentials) {
        var baseUrl = credentials.baseUrl() == null || credentials.baseUrl().isBlank()
                ? "(default endpoint)"
                : credentials.baseUrl();
        return baseUrl + " (model " + config.modelKey() + ", provider " + config.providerType() + ")";
    }

    private String sanitize(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        var cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() > MESSAGE_MAX_LENGTH
                ? cleaned.substring(0, MESSAGE_MAX_LENGTH) + "…"
                : cleaned;
    }
}
