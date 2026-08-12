package xin.v5ai.nb.application;

public record Application(String appKey, String name, ApplicationStatus status, Long modelId, Long publishedVersion) { }
