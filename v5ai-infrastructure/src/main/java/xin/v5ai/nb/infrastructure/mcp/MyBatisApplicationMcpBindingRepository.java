package xin.v5ai.nb.infrastructure.mcp;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.mcp.ApplicationMcpBindingRepository;

import java.util.List;

@Repository
public class MyBatisApplicationMcpBindingRepository implements ApplicationMcpBindingRepository {
    private final ApplicationMcpBindingGateway gateway;

    public MyBatisApplicationMcpBindingRepository(ApplicationMcpBindingGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(String appKey, List<Long> mcpServerIds) {
        gateway.deleteByAppKey(appKey);
        for (Long serverId : mcpServerIds) {
            var entity = new ApplicationMcpBindingEntity();
            entity.setAppKey(appKey);
            entity.setMcpServerId(serverId);
            gateway.insertBinding(entity);
        }
    }

    @Override
    public List<Long> findByAppKey(String appKey) {
        return gateway.selectByAppKey(appKey).stream()
                .map(ApplicationMcpBindingEntity::getMcpServerId)
                .toList();
    }

    @Override
    public void delete(String appKey) {
        gateway.deleteByAppKey(appKey);
    }
}
