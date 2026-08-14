package xin.v5ai.nb.runtime;

public interface RunRecordRepository {
    void start(String runId, AgentRunRequest request);

    void complete(String runId);

    void fail(String runId, String message);
}
