package xin.v5ai.nb.common.elasticsearch;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import xin.v5ai.nb.common.elasticsearch.domain.ConnectionTestResult;
import xin.v5ai.nb.common.elasticsearch.domain.ElasticsearchVectorConfigDO;

/**
 * Elasticsearch 连接测试：对 {@code GET /} 发起一次受鉴权保护的服务端调用，2xx 即判定连通。
 */
public class ElasticsearchConnectionTester {

    private final ElasticsearchClientFactory factory = new ElasticsearchClientFactory();

    public ConnectionTestResult test(ElasticsearchVectorConfigDO config) {
        try (RestClient client = factory.create(config)) {
            Response response = client.performRequest(new Request("GET", "/"));
            int status = response.getStatusLine().getStatusCode();
            if (status < 300) {
                return ConnectionTestResult.success("连接成功");
            }
            return ConnectionTestResult.failure("Elasticsearch 返回状态码 " + status);
        } catch (Exception e) {
            String target = config.getHost() + ":" + config.getPort();
            return ConnectionTestResult.failure("连接 Elasticsearch(" + target + ") 失败: " + messageOf(e));
        }
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
