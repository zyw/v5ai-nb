package xin.v5ai.nb.runtime;

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
            messageRepository.save(new Message(request.conversationId(), request.appKey(), MessageRole.USER, request.query()));
            var answer = new StringBuilder();
            var runId = new String[1];
            return delegate.stream(request)
                    .doOnNext(event -> {
                        if (event.type() == RuntimeEventType.RUN_STARTED) {
                            runId[0] = event.runId();
                            runRecordRepository.start(event.runId(), request);
                        }
                        if (event.type() == RuntimeEventType.TEXT_DELTA) {
                            answer.append(event.payload());
                        }
                        if (event.type() == RuntimeEventType.RUN_COMPLETED) {
                            messageRepository.save(new Message(request.conversationId(), request.appKey(), MessageRole.ASSISTANT, answer.toString()));
                            runRecordRepository.complete(event.runId());
                        }
                    })
                    .doOnComplete(() -> {
                        if (runId[0] == null) {
                            messageRepository.save(new Message(request.conversationId(), request.appKey(), MessageRole.ASSISTANT, answer.toString()));
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
}
