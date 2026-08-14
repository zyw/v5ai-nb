package xin.v5ai.nb.api.application;

import xin.v5ai.nb.application.ApplicationVersion;

public record ApplicationVersionResponse(String appKey, long version, String snapshotJson, String description) {
    static ApplicationVersionResponse from(ApplicationVersion version) {
        return new ApplicationVersionResponse(version.appKey(), version.version(),
                version.snapshotJson(), version.description());
    }
}
