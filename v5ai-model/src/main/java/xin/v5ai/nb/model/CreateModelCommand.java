package xin.v5ai.nb.model;

public record CreateModelCommand(String providerKey, String modelKey, ModelType type, String credentials) { }
