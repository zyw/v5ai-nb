package xin.v5ai.nb.infrastructure.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Explicit MyBatis-Plus wiring. The Boot 3-era MybatisPlusAutoConfiguration is skipped
 * under Spring Boot 4 (its DataSource single-candidate condition is evaluated before the
 * DataSource bean definition is registered), so the SqlSessionFactory/SqlSessionTemplate
 * beans are declared here directly. Note that skipping the auto-configuration also means
 * the {@code mybatis-plus.*} properties in application.yml are inert — all settings are
 * configured programmatically here.
 */
@Configuration
public class MyBatisConfiguration {

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setGlobalConfig(buildGlobalConfig());
        return factory.getObject();
    }

    @Bean
    SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * MyBatis-Plus defaults to {@link IdType#ASSIGN_ID} (Snowflake). That generates
     * 19-digit ids which the Vue/JS frontend cannot represent exactly (beyond
     * {@code Number.MAX_SAFE_INTEGER}), corrupting ids in URLs and breaking lookups.
     * The schema uses {@code BIGSERIAL} sequences, so {@link IdType#AUTO} is the
     * correct strategy: PostgreSQL generates small, JS-safe ids.
     */
    static GlobalConfig buildGlobalConfig() {
        var globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        var dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setIdType(IdType.AUTO);
        globalConfig.setDbConfig(dbConfig);
        return globalConfig;
    }
}
