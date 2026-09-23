package xin.v5ai.nb.model.api;

import xin.v5ai.nb.model.api.domain.ModelUsageDTO;

public interface ModelUsageService {

    boolean record(ModelUsageDTO usage);

}
