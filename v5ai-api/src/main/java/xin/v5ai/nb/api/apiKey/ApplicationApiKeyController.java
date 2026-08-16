package xin.v5ai.nb.api.apiKey;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.platform.AuditRecorder;
import xin.v5ai.nb.infrastructure.apiKey.ApplicationApiKeyStore;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class ApplicationApiKeyController {
    private final ApplicationApiKeyManagementService apiKeyManagementService;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    public ApplicationApiKeyController(ApplicationApiKeyManagementService apiKeyManagementService) {
        this.apiKeyManagementService = apiKeyManagementService;
    }

    @PostMapping("/applications/{appKey}/api-keys")
    public ApplicationApiKeyManagementService.CreateApiKeyResult createApiKey(@PathVariable("appKey") String appKey) {
        audit("CREATE", "api-key", appKey, "create application api key");
        return apiKeyManagementService.createApiKey(appKey);
    }

    @GetMapping("/api-keys")
    public List<ApplicationApiKeyStore.ApiKeyRecord> listApiKeys() {
        return apiKeyManagementService.listApiKeys();
    }

    @DeleteMapping("/applications/{appKey}/api-keys")
    public void deleteApiKey(@PathVariable("appKey") String appKey) {
        audit("DELETE", "api-key", appKey, "delete application api key");
        apiKeyManagementService.deleteApiKey(appKey);
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
