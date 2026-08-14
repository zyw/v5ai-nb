package xin.v5ai.nb.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AgentScopeHarnessExecutor implements AgentTextExecutor {
    private final Model model;
    private final AgentStateStore stateStore;
    private final Path workspace;

    public AgentScopeHarnessExecutor(Model model) {
        this(model, new JsonFileAgentStateStore(Path.of(".agentscope/state")), Path.of(".agentscope/workspace"));
    }

    public AgentScopeHarnessExecutor(Model model, AgentStateStore stateStore) {
        this(model, stateStore, Path.of(".agentscope/workspace"));
    }

    public AgentScopeHarnessExecutor(Model model, AgentStateStore stateStore, Path workspace) {
        this.model = model;
        this.stateStore = stateStore;
        this.workspace = workspace;
    }

    @Override
    public Flux<AgentTextEvent> streamText(Application application, AgentRunRequest request) {
        ensureWorkspaceExists();
        HarnessAgent agent = HarnessAgent.builder()
                .name(application.name())
                .description("v5ai AgentScope Java minimum runtime")
                .model(model)
                .stateStore(stateStore)
                .workspace(workspace)
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableSessionPersistence()
                .disableWorkspaceContext()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .build();
        return Flux.concat(
                        Flux.just(new AgentTextEvent.ModelCall(model.getModelName())),
                        agent.call(request.query(), RuntimeContext.empty())
                                .map(message -> (AgentTextEvent) new AgentTextEvent.Text(message.getTextContent()))
                                .flux())
                .doFinally(signal -> agent.close());
    }

    private void ensureWorkspaceExists() {
        try {
            Files.createDirectories(workspace);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create AgentScope workspace: " + workspace, exception);
        }
    }
}
