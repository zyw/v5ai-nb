package xin.v5ai.nb.api.apiKey;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.infrastructure.apiKey.AgentApiKeyStore;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AgentApiKeyController {
    private final AgentApiKeyManagementService apiKeyManagementService;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    public AgentApiKeyController(AgentApiKeyManagementService apiKeyManagementService) {
        this.apiKeyManagementService = apiKeyManagementService;
    }

    @PostMapping("/agents/{agentKey}/api-keys")
    public AgentApiKeyManagementService.CreateApiKeyResult createApiKey(@PathVariable("agentKey") String agentKey) {
        audit("CREATE", "api-key", agentKey, "create application api key");
        return apiKeyManagementService.createApiKey(agentKey);
    }

    @GetMapping("/api-keys")
    public List<AgentApiKeyStore.ApiKeyRecord> listApiKeys() {
        return apiKeyManagementService.listApiKeys();
    }

    @DeleteMapping("/agents/{agentKey}/api-keys")
    public void deleteApiKey(@PathVariable("agentKey") String agentKey) {
        audit("DELETE", "api-key", agentKey, "delete application api key");
        apiKeyManagementService.deleteApiKey(agentKey);
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = StpUtil.getLoginIdAsString();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
