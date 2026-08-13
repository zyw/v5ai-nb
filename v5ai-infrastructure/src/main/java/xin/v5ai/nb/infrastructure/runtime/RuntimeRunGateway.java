package xin.v5ai.nb.infrastructure.runtime;

public interface RuntimeRunGateway {
    void insertRun(RuntimeRunEntity entity);

    void markCompleted(String runId);
}
