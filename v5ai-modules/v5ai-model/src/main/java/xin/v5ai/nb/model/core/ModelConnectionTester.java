package xin.v5ai.nb.model.core;

import xin.v5ai.nb.model.domain.vo.TestModelConnectionVo;

/**
 * Port for testing a configured model connection. Implementations may verify the
 * endpoint and credentials by issuing a minimal request against the model provider.
 */
public interface ModelConnectionTester {
    TestModelConnectionVo test(Long modelId);
}
