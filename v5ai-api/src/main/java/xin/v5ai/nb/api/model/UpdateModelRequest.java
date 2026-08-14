package xin.v5ai.nb.api.model;

import xin.v5ai.nb.model.ModelType;

public record UpdateModelRequest(String modelKey, ModelType type, String credentials, Boolean enabled) {
}
