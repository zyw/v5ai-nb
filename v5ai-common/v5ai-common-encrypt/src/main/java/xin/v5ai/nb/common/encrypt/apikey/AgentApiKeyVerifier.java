package xin.v5ai.nb.common.encrypt.apikey;

import xin.v5ai.nb.common.core.domain.model.Tuple2;

public interface AgentApiKeyVerifier {
    /**
     * 验证ApiKey
     * @param apiKey 接收的ApiKey
     * @param secretHash 数据库中的密文
     * @return 是否验证通过
     */
    boolean verify(String apiKey,String secretHash);

    /**
     * 生成ApiKey
     * @return first: 生成的ApiKey second: 生成的ApiKey的密文
     */
    Tuple2<String, String> generateApiKey();
}
