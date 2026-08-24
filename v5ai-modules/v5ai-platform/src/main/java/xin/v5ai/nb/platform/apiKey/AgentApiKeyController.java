package xin.v5ai.nb.platform.apiKey;

import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;

@RestController
@RequestMapping("/api/admin")
public class AgentApiKeyController {
    private final AgentApiKeyManagementService apiKeyManagementService;

    public AgentApiKeyController(AgentApiKeyManagementService apiKeyManagementService) {
        this.apiKeyManagementService = apiKeyManagementService;
    }

    @PostMapping("/agents/{agentKey}/api-keys")
    @Log(title = "生成应用API密钥", businessType = BusinessType.INSERT)
    public R<AgentApiKeyManagementService.CreateApiKeyResult> createApiKey(@PathVariable("agentKey") String agentKey) {
        return R.ok(apiKeyManagementService.createApiKey(agentKey));
    }

    @GetMapping("/api-keys")
    public R<PageResult<AgentApiKeyStore.ApiKeyRecord>> listApiKeys(PageQuery pageQuery) {
//      TODO PageUtils.page  return R.ok(PageUtils.page(apiKeyManagementService.listApiKeys(), pageQuery));
        return R.ok();
    }

    @DeleteMapping("/agents/{agentKey}/api-keys")
    @Log(title = "删除应用API密钥", businessType = BusinessType.DELETE)
    public R<Void> deleteApiKey(@PathVariable("agentKey") String agentKey) {
        apiKeyManagementService.deleteApiKey(agentKey);
        return R.ok();
    }
}
