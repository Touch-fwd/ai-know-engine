package cn.weidong.llm.aiknowengine.chat.config;

import cn.weidong.llm.aiknowengine.document.config.VisionModelProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModelConfig {

    @Bean
    public ChatModel chatModel(VisionModelProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.normalizedBaseUrl())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .logRequests(properties.isLogRequests())
                .logResponses(properties.isLogResponses())
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel(VisionModelProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.normalizedBaseUrl())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .logRequests(properties.isLogRequests())
                .logResponses(properties.isLogResponses())
                .build();
    }
}
