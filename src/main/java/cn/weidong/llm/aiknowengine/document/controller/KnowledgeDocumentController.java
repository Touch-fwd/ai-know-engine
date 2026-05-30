package cn.weidong.llm.aiknowengine.document.controller;

import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.service.DocumentHandleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge-documents")
public class KnowledgeDocumentController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentController.class);

    private final DocumentHandleService documentHandleService;

    public KnowledgeDocumentController(DocumentHandleService documentHandleService) {
        this.documentHandleService = documentHandleService;
    }

    /**
     * 上传知识文档。
     * <p>
     * 文件会先保存到 MinIO 并落库为 UPLOADED 状态；如果识别为 PDF，则立即触发 MinerU 解析，
     * 解析完成后返回带有 convertedDocUrl 的文档信息。
     *
     * @param file 上传文件
     * @param uploadUser 上传用户
     * @param accessibleBy 可见范围
     * @return 文档记录
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeDocument upload(@RequestParam MultipartFile file,
                                    @RequestParam(required = false) String uploadUser,
                                    @RequestParam(required = false) String accessibleBy) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        try {
            return documentHandleService.upload(file, uploadUser, accessibleBy);
        } catch (Exception ex) {
            log.error("Knowledge document upload failed, fileName={}, uploadUser={}", originalFilename, uploadUser, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
