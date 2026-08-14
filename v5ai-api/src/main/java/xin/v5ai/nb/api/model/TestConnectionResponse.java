package xin.v5ai.nb.api.model;

public record TestConnectionResponse(Long modelId, boolean ok, String message) {
    static TestConnectionResponse ok(Long modelId) {
        return new TestConnectionResponse(modelId, true, "connection ok");
    }

    static TestConnectionResponse failed(Long modelId, String message) {
        return new TestConnectionResponse(modelId, false, message);
    }
}
