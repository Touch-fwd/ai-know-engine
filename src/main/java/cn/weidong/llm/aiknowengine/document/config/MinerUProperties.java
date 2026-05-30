package cn.weidong.llm.aiknowengine.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinerU 解析服务配置。
 * <p>
 * token 建议通过环境变量注入，避免把密钥写死在代码仓库中。
 */
@ConfigurationProperties(prefix = "mineru")
public class MinerUProperties {

    /** MinerU 服务地址 */
    private String baseUrl = "https://mineru.net";
    /** MinerU API token */
    private String token;
    /** 解析模型版本，vlm 用于支持图片/扫描件等视觉解析能力 */
    private String modelVersion = "vlm";
    /** 是否开启 OCR */
    private boolean ocrEnabled = true;
    /** 轮询解析结果的间隔秒数 */
    private int pollIntervalSeconds = 5;
    /** 最大轮询次数 */
    private int maxPollTimes = 120;
    /** MinerU zip 下载和解压的本地临时目录 */
    private String workDir = "data/mineru";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public boolean isOcrEnabled() {
        return ocrEnabled;
    }

    public void setOcrEnabled(boolean ocrEnabled) {
        this.ocrEnabled = ocrEnabled;
    }

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public void setPollIntervalSeconds(int pollIntervalSeconds) {
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    public int getMaxPollTimes() {
        return maxPollTimes;
    }

    public void setMaxPollTimes(int maxPollTimes) {
        this.maxPollTimes = maxPollTimes;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://mineru.net";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

}
