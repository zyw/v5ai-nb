package xin.v5ai.nb.infrastructure.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinioConfigurationTest {
    @Test
    void createsMinioClientFromProperties() {
        var properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("minio");
        properties.setSecretKey("minio123456");
        properties.setBucket("v5ai");

        MinioClient client = MinioConfiguration.createClient(properties);

        assertThat(client).isNotNull();
        assertThat(properties.getBucket()).isEqualTo("v5ai");
    }
}
