package xin.v5ai.nb.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "xin.v5ai.nb")
public class V5aiApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(V5aiApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  V5ai-NB-AI启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
