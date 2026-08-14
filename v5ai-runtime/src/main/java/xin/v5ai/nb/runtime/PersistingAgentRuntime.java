package xin.v5ai.nb.runtime;

import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PersistingAgentRuntime implements AgentRuntime {
    private final AgentRuntime delegate;
    private final MessageRepository messageRepository;
    private final RunRecordRepository runRecordRepository;
    private final ConversationRepository conversationRepository;
    private final RunEventRepository runEventRepository;
    private final AgentStateRepository agentStateRepository;

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository
    ) {
        this(delegate, messageRepository, runRecordRepository, conversation -> {}, event -> {}, null);
    }

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository,
            ConversationRepository conversationRepository,
            RunEventRepository runEventRepository
    ) {
        this(delegate, messageRepository, runRecordRepository, conversationRepository, runEventRepository, null);
    }

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository,
            ConversationRepository conversationRepository,
            RunEventRepository runEventRepository,
            AgentStateRepository agentStateRepository
    ) {
        this.delegate = delegate;
        this.messageRepository = messageRepository;
        this.runRecordRepository = runRecordRepository;
        this.conversationRepository = conversationRepository;
        this.runEventRepository = runEventRepository;
        this.agentStateRepository = agentStateRepository;
    }

    @Override
    public Flux<RuntimeEvent> stream(AgentRunRequest request) {
        return Flux.defer(() -> {
            var normalizedRequest = normalize(request);
            conversationRepository.ensureConversation(new Conversation(normalizedRequest.conversationId(), normalizedRequest.appKey()));
            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.USER, normalizedRequest.query()));
            var answer = new StringBuilder();
            var runId = new String[1];
            return delegate.stream(normalizedRequest)
                    .doOnNext(event -> {
                        runEventRepository.save(event);
                        if (event.type() == RuntimeEventType.RUN_STARTED) {
                            runId[0] = event.runId();
                            runRecordRepository.start(event.runId(), normalizedRequest);
                        }
                        if (event.type() == RuntimeEventType.TEXT_DELTA) {
                            answer.append(event.payload());
                        }
                        if (event.type() == RuntimeEventType.MESSAGE_COMPLETED) {
                            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                        }
                        if (event.type() == RuntimeEventType.RUN_COMPLETED) {
                            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                            runRecordRepository.complete(event.runId());
                        }
                        if (event.type() == RuntimeEventType.RUN_FAILED) {
                            runRecordRepository.fail(event.runId(), event.payload());
                        }
                    })
                    .onErrorResume(exception -> {
                        var failedRunId = runId[0] == null ? UUID.randomUUID().toString() : runId[0];
                        runEventRepository.save(RuntimeEvent.failed(failedRunId, exception.getMessage()));
                        runRecordRepository.fail(failedRunId, exception.getMessage());
                        return Flux.just(RuntimeEvent.failed(failedRunId, exception.getMessage()));
                    })
                    .doOnComplete(() -> {
                        if (runId[0] == null) {
                            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                        }
                    });
        });
    }

    @Override
    public Mono<AgentRunResult> call(AgentRunRequest request) {
        return stream(request)
                .filter(event -> event.type() == RuntimeEventType.TEXT_DELTA)
                .map(RuntimeEvent::payload)
                .collectList()
                .map(parts -> new AgentRunResult("", String.join("", parts)));
    }

    private void saveAgentState(AgentRunRequest request, String answer) {
        if (agentStateRepository == null) {
            return;
        }
        agentStateRepository.save(request.conversationId(),
                """
                {"appKey":"%s","lastAnswer":"%s"}
                """.formatted(request.appKey(), answer == null ? "" : answer.replace("\"", "\\\"")).trim());
    }

    private AgentRunRequest normalize(AgentRunRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return request;
        }
        return new AgentRunRequest(request.appKey(), UUID.randomUUID().toString(), request.query(),
                request.ragContext(), request.history());
    }
}
