package xin.v5ai.nb.code.gen;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.nio.file.Paths;

public class CodeGenerator {
    public static void main(String[] args) {
        // 通过环境变量获取数据库密码
        String url = System.getenv("url");
        String pwd = System.getenv("pwd");
        FastAutoGenerator.create(url,
                        "mypgsql", pwd)
                .globalConfig(builder -> builder
                        .author("ZYW")
                        .outputDir("/Users/jingyuan-mb01/other/v5ai-nb")
                        .commentDate("yyyy-MM-dd")
                )
                .packageConfig(builder -> builder
                        .parent("xin.v5ai.nb")
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .xml("mapper.xml")
                )
                .strategyConfig(builder -> builder
                        .addInclude("v5ai_knowledge_chunk")
                        .entityBuilder()
                        .enableLombok()
                )
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}