package xin.v5ai.nb.infrastructure.mybatis;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Explicit MyBatis-Plus wiring. The Boot 3-era MybatisPlusAutoConfiguration is skipped
 * under Spring Boot 4 (its DataSource single-candidate condition is evaluated before the
 * DataSource bean definition is registered), so the SqlSessionFactory/SqlSessionTemplate
 * beans are declared here directly.
 */
@Configuration
public class MyBatisConfiguration {

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    @Bean
    SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
