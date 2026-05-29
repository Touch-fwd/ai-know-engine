package cn.weidong.llm.aiknowengine.document.service;

import cn.weidong.llm.aiknowengine.document.config.MinerUProperties;
import cn.weidong.llm.aiknowengine.document.constant.DocumentStatus;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MinerUProcessBaseService.class);

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
        long startTime = System.currentTimeMillis();
        log.info("Start MinerU PDF parsing process, docId={}, docTitle={}", document == null ? null : document.getDocId(),
                document == null ? null : document.getDocTitle());
        validateInput(document, inputStream);
        updateStatus(document, DocumentStatus.CONVERTING);

        try {
            MinerUBatchUpload batchUpload = createBatchUpload(document);
            log.info("MinerU batch upload created, docId={}, batchId={}", document.getDocId(), batchUpload.batchId());
            uploadPdfToMinerU(batchUpload.uploadUrl(), inputStream);
            log.info("PDF uploaded to MinerU, docId={}, batchId={}", document.getDocId(), batchUpload.batchId());
            String fullZipUrl = waitForFullZipUrl(batchUpload.batchId());
            log.info("MinerU parsing result is ready, docId={}, batchId={}", document.getDocId(), batchUpload.batchId());
            byte[] zipBytes = downloadZip(fullZipUrl);
            log.info("MinerU zip downloaded, docId={}, batchId={}, zipSize={}",
                    document.getDocId(), batchUpload.batchId(), zipBytes.length);
            String convertedDocUrl = uploadZipToMinio(document, zipBytes);
            log.info("MinerU zip uploaded to MinIO, docId={}, convertedDocUrl={}",
                    document.getDocId(), convertedDocUrl);

            KnowledgeDocument updateDocument = new KnowledgeDocument();
            updateDocument.setDocId(document.getDocId());
            updateDocument.setConvertedDocUrl(convertedDocUrl);
            updateDocument.setStatus(DocumentStatus.CONVERTED);
            knowledgeDocumentService.updateById(updateDocument);

            document.setConvertedDocUrl(convertedDocUrl);
            document.setStatus(DocumentStatus.CONVERTED);
            log.info("MinerU PDF parsing process completed, docId={}, elapsedMs={}, status={}",
                    document.getDocId(), System.currentTimeMillis() - startTime, document.getStatus());
            return document;
        } catch (IOException ex) {
            log.error("MinerU PDF parsing request failed, docId={}", document.getDocId(), ex);
            throw new IllegalStateException("MinerU PDF parsing request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("MinerU PDF parsing interrupted, docId={}", document.getDocId(), ex);
            throw new IllegalStateException("MinerU PDF parsing was interrupted", ex);
        } catch (Exception ex) {
            log.error("MinerU PDF parsing failed, docId={}", document.getDocId(), ex);
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
        log.info("Knowledge document status updated, docId={}, status={}", document.getDocId(), status);
    }

    private MinerUBatchUpload createBatchUpload(KnowledgeDocument document) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String fileName = resolvePdfFileName(document);
        log.info("Creating MinerU batch upload, docId={}, fileName={}, modelVersion={}, ocrEnabled={}",
                document.getDocId(), fileName, minerUProperties.getModelVersion(), minerUProperties.isOcrEnabled());
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("enable_formula", true);
        requestBody.put("enable_table", true);
        requestBody.put("model_version", minerUProperties.getModelVersion());
        requestBody.put("files", List.of(Map.of(
                "name", fileName,
                "is_ocr", minerUProperties.isOcrEnabled(),
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
        log.info("MinerU batch upload response received, docId={}, batchId={}, elapsedMs={}",
                document.getDocId(), batchId, System.currentTimeMillis() - startTime);
        return new MinerUBatchUpload(batchId, fileUrls.get(0).asText());
    }

    private void uploadPdfToMinerU(String uploadUrl, InputStream inputStream) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        log.info("Uploading PDF stream to MinerU signed URL");
        byte[] fileBytes = inputStream.readAllBytes();
        log.info("PDF stream loaded for MinerU upload, size={}", fileBytes.length);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(Duration.ofMinutes(10))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("MinerU file upload failed, status={}, responseBody={}, elapsedMs={}",
                    response.statusCode(), response.body(), System.currentTimeMillis() - startTime);
            throw new IllegalStateException("MinerU file upload failed, status=" + response.statusCode());
        }
        log.info("MinerU file upload succeeded, status={}, elapsedMs={}",
                response.statusCode(), System.currentTimeMillis() - startTime);
    }

    private String waitForFullZipUrl(String batchId) throws IOException, InterruptedException {
        log.info("Start polling MinerU extract result, batchId={}, maxPollTimes={}, pollIntervalSeconds={}",
                batchId, minerUProperties.getMaxPollTimes(), minerUProperties.getPollIntervalSeconds());
        for (int i = 0; i < minerUProperties.getMaxPollTimes(); i++) {
            MinerUResult result = getExtractResult(batchId);
            log.info("MinerU extract result polled, batchId={}, pollIndex={}, state={}, hasFullZipUrl={}",
                    batchId, i + 1, result.state(), StringUtils.hasText(result.fullZipUrl()));
            if (SUCCESS_STATE.equalsIgnoreCase(result.state()) && StringUtils.hasText(result.fullZipUrl())) {
                return result.fullZipUrl();
            }
            if (FAILED_STATE.equalsIgnoreCase(result.state())) {
                log.error("MinerU parsing returned failed state, batchId={}, errorMessage={}",
                        batchId, result.errorMessage());
                throw new IllegalStateException("MinerU parsing failed: " + result.errorMessage());
            }
            Thread.sleep(Duration.ofSeconds(minerUProperties.getPollIntervalSeconds()).toMillis());
        }
        log.error("MinerU parsing timed out, batchId={}", batchId);
        throw new IllegalStateException("MinerU parsing timed out, batchId=" + batchId);
    }

    private MinerUResult getExtractResult(String batchId) throws IOException, InterruptedException {
        log.debug("Querying MinerU extract result, batchId={}", batchId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(minerUProperties.normalizedBaseUrl() + "/api/v4/extract-results/batch/" + batchId))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + minerUProperties.getToken())
                .GET()
                .build();

        JsonNode data = sendJsonRequest(request, "query MinerU extract result").path("data");
        JsonNode extractResults = data.path("extract_result");
        if (!extractResults.isArray() || extractResults.isEmpty()) {
            log.debug("MinerU extract result is empty, batchId={}", batchId);
            return new MinerUResult("pending", null, null);
        }

        JsonNode result = extractResults.get(0);
        String state = result.path("state").asText("pending");
        String fullZipUrl = result.path("full_zip_url").asText(null);
        String errorMessage = result.path("err_msg").asText(result.path("error_msg").asText(null));
        return new MinerUResult(state, fullZipUrl, errorMessage);
    }

    private JsonNode sendJsonRequest(HttpRequest request, String action) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("MinerU HTTP request failed, action={}, status={}, elapsedMs={}",
                    action, response.statusCode(), System.currentTimeMillis() - startTime);
            throw new IllegalStateException("Failed to " + action + ", status=" + response.statusCode());
        }
        JsonNode body = objectMapper.readTree(response.body());
        int code = body.path("code").asInt(0);
        if (code != 0) {
            log.error("MinerU API returned non-zero code, action={}, code={}, msg={}, elapsedMs={}",
                    action, code, body.path("msg").asText(), System.currentTimeMillis() - startTime);
            throw new IllegalStateException("Failed to " + action + ": " + body.path("msg").asText(response.body()));
        }
        log.info("MinerU HTTP request succeeded, action={}, status={}, elapsedMs={}",
                action, response.statusCode(), System.currentTimeMillis() - startTime);
        return body;
    }

    private byte[] downloadZip(String fullZipUrl) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        log.info("Downloading MinerU zip result");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullZipUrl))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("Failed to download MinerU zip, status={}, elapsedMs={}",
                    response.statusCode(), System.currentTimeMillis() - startTime);
            throw new IllegalStateException("Failed to download MinerU zip, status=" + response.statusCode());
        }
        log.info("MinerU zip downloaded successfully, status={}, size={}, elapsedMs={}",
                response.statusCode(), response.body().length, System.currentTimeMillis() - startTime);
        return response.body();
    }

    private String uploadZipToMinio(KnowledgeDocument document, byte[] zipBytes) throws Exception {
        String objectName = "converted/" + document.getDocId() + "/" + UUID.randomUUID() + ".zip";
        log.info("Uploading MinerU zip to MinIO, docId={}, objectName={}, zipSize={}",
                document.getDocId(), objectName, zipBytes.length);
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
