package xin.v5ai.nb.starter;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "xin.v5ai.nb")
@MapperScan(basePackages = "xin.v5ai.nb.infrastructure", annotationClass = Mapper.class)
@EnableScheduling
public class V5aiApplication {

    public static void main(String[] args) {
        SpringApplication.run(V5aiApplication.class, args);
    }
}
