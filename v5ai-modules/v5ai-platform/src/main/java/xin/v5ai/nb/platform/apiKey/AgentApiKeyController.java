package xin.v5ai.nb.platform.apiKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.platform.AuditLogRecorderService;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;

@RestController
@RequestMapping("/api/admin")
public class AgentApiKeyController {
    private final AgentApiKeyManagementService apiKeyManagementService;

    @Autowired(required = false)
    private AuditLogRecorderService auditRecorder;

    public AgentApiKeyController(AgentApiKeyManagementService apiKeyManagementService) {
        this.apiKeyManagementService = apiKeyManagementService;
    }

    @PostMapping("/agents/{agentKey}/api-keys")
    public R<AgentApiKeyManagementService.CreateApiKeyResult> createApiKey(@PathVariable("agentKey") String agentKey) {
        audit("CREATE", "api-key", agentKey, "create application api key");
        return R.ok(apiKeyManagementService.createApiKey(agentKey));
    }

    @GetMapping("/api-keys")
    public R<PageResult<AgentApiKeyStore.ApiKeyRecord>> listApiKeys(PageQuery pageQuery) {
//      TODO PageUtils.page  return R.ok(PageUtils.page(apiKeyManagementService.listApiKeys(), pageQuery));
        return R.ok();
    }

    @DeleteMapping("/agents/{agentKey}/api-keys")
    public R<Void> deleteApiKey(@PathVariable("agentKey") String agentKey) {
        audit("DELETE", "api-key", agentKey, "delete application api key");
        apiKeyManagementService.deleteApiKey(agentKey);
        return R.ok();
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = LoginHelper.getUsername();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
