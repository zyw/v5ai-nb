package xin.v5ai.nb.common.elasticsearch;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import xin.v5ai.nb.common.elasticsearch.domain.ElasticsearchVectorConfigDO;

import javax.net.ssl.SSLContext;

/**
 * 按存储实例 config 构建低层 {@link RestClient}：scheme/host/port、Basic 认证、自签名证书关闭校验。
 */
public class ElasticsearchClientFactory {

    public RestClient create(ElasticsearchVectorConfigDO config) {
        String scheme = config.getScheme() == null || config.getScheme().isBlank() ? "http" : config.getScheme();
        HttpHost httpHost = new HttpHost(config.getHost(), config.getPort(), scheme);
        return RestClient.builder(httpHost)
                .setHttpClientConfigCallback(http -> {
                    if (config.getUsername() != null && !config.getUsername().isBlank()) {
                        var provider = new BasicCredentialsProvider();
                        provider.setCredentials(AuthScope.ANY,
                                new UsernamePasswordCredentials(config.getUsername(), config.getPassword()));
                        http.setDefaultCredentialsProvider(provider);
                    }
                    if (config.isSslVerificationDisabled()) {
                        http.setSSLContext(trustAllSslContext()).setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    }
                    return http;
                })
                .build();
    }

    private static SSLContext trustAllSslContext() {
        try {
            return SSLContextBuilder.create().loadTrustMaterial(null, (chain, authType) -> true).build();
        } catch (Exception e) {
            throw new IllegalStateException("构建信任所有证书的 SSL 上下文失败", e);
        }
    }
}
