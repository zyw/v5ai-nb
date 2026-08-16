package xin.v5ai.nb.agent;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;

@Service
public class AgentManagementService {
    private final AgentRepository repository;
    private final Predicate<Long> modelEnabled;

    public AgentManagementService(AgentRepository repository, Predicate<Long> modelEnabled) {
        this.repository = repository;
        this.modelEnabled = modelEnabled;
    }

    public Agent createAgent(CreateAgentCommand command) {
        if (command.agentKey() == null || command.agentKey().isBlank()
                || command.name() == null || command.name().isBlank()
                || command.modelId() == null) {
            throw new IllegalArgumentException("agent key, name and model are required");
        }
        if (repository.findByAgentKey(command.agentKey()) != null) {
            throw new IllegalArgumentException("agent already exists: " + command.agentKey());
        }
        var agent = new Agent(command.agentKey(), command.name(), command.description(),
                AgentStatus.DRAFT, command.modelId(), null, command.systemPrompt());
        repository.save(agent);
        return agent;
    }

    public List<Agent> listAgents() {
        return repository.list();
    }

    public Agent updateAgent(UpdateAgentCommand command) {
        var existing = repository.findByAgentKey(command.agentKey());
        if (existing == null) {
            throw new IllegalArgumentException("agent does not exist: " + command.agentKey());
        }
        var agent = new Agent(
                existing.agentKey(),
                command.name() == null || command.name().isBlank() ? existing.name() : command.name(),
                command.description() == null ? existing.description() : command.description(),
                existing.status(),
                command.modelId() == null ? existing.modelId() : command.modelId(),
                existing.publishedVersion(),
                command.systemPrompt() == null ? existing.systemPrompt() : command.systemPrompt());
        repository.update(agent);
        return agent;
    }

    public void disable(String agentKey) {
        if (repository.findByAgentKey(agentKey) == null) {
            throw new IllegalArgumentException("agent does not exist: " + agentKey);
        }
        repository.disable(agentKey);
    }

    public List<AgentVersion> listVersions(String agentKey) {
        if (repository.findByAgentKey(agentKey) == null) {
            throw new IllegalArgumentException("agent does not exist: " + agentKey);
        }
        return repository.listVersions(agentKey);
    }

    public void publish(String agentKey) {
        publish(new PublishAgentCommand(agentKey, ""));
    }

    public void publish(PublishAgentCommand command) {
        String agentKey = command.agentKey();
        var agent = repository.findByAgentKey(agentKey);
        if (agent == null || agent.modelId() == null || !modelEnabled.test(agent.modelId())) {
            throw new AgentPublishException("agent requires an enabled model before publishing");
        }
        long version = repository.nextVersion(agentKey);
        repository.saveVersion(new AgentVersion(agentKey, version, snapshot(agent), command.description()));
        repository.publish(agentKey, version);
    }

    private String snapshot(Agent agent) {
        return """
                {"agentKey":"%s","name":"%s","description":"%s","modelId":%d,"systemPrompt":"%s"}
                """.formatted(
                        agent.agentKey(),
                        agent.name(),
                        agent.description() == null ? "" : agent.description(),
                        agent.modelId(),
                        agent.systemPrompt() == null ? "" : agent.systemPrompt().replace("\"", "\\\"")).trim();
    }
}
