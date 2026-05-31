package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.config.MinioProperties;
import cn.weidong.llm.aiknowengine.document.constant.DocumentStatus;
import cn.weidong.llm.aiknowengine.document.constant.FileType;
import cn.weidong.llm.aiknowengine.document.constant.SegmentStatus;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeSegment;
import cn.weidong.llm.aiknowengine.document.event.DocumentSplitCompletedEvent;
import cn.weidong.llm.aiknowengine.document.param.DocumentSplitParam;
import cn.weidong.llm.aiknowengine.document.service.*;
import cn.weidong.llm.aiknowengine.document.util.FileTypeUtil;
import cn.weidong.llm.aiknowengine.infra.lock.DistributeLock;
import cn.weidong.llm.aiknowengine.rag.constant.MetadataKeyConstant;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默认文档处理服务实现。
 * <p>
 * 负责串联文档处理主流程：上传原始文件、转换为 Markdown、切分片段、向量化入库。
 * 这里尽量只编排流程，具体文件存储、格式解析、切分器选择和向量库写入交给对应服务完成。
 */
@Service
public class DocumentHandleServiceImpl implements DocumentHandleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentHandleServiceImpl.class);

    /**
     * 片段元数据反序列化类型，避免 fastjson 泛型擦除导致解析结果不可控。
     */
    private static final TypeReference<Map<String, Object>> METADATA_TYPE_REFERENCE = new TypeReference<>() {
    };

    @Autowired
    private  KnowledgeDocumentService knowledgeDocumentService;
    @Autowired
    private  FileStorageService fileStorageService;
    @Autowired
    private FileProcessServiceFactory fileProcessServiceFactory;
    @Autowired
    private KnowledgeSegmentService knowledgeSegmentService;
    @Autowired
    private DocumentSplitterFactory documentSplitterFactory;
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OpenAiEmbeddingModel openAiEmbeddingModel;
    @Autowired
    private ElasticsearchEmbeddingStore elasticsearchEmbeddingStore;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * 上传文档并触发对应格式的转换处理。
     * <p>
     * 当前流程会先将原始文件写入 MinIO，再落库生成文档记录。
     * 如果文件类型存在对应的 {@link FileProcessService}，则继续执行解析转换流程，
     * 例如 PDF 会转换为 Markdown 并更新文档状态为 CONVERTED。
     *
     * @param file 上传的原始文件
     * @param uploadUser 上传用户
     * @param accessibleBy 文档可见范围
     * @return 保存并处理后的文档记录
     */
    @Override
    @DistributeLock(scene = "document-upload" ,keyExpression = "#uploadUser",waitTime = 0)
    public KnowledgeDocument upload(MultipartFile file, String uploadUser, String accessibleBy) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        long fileSize = file == null ? 0 : file.getSize();
        log.info("Start uploading knowledge document, fileName={}, size={}, uploadUser={}, accessibleBy={}",
                originalFilename, fileSize, uploadUser, accessibleBy);
        try {
            // Step 1：先保存原始文件，确保后续转换失败时也能追溯原始上传内容。
            String fileUrl = fileStorageService.uploadFile(file, originalFilename);
            log.info("Knowledge document file uploaded to storage, fileName={}, fileUrl={}", originalFilename, fileUrl);

            // Step 2：创建文档主表记录，初始状态为 UPLOADED。
            KnowledgeDocument document = new KnowledgeDocument();
            document.setDocTitle(originalFilename);
            document.setUploadUser(uploadUser);
            document.setAccessibleBy(accessibleBy);
            document.setDocUrl(fileUrl);
            document.setStatus(DocumentStatus.UPLOADED);
            knowledgeDocumentService.save(document);
            log.info("Knowledge document saved, docId={}, status={}", document.getDocId(), document.getStatus());


            // Step 3：根据文件后缀和内容识别文件类型，再选择对应处理器。
            FileType fileType = FileTypeUtil.getFileType(file);

            FileProcessService fileProcessService = fileProcessServiceFactory.get(fileType);

            // Step 4：存在处理器时执行转换，具体状态流转由对应处理器负责。
            if(fileProcessService != null){
                fileProcessService.processDocument(document,file.getInputStream());
            }

            log.info("Knowledge document upload completed without MinerU parsing, docId={}, fileName={}",
                    document.getDocId(), originalFilename);
            return document;
        } catch (Exception ex) {
            log.error("Knowledge document upload failed, fileName={}, uploadUser={}", originalFilename, uploadUser, ex);
            throw new IllegalStateException("Knowledge document upload failed", ex);
        }
    }

    /**
     * 将已转换完成的 Markdown 文档切分为知识片段。
     * <p>
     * 只允许处理 CONVERTED 状态的文档。切分后的片段会保存到 knowledge_segment 表，
     * 文档状态随即推进为 CHUNKED，等待后续向量化。
     *
     * @param document 待切分文档
     * @param documentSplitParam 切分参数，用于决定切分器类型、块大小和重叠范围
     * @return 成功保存的片段数量
     */
    @Override
    @DistributeLock(scene = "document-split" ,keyExpression = "#document.docId",waitTime = 0)
    public int split(KnowledgeDocument document, DocumentSplitParam documentSplitParam) {
        // Step 1：校验文档基础信息和状态，避免重复切分或越过转换步骤。
        if (document == null || document.getDocId() == null) {
            throw new IllegalArgumentException("待切分文档不能为空");
        }
        if (document.getStatus() != DocumentStatus.CONVERTED) {
            throw new IllegalStateException("当前文档状态为 " + document.getStatus() + "，不可切分");
        }
        if (!StringUtils.hasText(document.getConvertedDocUrl())) {
            throw new IllegalStateException("文档转换结果地址为空，无法切分");
        }

        log.info("Start splitting document, docId={}, convertedDocUrl={}, splitParam={}",
                document.getDocId(), document.getConvertedDocUrl(), documentSplitParam);
        try {
            // Step 2：从转换结果 URL 中解析 MinIO 对象名，并下载 Markdown 内容。
            String objectName = resolveObjectName(document.getConvertedDocUrl());
            String markdown;
            try (InputStream inputStream = fileStorageService.downloadFile(objectName)) {
                markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Step 3：根据入参选择切分器，将 Markdown 转为 LangChain4j 的 TextSegment。
            DocumentSplitter splitter = documentSplitterFactory.get(documentSplitParam);
            Document splitDocument = Document.from(markdown, Metadata.from("docId", String.valueOf(document.getDocId())));
            List<TextSegment> segments = splitter.split(splitDocument);

            // Step 4：转换为业务片段实体并保存，保留 docId、文件名、URL 等召回所需元数据。
            List<KnowledgeSegment> knowledgeSegments = toKnowledgeSegments(document, segments);
            if (!knowledgeSegments.isEmpty()) {
                knowledgeSegmentService.saveBatch(knowledgeSegments);
            }

            // Step 5：片段保存成功后推进文档状态，表示可以进入向量化阶段。
            KnowledgeDocument updateDocument = new KnowledgeDocument();
            updateDocument.setDocId(document.getDocId());
            updateDocument.setStatus(DocumentStatus.CHUNKED);
            knowledgeDocumentService.updateById(updateDocument);
            document.setStatus(DocumentStatus.CHUNKED);
            log.info("Document split completed, docId={}, segmentCount={}", document.getDocId(), knowledgeSegments.size());
            // 发布文档切换完成事件，实现文档向量化
            applicationEventPublisher.publishEvent(new DocumentSplitCompletedEvent(document.getDocId()));
            return knowledgeSegments.size();
        } catch (Exception ex) {
            log.error("Document split failed, docId={}", document.getDocId(), ex);
            throw new IllegalStateException("文档切分失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 对已切分文档进行向量化并写入 Elasticsearch 向量索引。
     * <p>
     * 只处理 CHUNKED 状态的文档，并且仅向量化状态为 STORED、未标记跳过嵌入的片段。
     * 向量库写入成功后，会回填每个片段的 embeddingId，并将片段和文档状态更新为 VECTOR_STORED。
     *
     * @param document 待向量化文档
     * @return true 表示向量化和入库成功；false 表示没有可处理片段
     */
    @Override
    @DistributeLock(scene = "document-embed" ,keyExpression = "#document.docId",waitTime = 0)
    public boolean embedAndStore(KnowledgeDocument document) {
        // Step 1：检查状态机，只有完成切分后的文档才允许进入向量化。
        if (document == null || document.getDocId() == null) {
            throw new IllegalArgumentException("待向量化文档不能为空");
        }
        if (document.getStatus() != DocumentStatus.CHUNKED) {
            throw new IllegalStateException("当前文档状态为 " + document.getStatus() + "，不可向量化");
        }

        log.info("Start embedding document segments, docId={}", document.getDocId());
        try {
            // Step 2：查询待向量化片段；已向量化或显式跳过的片段不再重复处理。
            List<KnowledgeSegment> segments = knowledgeSegmentService.list(
                    new LambdaQueryWrapper<KnowledgeSegment>()
                            .eq(KnowledgeSegment::getDocumentId, document.getDocId())
                            .eq(KnowledgeSegment::getStatus, SegmentStatus.STORED)
                            .and(wrapper -> wrapper.isNull(KnowledgeSegment::getSkipEmbedding)
                                    .or()
                                    .ne(KnowledgeSegment::getSkipEmbedding, 1))
                            .orderByAsc(KnowledgeSegment::getChunkOrder)
            );
            if (segments.isEmpty()) {
                log.warn("No segments need embedding, docId={}", document.getDocId());
                return false;
            }

            // Step 3：过滤空文本片段，防止向量模型入参为空或回填 embeddingId 时发生错位。
            List<KnowledgeSegment> embeddableSegments = filterEmbeddableSegments(document, segments);
            if (embeddableSegments.isEmpty()) {
                log.warn("No valid text segments need embedding, docId={}", document.getDocId());
                return false;
            }

            // Step 4：构造 LangChain4j TextSegment，metadata 会随向量一起存储，便于检索后还原上下文。
            List<TextSegment> textSegments = embeddableSegments.stream().map(segment -> TextSegment.from(segment.getText(), Metadata.from(segment.getMetadataMap()))).toList();

            // Step 5：调用向量模型批量生成 embedding。
            log.info("Embedding model request started, docId={}, segmentCount={}", document.getDocId(), textSegments.size());
            Response<List<Embedding>> response = openAiEmbeddingModel.embedAll(textSegments);
            List<Embedding> embeddings = response.content();
            if (embeddings.size() != textSegments.size()) {
                throw new IllegalStateException("向量模型返回数量异常，segmentCount="
                        + textSegments.size() + ", embeddingCount=" + (embeddings == null ? 0 : embeddings.size()));
            }

            // Step 6：写入向量库并保存返回的向量记录 ID，后续可以用于检索定位或删除。
            List<String> embeddingIds = elasticsearchEmbeddingStore.addAll(embeddings, textSegments);
            if (embeddingIds == null || embeddingIds.size() != embeddableSegments.size()) {
                throw new IllegalStateException("向量库返回 ID 数量异常，segmentCount="
                        + embeddableSegments.size() + ", embeddingIdCount=" + (embeddingIds == null ? 0 : embeddingIds.size()));
            }
            for (int i = 0; i < embeddableSegments.size(); i++) {
                KnowledgeSegment segment = embeddableSegments.get(i);
                segment.setEmbeddingId(embeddingIds.get(i));
                segment.setStatus(SegmentStatus.VECTOR_STORED);
            }
            knowledgeSegmentService.updateBatchById(embeddableSegments);

            // Step 7：所有可处理片段入库完成后，推进文档主状态。
            KnowledgeDocument updateDocument = new KnowledgeDocument();
            updateDocument.setDocId(document.getDocId());
            updateDocument.setStatus(DocumentStatus.VECTOR_STORED);
            knowledgeDocumentService.updateById(updateDocument);
            log.info("Document embedding completed, docId={}, segmentCount={}", document.getDocId(), embeddableSegments.size());
            return true;
        } catch (Exception ex) {
            log.error("Document embedding failed, docId={}", document.getDocId(), ex);
            throw new IllegalStateException("文档向量化失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 将 LangChain4j 切分出的 TextSegment 转换为数据库片段实体。
     * <p>
     * 这里会补充文档维度元数据，并把 metadata 序列化成 JSON 字符串保存，
     * 后续向量化和检索召回时都依赖这些字段还原业务上下文。
     *
     * @param document 原始文档记录
     * @param segments 切分器输出的文本片段
     * @return 可落库的知识片段集合
     */
    private List<KnowledgeSegment> toKnowledgeSegments(KnowledgeDocument document, List<TextSegment> segments) {
        List<KnowledgeSegment> knowledgeSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment textSegment = segments.get(i);
            if (textSegment == null || !StringUtils.hasText(textSegment.text())) {
                continue;
            }

            // 保存正文、顺序和状态；chunkId 来源于切分器生成的元数据。
            KnowledgeSegment knowledgeSegment = new KnowledgeSegment();
            knowledgeSegment.setText(textSegment.text());
            knowledgeSegment.setChunkId(textSegment.metadata().getString(MetadataKeyConstant.CHUNK_ID));

            // 补齐业务元数据，保证片段脱离文档主表后仍能被检索链路识别。
            Metadata metadata = textSegment.metadata();
            metadata.put(MetadataKeyConstant.DOC_ID, document.getDocId());
            metadata.put(MetadataKeyConstant.FILE_NAME, document.getDocTitle());
            metadata.put(MetadataKeyConstant.URL, document.getDocUrl());

            knowledgeSegment.setMetadata(JSON.toJSONString(metadata.toMap()));
            knowledgeSegment.setDocumentId(document.getDocId());
            knowledgeSegment.setChunkOrder(i + 1);
            knowledgeSegment.setStatus(SegmentStatus.STORED);

            // 标题父块或结构性片段可能只用于组织上下文，不一定需要生成 embedding。
            Integer skipEmbedding = metadata.getInteger(MetadataKeyConstant.SKIP_EMBEDDING);
            if (skipEmbedding != null && skipEmbedding == 1) {
                knowledgeSegment.setSkipEmbedding(1);
            } else {
                knowledgeSegment.setSkipEmbedding(0);
            }
            knowledgeSegments.add(knowledgeSegment);
        }
        return knowledgeSegments;
    }

    /**
     * 过滤不适合送入向量模型的片段。
     * <p>
     * 正常情况下数据库 text 字段非空；这里保留兜底校验，避免脏数据影响整批向量化。
     *
     * @param document 当前文档
     * @param segments 数据库查询出的候选片段
     * @return 可向量化的有效片段
     */
    private List<KnowledgeSegment> filterEmbeddableSegments(KnowledgeDocument document, List<KnowledgeSegment> segments) {
        List<KnowledgeSegment> embeddableSegments = new ArrayList<>();
        for (KnowledgeSegment segment : segments) {
            if (segment == null || !StringUtils.hasText(segment.getText())) {
                log.warn("Skip empty text segment during embedding, docId={}, segmentId={}",
                        document.getDocId(), segment == null ? null : segment.getId());
                continue;
            }
            embeddableSegments.add(segment);
        }
        return embeddableSegments;
    }

    /**
     * 从 MinIO 访问 URL 中还原对象名。
     * <p>
     * MinIO 下载接口通常只需要 bucket 内部对象名；如果 URL path 中带有 bucket 前缀，
     * 这里会自动剥离，兼容不同 endpoint 暴露方式。
     *
     * @param fileUrl 文件访问地址
     * @return MinIO bucket 内部对象名
     */
    private String resolveObjectName(String fileUrl) {
        URI uri = URI.create(fileUrl);
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("无法从文件地址解析 MinIO 对象名: " + fileUrl);
        }
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        String bucketPrefix = minioProperties.getBucketName() + "/";
        if (normalizedPath.startsWith(bucketPrefix)) {
            return normalizedPath.substring(bucketPrefix.length());
        }
        return normalizedPath;
    }
}
