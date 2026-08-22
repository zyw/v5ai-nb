//package xin.v5ai.nb.agent;
//
//import java.util.List;
//
//public interface AgentRepository {
//    Agent findByAgentKey(String agentKey);
//
//    void save(Agent agent);
//
//    void update(Agent agent);
//
//    void disable(String agentKey);
//
//    /** 删除 Agent 及其全部关联数据（版本/绑定/API Key/会话/运行/用量等）。 */
//    void delete(String agentKey);
//
//    List<Agent> list();
//
//    long nextVersion(String agentKey);
//
//    void saveVersion(AgentVersion version);
//
//    List<AgentVersion> listVersions(String agentKey);
//
//    void publish(String agentKey, long version);
//}
