package xin.v5ai.nb.runtime;

import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PersistingAgentRuntime implements AgentRuntime {
    private final AgentRuntime delegate;
    private final MessageRepository messageRepository;
    private final RunRecordRepository runRecordRepository;

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository
    ) {
        this.delegate = delegate;
        this.messageRepository = messageRepository;
        this.runRecordRepository = runRecordRepository;
    }

    @Override
    public Flux<RuntimeEvent> stream(AgentRunRequest request) {
        return Flux.defer(() -> {
            var normalizedRequest = normalize(request);
            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.USER, normalizedRequest.query()));
            var answer = new StringBuilder();
            var runId = new String[1];
            return delegate.stream(normalizedRequest)
                    .doOnNext(event -> {
                        if (event.type() == RuntimeEventType.RUN_STARTED) {
                            runId[0] = event.runId();
                            runRecordRepository.start(event.runId(), normalizedRequest);
                        }
                        if (event.type() == RuntimeEventType.TEXT_DELTA) {
                            answer.append(event.payload());
                        }
                        if (event.type() == RuntimeEventType.RUN_COMPLETED) {
                            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.ASSISTANT, answer.toString()));
                            runRecordRepository.complete(event.runId());
                        }
                    })
                    .doOnComplete(() -> {
                        if (runId[0] == null) {
                            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.ASSISTANT, answer.toString()));
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

    private AgentRunRequest normalize(AgentRunRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return request;
        }
        return new AgentRunRequest(request.appKey(), UUID.randomUUID().toString(), request.query());
    }
}
