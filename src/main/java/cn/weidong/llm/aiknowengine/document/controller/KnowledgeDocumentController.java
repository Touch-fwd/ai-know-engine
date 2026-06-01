package cn.weidong.llm.aiknowengine.document.controller;

import cn.weidong.llm.aiknowengine.document.entity.DocumentUploadParam;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.param.DocumentSplitParam;
import cn.weidong.llm.aiknowengine.document.service.DocumentHandleService;
import cn.weidong.llm.aiknowengine.document.service.KnowledgeDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-documents")
public class KnowledgeDocumentController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentController.class);

    private final DocumentHandleService documentHandleService;
    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentController(DocumentHandleService documentHandleService,
                                       KnowledgeDocumentService knowledgeDocumentService) {
        this.documentHandleService = documentHandleService;
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    /**
     * 上传知识文档。
     * <p>
     * 文件会先保存到 MinIO 并落库为 UPLOADED 状态；如果识别为 PDF，则立即触发 MinerU 解析，
     * 解析完成后返回带有 convertedDocUrl 的文档信息。
     *
     * @param file 上传文件
     * @param uploadUser 上传用户
     * @param title 文档标题
     * @param accessibleBy 可见范围
     * @param description 文档描述
     * @param knowledgeBaseType 知识库类型
     * @param tableName 数据查询表名
     * @return 文档记录
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeDocument upload(@RequestParam MultipartFile file,
                                    @RequestParam(required = false) String uploadUser,
                                    @RequestParam(required = false) String title,
                                    @RequestParam(required = false) String accessibleBy,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String knowledgeBaseType,
                                    @RequestParam(required = false) String tableName) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        DocumentUploadParam documentUploadParam = new DocumentUploadParam(
                file, uploadUser, title, accessibleBy, description, knowledgeBaseType, tableName);
        try {
            return documentHandleService.upload(documentUploadParam);
        } catch (Exception ex) {
            log.error("Knowledge document upload failed, fileName={}, uploadUser={}", originalFilename, uploadUser, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 切分知识文档。
     *
     * @param docId 文档 ID
     * @param documentSplitParam 切分参数
     * @return 切分结果
     */
    @PostMapping(value = "/{docId}/split", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> split(@PathVariable Long docId,
                                     @RequestBody(required = false) DocumentSplitParam documentSplitParam) {
        KnowledgeDocument document = getDocument(docId);
        try {
            int segmentCount = documentHandleService.split(document, documentSplitParam);
            return Map.of(
                    "docId", docId,
                    "segmentCount", segmentCount
            );
        } catch (Exception ex) {
            log.error("Knowledge document split failed, docId={}", docId, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 向量化并存储知识文档片段。
     *
     * @param docId 文档 ID
     * @return 向量化结果
     */
    @PostMapping("/{docId}/embed-and-store")
    public Map<String, Object> embedAndStore(@PathVariable Long docId) {
        KnowledgeDocument document = getDocument(docId);
        try {
            boolean stored = documentHandleService.embedAndStore(document);
            return Map.of(
                    "docId", docId,
                    "stored", stored
            );
        } catch (Exception ex) {
            log.error("Knowledge document embedding failed, docId={}", docId, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private KnowledgeDocument getDocument(Long docId) {
        KnowledgeDocument document = knowledgeDocumentService.getById(docId);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在: " + docId);
        }
        return document;
    }
}
