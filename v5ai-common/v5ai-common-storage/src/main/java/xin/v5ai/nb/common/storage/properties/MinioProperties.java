package xin.v5ai.nb.common.storage.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "v5ai.minio")
public class MinioProperties {
    /**
     * minio endpoint
     */
    private String endpoint = "http://localhost:9000";
    /**
     * minio accessKey
     */
    private String accessKey = "minioadmin";
    /**
     * minio secretKey
     */
    private String secretKey = "minioadmin";
    /**
     * minio bucket
     */
    private String bucket = "v5ai";
}
