package xin.v5ai.nb.common.storage;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.common.storage.properties.MinioProperties;

import java.net.Proxy;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {
    @Bean
    @ConditionalOnMissingBean
    MinioClient minioClient(MinioProperties properties) {
        return createClient(properties);
    }

    static MinioClient createClient(MinioProperties properties) {
        // 内部对象存储直连：显式 Proxy.NO_PROXY 绕过 JVM 系统代理（如 Clash/Surge）。
        // 否则 OkHttp 默认走 ProxySelector -> 系统代理，代理回源内网 MinIO 失败时
        // 返回 502 / Non-XML response。超时沿用 MinIO SDK 默认值（连接/写入 10 分钟、读取 30 秒）。
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .readTimeout(30, TimeUnit.SECONDS)
                .proxy(Proxy.NO_PROXY)
                .build();
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .httpClient(httpClient)
                .build();
    }
}
