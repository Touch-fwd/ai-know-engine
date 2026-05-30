package cn.weidong.llm.aiknowengine.document.service;

import cn.weidong.llm.aiknowengine.document.config.MinerUProperties;
import cn.weidong.llm.aiknowengine.document.config.VisionModelProperties;
import cn.weidong.llm.aiknowengine.document.constant.DocumentStatus;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class MinerUProcessBaseService {

    private static final Logger log = LoggerFactory.getLogger(MinerUProcessBaseService.class);

    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_PDF = "application/pdf";
    private static final String TEXT_MARKDOWN = "text/markdown; charset=utf-8";
    private static final String SUCCESS_STATE = "done";
    private static final String FAILED_STATE = "failed";
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)]\\(([^)]+)\\)");

    private final MinerUProperties minerUProperties;
    private final VisionModelProperties visionModelProperties;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MinerUProcessBaseService(MinerUProperties minerUProperties,
                                    VisionModelProperties visionModelProperties,
                                    KnowledgeDocumentService knowledgeDocumentService,
                                    FileStorageService fileStorageService,
                                    ObjectMapper objectMapper) {
        this.minerUProperties = minerUProperties;
        this.visionModelProperties = visionModelProperties;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 使用 MinerU 将 PDF 解析为 Markdown。
     * <p>
     * 完整流程：
     * 1. 将文档状态更新为 CONVERTING；
     * 2. 创建 MinerU 解析任务并上传 PDF；
     * 3. 轮询解析结果，下载 MinerU 返回的 zip；
     * 4. 解压 zip，上传图片到 MinIO，并把 Markdown 中的图片链接替换为 MinIO URL；
     * 5. 调用视觉模型为图片生成描述，并插入 Markdown；
     * 6. 上传最终 Markdown 到 MinIO，并更新 document.convertedDocUrl 与状态。
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
            // MinerU 采用“先申请签名 URL，再 PUT 上传文件，再轮询结果”的异步解析模式。
            MinerUBatchUpload batchUpload = createBatchUpload(document);
            log.info("MinerU batch upload created, docId={}, batchId={}", document.getDocId(), batchUpload.batchId());
            uploadPdfToMinerU(batchUpload.uploadUrl(), inputStream);
            log.info("PDF uploaded to MinerU, docId={}, batchId={}", document.getDocId(), batchUpload.batchId());
            String fullZipUrl = waitForFullZipUrl(batchUpload.batchId());
            log.info("MinerU parsing result is ready, docId={}, batchId={}", document.getDocId(), batchUpload.batchId());
            byte[] zipBytes = downloadZip(fullZipUrl);
            log.info("MinerU zip downloaded, docId={}, batchId={}, zipSize={}",
                    document.getDocId(), batchUpload.batchId(), zipBytes.length);
            // MinerU 原始结果是 zip，本地解压后需要进一步处理图片和 Markdown，再上传最终 md。
            String convertedDocUrl = processMinerUZip(document, zipBytes);
            log.info("MinerU markdown processed and uploaded to MinIO, docId={}, convertedDocUrl={}",
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
        // is_ocr 放在文件维度，方便后续同一 batch 支持不同文件使用不同解析策略。
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
        // MinerU 返回的是对象存储签名 URL，PUT 上传时不要额外设置 Content-Type，避免签名校验失败。
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

    private String processMinerUZip(KnowledgeDocument document, byte[] zipBytes) throws Exception {
        Path workDir = createWorkDir(document);
        try {
            Path zipPath = workDir.resolve("mineru-result.zip");
            Path unzipDir = workDir.resolve("unzipped");
            Files.createDirectories(unzipDir);
            Files.write(zipPath, zipBytes);
            log.info("MinerU zip saved locally, docId={}, zipPath={}", document.getDocId(), zipPath.toAbsolutePath());

            // 先落地 zip 再解压，便于问题排查；finally 中会清理整个临时目录。
            unzip(zipPath, unzipDir);
            log.info("MinerU zip extracted, docId={}, unzipDir={}", document.getDocId(), unzipDir.toAbsolutePath());

            Path markdownPath = findMarkdownFile(unzipDir);
            log.info("MinerU markdown found, docId={}, markdownPath={}", document.getDocId(), markdownPath.toAbsolutePath());

            String markdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
            // Markdown 中的本地图片链接不能直接被外部访问，需要上传 MinIO 后替换成可访问 URL。
            String processedMarkdown = processMarkdownImages(document, markdownPath, markdown);
            Files.writeString(markdownPath, processedMarkdown, StandardCharsets.UTF_8);

            String mdObjectName = "converted/" + document.getDocId() + "/" + stripExtension(markdownPath.getFileName().toString()) + "-"
                    + UUID.randomUUID() + ".md";
            String mdUrl = fileStorageService.uploadFile(mdObjectName,
                    processedMarkdown.getBytes(StandardCharsets.UTF_8),
                    TEXT_MARKDOWN);
            log.info("Processed markdown uploaded to MinIO, docId={}, objectName={}, mdUrl={}",
                    document.getDocId(), mdObjectName, mdUrl);
            return mdUrl;
        } finally {
            deleteRecursively(workDir);
            log.info("MinerU local temporary files cleaned, docId={}, workDir={}", document.getDocId(), workDir.toAbsolutePath());
        }
    }

    private Path createWorkDir(KnowledgeDocument document) throws IOException {
        Path baseDir = Path.of(minerUProperties.getWorkDir()).toAbsolutePath().normalize();
        Path workDir = baseDir.resolve("doc-" + document.getDocId() + "-" + UUID.randomUUID()).normalize();
        Files.createDirectories(workDir);
        return workDir;
    }

    private void unzip(Path zipPath, Path targetDir) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path targetPath = targetDir.resolve(entry.getName()).normalize();
                // 防止恶意 zip 通过 ../ 写到目标目录之外。
                if (!targetPath.startsWith(targetDir)) {
                    throw new IOException("Unsafe zip entry path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zipInputStream, targetPath);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private Path findMarkdownFile(Path unzipDir) throws IOException {
        try (Stream<Path> paths = Files.walk(unzipDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("MinerU markdown file not found"));
        }
    }

    private String processMarkdownImages(KnowledgeDocument document, Path markdownPath, String markdown) throws Exception {
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown);
        StringBuffer buffer = new StringBuffer();
        int imageCount = 0;
        while (matcher.find()) {
            String altText = matcher.group(1);
            String imagePathText = matcher.group(2);
            Path imagePath = resolveMarkdownImagePath(markdownPath, imagePathText);
            if (imagePath == null || !Files.exists(imagePath) || !Files.isRegularFile(imagePath)) {
                log.warn("Markdown image not found, docId={}, markdownPath={}, imagePath={}",
                        document.getDocId(), markdownPath, imagePathText);
                continue;
            }

            byte[] imageBytes = Files.readAllBytes(imagePath);
            String contentType = resolveContentType(imagePath);
            String imageObjectName = "converted/" + document.getDocId() + "/images/" + UUID.randomUUID() + "-"
                    + imagePath.getFileName();
            String imageUrl = fileStorageService.uploadFile(imageObjectName, imageBytes, contentType);
            // 视觉模型使用 Base64 Data URL，不依赖图片公网可访问性。
            String imageDescription = generateImageDescription(toDataUrl(contentType, imageBytes));
            String replacement = "![" + altText + "](" + imageUrl + ")";
            if (StringUtils.hasText(imageDescription)) {
                replacement = replacement + "\n\n图片描述：" + imageDescription + "\n";
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            imageCount++;
            log.info("Markdown image processed, docId={}, imagePath={}, imageUrl={}, hasDescription={},imageDescription={}",
                    document.getDocId(), imagePath, imageUrl, StringUtils.hasText(imageDescription),imageDescription);
        }
        matcher.appendTail(buffer);
        log.info("Markdown images processed, docId={}, imageCount={}", document.getDocId(), imageCount);
        return buffer.toString();
    }

    private Path resolveMarkdownImagePath(Path markdownPath, String imagePathText) {
        if (!StringUtils.hasText(imagePathText)) {
            return null;
        }
        String normalizedImagePath = imagePathText.trim();
        if (normalizedImagePath.startsWith("<") && normalizedImagePath.endsWith(">")) {
            normalizedImagePath = normalizedImagePath.substring(1, normalizedImagePath.length() - 1);
        }
        if (normalizedImagePath.startsWith("http://")
                || normalizedImagePath.startsWith("https://")
                || normalizedImagePath.startsWith("data:")) {
            return null;
        }
        return markdownPath.getParent().resolve(normalizedImagePath).normalize();
    }

    public String generateImageDescription(String imageUrl) {
        if (!StringUtils.hasText(visionModelProperties.getApiKey()) || !StringUtils.hasText(visionModelProperties.getBaseUrl())) {
            log.warn("Vision model config is incomplete, skip image description generation");
            return null;
        }
        try {
            String baseUrl = visionModelProperties.normalizedBaseUrl();
            log.info("Generating image description, modelName={}, baseUrl={}",
                    visionModelProperties.getModelName(), baseUrl);
            // 使用 OpenAI-compatible 协议调用视觉模型，具体供应商由配置中的 baseUrl 决定。
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .apiKey(visionModelProperties.getApiKey())
                    .baseUrl(baseUrl)
                    .modelName(visionModelProperties.getModelName())
                    .temperature(visionModelProperties.getTemperature())
                    .logRequests(visionModelProperties.isLogRequests())
                    .logResponses(visionModelProperties.isLogResponses())
                    .build();

            UserMessage userMessage = UserMessage.from(
                    TextContent.from(visionModelProperties.getPrompt()),
                    ImageContent.from(imageUrl)
            );
            String text = chatModel.chat(userMessage).aiMessage().text();
            log.info("Vision model image description generated, textLength={}", text == null ? 0 : text.length());
            return text;
        } catch (Exception ex) {
            log.warn("Failed to generate image description, modelName={}, baseUrl={}",
                    visionModelProperties.getModelName(), visionModelProperties.normalizedBaseUrl(), ex);
            return null;
        }
    }

    private String toDataUrl(String contentType, byte[] bytes) {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String resolveContentType(Path path) throws IOException {
        String contentType = Files.probeContentType(path);
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(paths::add);
        } catch (IOException ex) {
            log.warn("Failed to list temporary files for cleanup, path={}", path, ex);
            return;
        }
        for (Path item : paths) {
            try {
                Files.deleteIfExists(item);
            } catch (IOException ex) {
                log.warn("Failed to delete temporary file, path={}", item, ex);
            }
        }
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
