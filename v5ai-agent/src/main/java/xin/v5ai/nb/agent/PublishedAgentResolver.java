package xin.v5ai.nb.agent;

public class PublishedAgentResolver {
    private final AgentRepository repository;
    public PublishedAgentResolver(AgentRepository repository) { this.repository = repository; }
    public Agent resolve(String agentKey) {
        var agent = repository.findByAgentKey(agentKey);
        if (agent == null || agent.status() != AgentStatus.PUBLISHED) {
            throw new AgentPublishException("agent is not published");
        }
        return agent;
    }
}
