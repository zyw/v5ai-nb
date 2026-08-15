package xin.v5ai.nb.infrastructure.mcp;

import java.util.List;

public interface ApplicationMcpBindingGateway {
    void insertBinding(ApplicationMcpBindingEntity entity);

    void deleteByAppKey(String appKey);

    List<ApplicationMcpBindingEntity> selectByAppKey(String appKey);
}
