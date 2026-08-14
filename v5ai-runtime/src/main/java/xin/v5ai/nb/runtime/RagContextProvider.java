package xin.v5ai.nb.runtime;

import reactor.core.publisher.Mono;
import xin.v5ai.nb.application.Application;

/**
 * Builds the retrieval context (knowledge base references) for a run request.
 * Implementations may return an empty string when no knowledge is bound or no
 * retrieval is configured; the runtime then proceeds without RAG context.
 */
public interface RagContextProvider {
    Mono<String> buildContext(Application application, AgentRunRequest request);
}
