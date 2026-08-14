package xin.v5ai.nb.application;

public record CreateApplicationCommand(String appKey, String name, String description, Long modelId) {
    public CreateApplicationCommand(String appKey, String name, Long modelId) {
        this(appKey, name, null, modelId);
    }
}
