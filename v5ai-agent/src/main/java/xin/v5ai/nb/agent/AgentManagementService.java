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
                AgentStatus.DRAFT, command.modelId(), null, command.systemPrompt(),
                command.avatar(), command.greeting(), command.presetQuestions(),
                Boolean.TRUE.equals(command.memoryEnabled()),
                Boolean.TRUE.equals(command.mcpEnabled()),
                Boolean.TRUE.equals(command.skillEnabled()),
                Boolean.TRUE.equals(command.webSearchEnabled()),
                Boolean.TRUE.equals(command.ragEnabled()));
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
                command.systemPrompt() == null ? existing.systemPrompt() : command.systemPrompt(),
                command.avatar() == null ? existing.avatar() : command.avatar(),
                command.greeting() == null ? existing.greeting() : command.greeting(),
                command.presetQuestions() == null ? existing.presetQuestions() : command.presetQuestions(),
                command.memoryEnabled() == null ? existing.memoryEnabled() : command.memoryEnabled(),
                command.mcpEnabled() == null ? existing.mcpEnabled() : command.mcpEnabled(),
                command.skillEnabled() == null ? existing.skillEnabled() : command.skillEnabled(),
                command.webSearchEnabled() == null ? existing.webSearchEnabled() : command.webSearchEnabled(),
                command.ragEnabled() == null ? existing.ragEnabled() : command.ragEnabled());
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
                {"agentKey":"%s","name":"%s","description":"%s","modelId":%d,"systemPrompt":"%s","avatar":"%s","greeting":"%s","presetQuestions":"%s","memoryEnabled":%b,"mcpEnabled":%b,"skillEnabled":%b,"webSearchEnabled":%b,"ragEnabled":%b}
                """.formatted(
                        jsonEscape(agent.agentKey()),
                        jsonEscape(agent.name()),
                        jsonEscape(agent.description()),
                        agent.modelId(),
                        jsonEscape(agent.systemPrompt()),
                        jsonEscape(agent.avatar()),
                        jsonEscape(agent.greeting()),
                        jsonEscape(agent.presetQuestions()),
                        agent.memoryEnabled(),
                        agent.mcpEnabled(),
                        agent.skillEnabled(),
                        agent.webSearchEnabled(),
                        agent.ragEnabled()).trim();
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
