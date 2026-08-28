//package xin.v5ai.nb.platform.apiKey;
//
//import java.util.List;
//
//public interface AgentApiKeyStore {
//    String findSecretHash(String agentKey);
//
//    void upsertSecret(String agentKey, String secretHash);
//
//    List<ApiKeyRecord> list();
//
//    void delete(String agentKey);
//
//    record ApiKeyRecord(String agentKey, boolean enabled) {
//    }
//}
