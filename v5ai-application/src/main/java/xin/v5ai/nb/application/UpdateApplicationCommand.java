package xin.v5ai.nb.application;

public record UpdateApplicationCommand(String appKey, String name, String description, Long modelId) {
}
