package xin.v5ai.nb.api.apiKey;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.infrastructure.apiKey.ApplicationApiKeyStore;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class ApplicationApiKeyController {
    private final ApplicationApiKeyManagementService apiKeyManagementService;

    public ApplicationApiKeyController(ApplicationApiKeyManagementService apiKeyManagementService) {
        this.apiKeyManagementService = apiKeyManagementService;
    }

    @PostMapping("/applications/{appKey}/api-keys")
    public ApplicationApiKeyManagementService.CreateApiKeyResult createApiKey(@PathVariable("appKey") String appKey) {
        return apiKeyManagementService.createApiKey(appKey);
    }

    @GetMapping("/api-keys")
    public List<ApplicationApiKeyStore.ApiKeyRecord> listApiKeys() {
        return apiKeyManagementService.listApiKeys();
    }

    @DeleteMapping("/applications/{appKey}/api-keys")
    public void deleteApiKey(@PathVariable("appKey") String appKey) {
        apiKeyManagementService.deleteApiKey(appKey);
    }
}
