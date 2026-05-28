package cn.weidong.llm.aiknowengine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("cn.weidong.llm.aiknowengine.document.mapper")
@SpringBootApplication
public class AiKnowEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKnowEngineApplication.class, args);
    }
}
