package xin.v5ai.nb.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;

import java.util.ArrayList;
import java.util.List;

/**
 * Streams deltas directly from an AgentScope {@link Model}. Supports conversation
 * history replay and RAG context injection. Emits a {@code MODEL_CALL} marker before
 * the model request so the runtime can surface it as a platform event.
 */
public class ModelStreamTextExecutor implements AgentTextExecutor {
    private final Model model;

    public ModelStreamTextExecutor(Model model) {
        this.model = model;
    }

    @Override
    public Flux<AgentTextEvent> streamText(Application application, AgentRunRequest request) {
        List<Msg> messages = buildMessages(application, request);
        return Flux.concat(
                Flux.just(new AgentTextEvent.ModelCall(model.getModelName())),
                model.stream(messages, List.of(), generateOptions())
                        .map(ModelStreamTextExecutor::extractText)
                        .filter(text -> text != null && !text.isEmpty())
                        .map(AgentTextEvent.Text::new));
    }

    private static List<Msg> buildMessages(Application application, AgentRunRequest request) {
        List<Msg> messages = new ArrayList<>();
        var system = new StringBuilder();
        system.append("You are the assistant for application \"").append(application.name()).append("\".");
        if (application.description() != null && !application.description().isBlank()) {
            system.append(' ').append(application.description());
        }
        if (request.ragContext() != null && !request.ragContext().isBlank()) {
            system.append("\n\nUse the following knowledge base context when relevant:\n")
                    .append(request.ragContext());
        }
        messages.add(Msg.builder().role(MsgRole.SYSTEM).textContent(system.toString()).build());

        for (Message history : request.history()) {
            var role = history.role() == MessageRole.USER ? MsgRole.USER : MsgRole.ASSISTANT;
            messages.add(Msg.builder().role(role).textContent(history.content()).build());
        }
        messages.add(Msg.builder().role(MsgRole.USER).textContent(request.query()).build());
        return messages;
    }

    private static GenerateOptions generateOptions() {
        return GenerateOptions.builder().stream(true).build();
    }

    private static String extractText(ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return null;
        }
        var text = new StringBuilder();
        for (var block : response.getContent()) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }
}
