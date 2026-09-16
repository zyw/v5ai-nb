//package xin.v5ai.nb.common.agentscope.core.repository;
//
//import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
//import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentVersionDTO;
//
//import java.util.List;
//
//public interface AgentRepository {
//
//    AgentDTO findByAgentKey(String agentKey);
//
//    void save(AgentDTO agent);
//
//    void update(AgentDTO agent);
//
//    void disable(String agentKey);
//
//    /** 删除 AgentDTO 及其全部关联数据（版本/绑定/API Key/会话/运行/用量等）。 */
//    void delete(String agentKey);
//
//    List<AgentDTO> list();
//
//    long nextVersion(String agentKey);
//
//    void saveVersion(AgentVersionDTO version);
//
//    List<AgentVersionDTO> listVersions(String agentKey);
//
//    void publish(String agentKey, long version);
//}
