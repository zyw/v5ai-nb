package xin.v5ai.nb.api.application;

public record CreateApplicationRequest(String appKey, String name, String description, Long modelId) {
}
