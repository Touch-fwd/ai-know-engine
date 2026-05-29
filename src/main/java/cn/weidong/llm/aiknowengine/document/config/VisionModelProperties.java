package cn.weidong.llm.aiknowengine.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 视觉大模型配置。
 * <p>
 * 当前使用 OpenAI-compatible 接口，baseUrl 可以指向 DashScope 等兼容服务。
 */
@ConfigurationProperties(prefix = "model")
public class VisionModelProperties {

    /** 大模型 API Key */
    private String apiKey;
    /** OpenAI-compatible 服务地址 */
    private String baseUrl;
    /** 支持视觉输入的模型名称 */
    private String modelName = "qwen3-vl-plus";
    /** 生成温度 */
    private double temperature = 0.7;
    /** 是否打印 LangChain4j 请求日志 */
    private boolean logRequests = true;
    /** 是否打印 LangChain4j 响应日志 */
    private boolean logResponses = true;
    /** 图片描述生成提示词 */
    private String prompt = "请描述这张图片的内容，包括场景、对象、布局、颜色、文字信息，直接输出纯文本描述，不要多余说明。";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isLogRequests() {
        return logRequests;
    }

    public void setLogRequests(boolean logRequests) {
        this.logRequests = logRequests;
    }

    public boolean isLogResponses() {
        return logResponses;
    }

    public void setLogResponses(boolean logResponses) {
        this.logResponses = logResponses;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
