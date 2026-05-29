package cn.weidong.llm.aiknowengine.document.service;

import cn.weidong.llm.aiknowengine.document.config.MinerUProperties;
import cn.weidong.llm.aiknowengine.document.constant.DocumentStatus;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MinerUProcessBaseService {

    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_PDF = "application/pdf";
    private static final String APPLICATION_ZIP = "application/zip";
    private static final String SUCCESS_STATE = "done";
    private static final String FAILED_STATE = "failed";

    private final MinerUProperties minerUProperties;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MinerUProcessBaseService(MinerUProperties minerUProperties,
                                    KnowledgeDocumentService knowledgeDocumentService,
                                    FileStorageService fileStorageService,
                                    ObjectMapper objectMapper) {
        this.minerUProperties = minerUProperties;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 使用 MinerU 将 PDF 解析为 markdown 结果 zip，并将 zip 保存到 MinIO。
     *
     * @param document 文档记录
     * @param inputStream PDF 文件输入流
     * @return 更新后的文档记录
     */
    public KnowledgeDocument process(KnowledgeDocument document, InputStream inputStream) {
        validateInput(document, inputStream);
        updateStatus(document, DocumentStatus.CONVERTING);

        try {
            MinerUBatchUpload batchUpload = createBatchUpload(document);
            uploadPdfToMinerU(batchUpload.uploadUrl(), inputStream);
            String fullZipUrl = waitForFullZipUrl(batchUpload.batchId());
            byte[] zipBytes = downloadZip(fullZipUrl);
            String convertedDocUrl = uploadZipToMinio(document, zipBytes);

            KnowledgeDocument updateDocument = new KnowledgeDocument();
            updateDocument.setDocId(document.getDocId());
            updateDocument.setConvertedDocUrl(convertedDocUrl);
            updateDocument.setStatus(DocumentStatus.CONVERTED);
            knowledgeDocumentService.updateById(updateDocument);

            document.setConvertedDocUrl(convertedDocUrl);
            document.setStatus(DocumentStatus.CONVERTED);
            return document;
        } catch (IOException ex) {
            throw new IllegalStateException("MinerU PDF parsing request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MinerU PDF parsing was interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("MinerU PDF parsing failed", ex);
        }
    }

    private void validateInput(KnowledgeDocument document, InputStream inputStream) {
        if (document == null || document.getDocId() == null) {
            throw new IllegalArgumentException("KnowledgeDocument and docId must not be null");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("PDF inputStream must not be null");
        }
        if (!StringUtils.hasText(minerUProperties.getToken())) {
            throw new IllegalStateException("MinerU API token must be configured");
        }
    }

    private void updateStatus(KnowledgeDocument document, DocumentStatus status) {
        KnowledgeDocument updateDocument = new KnowledgeDocument();
        updateDocument.setDocId(document.getDocId());
        updateDocument.setStatus(status);
        knowledgeDocumentService.updateById(updateDocument);
        document.setStatus(status);
    }

    private MinerUBatchUpload createBatchUpload(KnowledgeDocument document) throws IOException, InterruptedException {
        String fileName = resolvePdfFileName(document);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("enable_formula", true);
        requestBody.put("enable_table", true);
        requestBody.put("is_ocr", minerUProperties.isOcrEnabled());
        requestBody.put("model_version", minerUProperties.getModelVersion());
        requestBody.put("files", List.of(Map.of(
                "name", fileName,
                "data_id", "doc_" + document.getDocId()
        )));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(minerUProperties.normalizedBaseUrl() + "/api/v4/file-urls/batch"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + minerUProperties.getToken())
                .header("Content-Type", APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        JsonNode data = sendJsonRequest(request, "create MinerU batch upload").path("data");
        String batchId = data.path("batch_id").asText(null);
        JsonNode fileUrls = data.path("file_urls");
        if (!StringUtils.hasText(batchId) || !fileUrls.isArray() || fileUrls.isEmpty()) {
            throw new IllegalStateException("MinerU batch upload response missing batch_id or file_urls");
        }
        return new MinerUBatchUpload(batchId, fileUrls.get(0).asText());
    }

    private void uploadPdfToMinerU(String uploadUrl, InputStream inputStream) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", APPLICATION_PDF)
                .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> inputStream))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("MinerU file upload failed, status=" + response.statusCode());
        }
    }

    private String waitForFullZipUrl(String batchId) throws IOException, InterruptedException {
        for (int i = 0; i < minerUProperties.getMaxPollTimes(); i++) {
            MinerUResult result = getExtractResult(batchId);
            if (SUCCESS_STATE.equalsIgnoreCase(result.state()) && StringUtils.hasText(result.fullZipUrl())) {
                return result.fullZipUrl();
            }
            if (FAILED_STATE.equalsIgnoreCase(result.state())) {
                throw new IllegalStateException("MinerU parsing failed: " + result.errorMessage());
            }
            Thread.sleep(Duration.ofSeconds(minerUProperties.getPollIntervalSeconds()).toMillis());
        }
        throw new IllegalStateException("MinerU parsing timed out, batchId=" + batchId);
    }

    private MinerUResult getExtractResult(String batchId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(minerUProperties.normalizedBaseUrl() + "/api/v4/extract-results/batch/" + batchId))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + minerUProperties.getToken())
                .GET()
                .build();

        JsonNode data = sendJsonRequest(request, "query MinerU extract result").path("data");
        JsonNode extractResults = data.path("extract_result");
        if (!extractResults.isArray() || extractResults.isEmpty()) {
            return new MinerUResult("pending", null, null);
        }

        JsonNode result = extractResults.get(0);
        String state = result.path("state").asText("pending");
        String fullZipUrl = result.path("full_zip_url").asText(null);
        String errorMessage = result.path("err_msg").asText(result.path("error_msg").asText(null));
        return new MinerUResult(state, fullZipUrl, errorMessage);
    }

    private JsonNode sendJsonRequest(HttpRequest request, String action) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Failed to " + action + ", status=" + response.statusCode());
        }
        JsonNode body = objectMapper.readTree(response.body());
        int code = body.path("code").asInt(0);
        if (code != 0) {
            throw new IllegalStateException("Failed to " + action + ": " + body.path("msg").asText(response.body()));
        }
        return body;
    }

    private byte[] downloadZip(String fullZipUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullZipUrl))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Failed to download MinerU zip, status=" + response.statusCode());
        }
        return response.body();
    }

    private String uploadZipToMinio(KnowledgeDocument document, byte[] zipBytes) throws Exception {
        String objectName = "converted/" + document.getDocId() + "/" + UUID.randomUUID() + ".zip";
        return fileStorageService.uploadFile(objectName, zipBytes, APPLICATION_ZIP);
    }

    private String resolvePdfFileName(KnowledgeDocument document) {
        if (StringUtils.hasText(document.getDocTitle())) {
            String docTitle = document.getDocTitle();
            if (docTitle.toLowerCase().endsWith(".pdf")) {
                return docTitle;
            }
            return docTitle + ".pdf";
        }
        return "document-" + document.getDocId() + ".pdf";
    }

    private record MinerUBatchUpload(String batchId, String uploadUrl) {
    }

    private record MinerUResult(String state, String fullZipUrl, String errorMessage) {
    }
}
