package xin.v5ai.nb.api.application;

import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationStatus;

public record ApplicationResponse(String appKey, String name, ApplicationStatus status, Long modelId, Long publishedVersion) {
    static ApplicationResponse from(Application application) {
        return new ApplicationResponse(application.appKey(), application.name(), application.status(),
                application.modelId(), application.publishedVersion());
    }
}
