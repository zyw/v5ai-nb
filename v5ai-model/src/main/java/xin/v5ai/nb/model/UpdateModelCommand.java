package xin.v5ai.nb.model;

public record UpdateModelCommand(Long id, String modelKey, ModelType type, String credentials, boolean enabled) {
}
