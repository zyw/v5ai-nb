package xin.v5ai.nb.starter;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.minio.MinioClient;
import xin.v5ai.nb.infrastructure.model.ModelProviderEntity;
import xin.v5ai.nb.infrastructure.model.ModelProviderMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the Snowflake-id bug: with the MyBatis-Plus default
 * {@code ASSIGN_ID} strategy, inserts bypass the DB {@code BIGSERIAL} sequence and
 * produce 19-digit ids that the JS frontend corrupts (beyond
 * {@code Number.MAX_SAFE_INTEGER}), breaking id-based lookups like
 * {@code /admin/knowledge-bases/{id}/documents}. With {@code IdType.AUTO} the DB
 * generates small ids. Uses a test-only table because the real migrations target
 * PostgreSQL + pgvector.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:idtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/idtest",
        "spring.flyway.table=v5ai_flyway_schema_history",
        "v5ai.redisson.address=redis://localhost:6379",
        "v5ai.minio.endpoint=http://localhost:9000",
        "v5ai.minio.access-key=minio",
        "v5ai.minio.secret-key=minio123456",
        "v5ai.minio.bucket=v5ai"
})
class IdGenerationTest {

    @Autowired
    private ModelProviderMapper modelProviderMapper;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private MinioClient minioClient;

    @Test
    void insertedEntityReceivesSmallDatabaseGeneratedId() {
        var entity = new ModelProviderEntity();
        entity.setProviderKey("openai");
        entity.setName("OpenAI");
        entity.setProviderType("OPENAI");
        entity.setEnabled(true);

        modelProviderMapper.insert(entity);

        assertThat(entity.getId())
                .as("id must come from the DB sequence, not a 19-digit Snowflake")
                .isNotNull()
                .isLessThan(1_000_000L);
        assertThat(modelProviderMapper.selectProviderById(entity.getId()).getProviderKey()).isEqualTo("openai");
    }
}
