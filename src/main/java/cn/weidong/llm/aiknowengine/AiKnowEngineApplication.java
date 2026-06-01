package cn.weidong.llm.aiknowengine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@MapperScan({
        "cn.weidong.llm.aiknowengine.document.mapper",
        "cn.weidong.llm.aiknowengine.chat.mapper"
})
@EnableAsync
@SpringBootApplication
public class AiKnowEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKnowEngineApplication.class, args);
    }
}
