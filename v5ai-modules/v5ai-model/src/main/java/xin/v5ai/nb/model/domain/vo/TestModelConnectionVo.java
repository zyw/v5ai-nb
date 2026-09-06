package xin.v5ai.nb.model.domain.vo;

public record TestModelConnectionVo(Long modelId, boolean ok, String message) {
    public static TestModelConnectionVo ok(Long modelId) {
        return new TestModelConnectionVo(modelId, true, "connection ok");
    }

    public static TestModelConnectionVo ok(Long modelId, String message) {
        return new TestModelConnectionVo(modelId, true, message);
    }

    public static TestModelConnectionVo failed(Long modelId, String message) {
        return new TestModelConnectionVo(modelId, false, message);
    }
}
