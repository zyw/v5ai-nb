package xin.v5ai.nb.common.milvus;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import xin.v5ai.nb.common.milvus.domain.MilvusVectorConfigDO;

/**
 * 按存储实例 config 构建 {@link MilvusClientV2}：uri、token、database 与连接超时。
 */
public class MilvusClientFactory {

    public MilvusClientV2 create(MilvusVectorConfigDO config) {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri("http://" + config.getHost() + ":" + config.getPort())
                .connectTimeoutMs(5000);
        if (config.getToken() != null && !config.getToken().isBlank()) {
            builder.token(config.getToken());
        }
        if (config.getDatabase() != null && !config.getDatabase().isBlank()) {
            builder.dbName(config.getDatabase());
        }
        return new MilvusClientV2(builder.build());
    }
}
