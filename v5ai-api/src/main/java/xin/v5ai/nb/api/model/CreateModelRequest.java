package xin.v5ai.nb.api.model;

import xin.v5ai.nb.model.ModelType;

public record CreateModelRequest(String providerKey, String modelKey, ModelType type, String credentials) {
}
