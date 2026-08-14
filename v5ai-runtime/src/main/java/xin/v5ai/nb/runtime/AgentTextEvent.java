package xin.v5ai.nb.runtime;

/**
 * Items produced by an {@link AgentTextExecutor}. Text deltas are streamed to the client;
 * model-call markers are converted into platform {@code MODEL_CALL} events.
 */
public sealed interface AgentTextEvent permits AgentTextEvent.Text, AgentTextEvent.ModelCall {

    record Text(String text) implements AgentTextEvent {
    }

    record ModelCall(String modelName) implements AgentTextEvent {
    }
}
