//package xin.v5ai.nb.platform.service.impl;
//
//import org.springframework.context.annotation.Primary;
//import org.springframework.stereotype.Repository;
//import xin.v5ai.nb.platform.apiKey.AgentApiKeyStore;
//import xin.v5ai.nb.platform.domain.V5aiApiKey;
//import xin.v5ai.nb.platform.mapper.V5aiApiKeyMapper;
//
//import java.util.List;
//
//@Primary
//@Repository
//public class MyBatisAgentApiKeyStore implements AgentApiKeyStore {
//    private final V5aiApiKeyMapper gateway;
//
//    public MyBatisAgentApiKeyStore(V5aiApiKeyMapper gateway) {
//        this.gateway = gateway;
//    }
//
//    @Override
//    public String findSecretHash(String agentKey) {
//        var entity = gateway.selectByAgentKey(agentKey);
//        return entity == null || !Boolean.TRUE.equals(entity.getEnabled()) ? null : entity.getSecretHash();
//    }
//
//    @Override
//    public void upsertSecret(String agentKey, String secretHash) {
//        var entity = new V5aiApiKey();
//        entity.setAgentKey(agentKey);
//        entity.setSecretHash(secretHash);
//        entity.setEnabled(true);
//        gateway.upsert(entity);
//    }
//
//    @Override
//    public List<ApiKeyRecord> list() {
//        return gateway.selectAll().stream()
//                .map(entity -> new ApiKeyRecord(entity.getAgentKey(), Boolean.TRUE.equals(entity.getEnabled())))
//                .toList();
//    }
//
//    @Override
//    public void delete(String agentKey) {
//        gateway.deleteByAgentKey(agentKey);
//    }
//}
