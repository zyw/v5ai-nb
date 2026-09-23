package xin.v5ai.nb.starter;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.redisson.api.RedissonClient;
import io.minio.MinioClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that Flyway migrations actually run under Spring Boot 4.
 * Boot 4 moved Flyway auto-configuration out of spring-boot-autoconfigure into the
 * dedicated {@code spring-boot-starter-flyway} artifact; without it the
 * {@code spring.flyway.*} properties are inert and no table is created.
 * Uses a test-only migration against H2 because the real V1/V2 migrations target
 * PostgreSQL + pgvector.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flywaytest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:flywaytest;DB_CLOSE_DELAY=-1",
        "spring.datasource.dynamic.datasource.master.driver-class-name=org.h2.Driver",
        "spring.datasource.dynamic.datasource.master.username=sa",
        "spring.datasource.dynamic.datasource.master.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/testmigration",
        "spring.flyway.table=v5ai_flyway_schema_history",
        "v5ai.redisson.address=redis://localhost:6379",
        "v5ai.minio.endpoint=http://localhost:9000",
        "v5ai.minio.access-key=minio",
        "v5ai.minio.secret-key=minio123456",
        "v5ai.minio.bucket=v5ai"
})
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private MinioClient minioClient;

    @Test
    void flywayAutoConfigurationRunsMigrations() {
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .containsExactly("1");
    }
}
