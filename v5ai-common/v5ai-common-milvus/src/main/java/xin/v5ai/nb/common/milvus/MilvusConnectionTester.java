package xin.v5ai.nb.common.milvus;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import xin.v5ai.nb.common.milvus.domain.ConnectionTestResult;
import xin.v5ai.nb.common.milvus.domain.MilvusVectorConfigDO;

/**
 * Milvus 连接测试：建立 gRPC 连接后执行 {@code listCollections}（一次受鉴权保护的服务端调用）。
 */
public class MilvusConnectionTester {

    private final MilvusClientFactory factory = new MilvusClientFactory();

    public ConnectionTestResult test(MilvusVectorConfigDO config) {
        MilvusClientV2 client = null;
        try {
            client = factory.create(config);
            ListCollectionsResp resp = client.listCollections();
            int count = resp.getCollectionNames() == null ? 0 : resp.getCollectionNames().size();
            return ConnectionTestResult.success("连接成功（已存在 " + count + " 个 collection）");
        } catch (Exception e) {
            String target = config.getHost() + ":" + config.getPort();
            return ConnectionTestResult.failure("连接 Milvus(" + target + ") 失败: " + messageOf(e));
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
