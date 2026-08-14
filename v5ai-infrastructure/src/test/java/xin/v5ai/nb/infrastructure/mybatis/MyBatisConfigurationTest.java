package xin.v5ai.nb.infrastructure.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisConfigurationTest {

    @Test
    void globalConfigUsesDatabaseSequencesInsteadOfSnowflake() {
        var globalConfig = MyBatisConfiguration.buildGlobalConfig();

        // IdType.AUTO delegates id generation to the DB (BIGSERIAL), producing small ids
        // that the JS frontend can represent exactly. ASSIGN_ID (MyBatis-Plus default)
        // would generate 19-digit Snowflake ids that corrupt in JavaScript.
        assertThat(globalConfig.getDbConfig().getIdType()).isEqualTo(IdType.AUTO);
    }
}
