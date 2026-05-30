package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.config.MinioProperties;
import cn.weidong.llm.aiknowengine.document.constant.DocumentStatus;
import cn.weidong.llm.aiknowengine.document.constant.FileType;
import cn.weidong.llm.aiknowengine.document.constant.SegmentStatus;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeSegment;
import cn.weidong.llm.aiknowengine.document.param.DocumentSplitParam;
import cn.weidong.llm.aiknowengine.document.service.*;
import cn.weidong.llm.aiknowengine.document.util.FileTypeUtil;
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
 */
@Service
public class DocumentHandleServiceImpl implements DocumentHandleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentHandleServiceImpl.class);
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

    @Override
    public KnowledgeDocument upload(MultipartFile file, String uploadUser, String accessibleBy) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        long fileSize = file == null ? 0 : file.getSize();
        log.info("Start uploading knowledge document, fileName={}, size={}, uploadUser={}, accessibleBy={}",
                originalFilename, fileSize, uploadUser, accessibleBy);
        try {
            String fileUrl = fileStorageService.uploadFile(file, originalFilename);
            log.info("Knowledge document file uploaded to storage, fileName={}, fileUrl={}", originalFilename, fileUrl);

            KnowledgeDocument document = new KnowledgeDocument();
            document.setDocTitle(originalFilename);
            document.setUploadUser(uploadUser);
            document.setAccessibleBy(accessibleBy);
            document.setDocUrl(fileUrl);
            document.setStatus(DocumentStatus.UPLOADED);
            knowledgeDocumentService.save(document);
            log.info("Knowledge document saved, docId={}, status={}", document.getDocId(), document.getStatus());


            FileType fileType = FileTypeUtil.getFileType(file);

            FileProcessService fileProcessService = fileProcessServiceFactory.get(fileType);

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

    @Override
    public int split(KnowledgeDocument document, DocumentSplitParam documentSplitParam) {
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
            String objectName = resolveObjectName(document.getConvertedDocUrl());
            String markdown;
            try (InputStream inputStream = fileStorageService.downloadFile(objectName)) {
                markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            DocumentSplitter splitter = documentSplitterFactory.get(documentSplitParam);
            Document splitDocument = Document.from(markdown, Metadata.from("docId", String.valueOf(document.getDocId())));
            List<TextSegment> segments = splitter.split(splitDocument);
            List<KnowledgeSegment> knowledgeSegments = toKnowledgeSegments(document, segments);
            if (!knowledgeSegments.isEmpty()) {
                knowledgeSegmentService.saveBatch(knowledgeSegments);
            }

            KnowledgeDocument updateDocument = new KnowledgeDocument();
            updateDocument.setDocId(document.getDocId());
            updateDocument.setStatus(DocumentStatus.CHUNKED);
            knowledgeDocumentService.updateById(updateDocument);
            document.setStatus(DocumentStatus.CHUNKED);
            log.info("Document split completed, docId={}, segmentCount={}", document.getDocId(), knowledgeSegments.size());
            return knowledgeSegments.size();
        } catch (Exception ex) {
            log.error("Document split failed, docId={}", document.getDocId(), ex);
            throw new IllegalStateException("文档切分失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean embedAndStore(KnowledgeDocument document) {
        if (document == null || document.getDocId() == null) {
            throw new IllegalArgumentException("待向量化文档不能为空");
        }
        if (document.getStatus() != DocumentStatus.CHUNKED) {
            throw new IllegalStateException("当前文档状态为 " + document.getStatus() + "，不可向量化");
        }

        log.info("Start embedding document segments, docId={}", document.getDocId());
        try {
            // 只处理已切分且未跳过嵌入的片段，避免重复向量化或处理标题占位片段。
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

            List<KnowledgeSegment> embeddableSegments = filterEmbeddableSegments(document, segments);
            if (embeddableSegments.isEmpty()) {
                log.warn("No valid text segments need embedding, docId={}", document.getDocId());
                return false;
            }

            List<TextSegment> textSegments = embeddableSegments.stream().map(segment -> TextSegment.from(segment.getText(), Metadata.from(segment.getMetadataMap()))).toList();

            log.info("Embedding model request started, docId={}, segmentCount={}", document.getDocId(), textSegments.size());
            Response<List<Embedding>> response = openAiEmbeddingModel.embedAll(textSegments);
            List<Embedding> embeddings = response.content();
            if (embeddings.size() != textSegments.size()) {
                throw new IllegalStateException("向量模型返回数量异常，segmentCount="
                        + textSegments.size() + ", embeddingCount=" + (embeddings == null ? 0 : embeddings.size()));
            }

            // 写入向量库并保存返回的向量记录 ID，后续可以用于检索定位或删除。
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

    private List<KnowledgeSegment> toKnowledgeSegments(KnowledgeDocument document, List<TextSegment> segments) {
        List<KnowledgeSegment> knowledgeSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment textSegment = segments.get(i);
            if (textSegment == null || !StringUtils.hasText(textSegment.text())) {
                continue;
            }
            KnowledgeSegment knowledgeSegment = new KnowledgeSegment();
            knowledgeSegment.setText(textSegment.text());
            knowledgeSegment.setChunkId(textSegment.metadata().getString(MetadataKeyConstant.CHUNK_ID));

            Metadata metadata = textSegment.metadata();
            metadata.put(MetadataKeyConstant.DOC_ID, document.getDocId());
            metadata.put(MetadataKeyConstant.FILE_NAME, document.getDocTitle());
            metadata.put(MetadataKeyConstant.URL, document.getDocUrl());

            knowledgeSegment.setMetadata(JSON.toJSONString(metadata.toMap()));
            knowledgeSegment.setDocumentId(document.getDocId());
            knowledgeSegment.setChunkOrder(i + 1);
            knowledgeSegment.setStatus(SegmentStatus.STORED);

            // 检查是否需要跳过嵌入
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
