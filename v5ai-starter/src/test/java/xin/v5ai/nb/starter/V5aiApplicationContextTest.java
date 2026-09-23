package xin.v5ai.nb.starter;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import xin.v5ai.nb.agent.controller.AgentController;
import xin.v5ai.nb.agent.service.IAgentService;
import xin.v5ai.nb.runtime.controller.ChatController;
import xin.v5ai.nb.model.controller.V5aiModelController;
import xin.v5ai.nb.model.controller.V5aiModelProviderController;
import xin.v5ai.nb.platform.controller.auth.AuthController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v5ai;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:v5ai;DB_CLOSE_DELAY=-1",
        "spring.datasource.dynamic.datasource.master.driver-class-name=org.h2.Driver",
        "spring.datasource.dynamic.datasource.master.username=sa",
        "spring.datasource.dynamic.datasource.master.password=",
        "spring.flyway.enabled=false",
        "v5ai.redisson.address=redis://localhost:6379",
        "v5ai.minio.endpoint=http://localhost:9000",
        "v5ai.minio.access-key=minio",
        "v5ai.minio.secret-key=minio123456",
        "v5ai.minio.bucket=v5ai"
})
class V5aiApplicationContextTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private MinioClient minioClient;

    @Test
    void applicationContextLoads() {
    }

    @Test
    void phase0InfrastructurePropertiesAreConfigured() {
        // 方言占位符已解析：未设 V5AI_DB_DIALECT 时默认 postgresql，locations 指向 common + postgresql 两目录
        assertThat(environment.getProperty("v5ai.db.dialect"))
                .isEqualTo("postgresql");
        assertThat(environment.getProperty("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration/common,classpath:db/migration/postgresql");
        assertThat(environment.getProperty("v5ai.redisson.address"))
                .isEqualTo("redis://localhost:6379");
        assertThat(environment.getProperty("v5ai.minio.bucket"))
                .isEqualTo("v5ai");
        assertThat(environment.getProperty("spring.servlet.multipart.max-file-size"))
                .isEqualTo("50MB");
    }

    @Test
    void phase1BeansAreRegisteredByComponentScan() {
        assertThat(applicationContext.getBean(AuthController.class)).isNotNull();
        assertThat(applicationContext.getBean(V5aiModelController.class)).isNotNull();
        assertThat(applicationContext.getBean(V5aiModelProviderController.class)).isNotNull();
        assertThat(applicationContext.getBean(AgentController.class)).isNotNull();
        assertThat(applicationContext.getBean(ChatController.class)).isNotNull();
        assertThat(applicationContext.getBean(IAgentService.class)).isNotNull();
    }
}
