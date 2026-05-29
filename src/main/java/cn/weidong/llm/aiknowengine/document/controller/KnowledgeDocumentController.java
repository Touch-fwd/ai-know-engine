package cn.weidong.llm.aiknowengine.document.controller;

import cn.weidong.llm.aiknowengine.document.constant.DocumentStatus;
import cn.weidong.llm.aiknowengine.document.constant.FileType;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.service.FileStorageService;
import cn.weidong.llm.aiknowengine.document.service.KnowledgeDocumentService;
import cn.weidong.llm.aiknowengine.document.service.MinerUProcessBaseService;
import cn.weidong.llm.aiknowengine.document.util.FileTypeUtil;
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

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final FileStorageService fileStorageService;
    private final MinerUProcessBaseService minerUProcessBaseService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService,
                                       FileStorageService fileStorageService,
                                       MinerUProcessBaseService minerUProcessBaseService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.fileStorageService = fileStorageService;
        this.minerUProcessBaseService = minerUProcessBaseService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeDocument upload(@RequestParam MultipartFile file,
                                    @RequestParam(required = false) String uploadUser,
                                    @RequestParam(required = false) String accessibleBy) {
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

            boolean pdf = FileTypeUtil.isFileType(originalFilename, file, FileType.PDF);
            log.info("Knowledge document file type checked, docId={}, fileName={}, isPdf={}",
                    document.getDocId(), originalFilename, pdf);
            if (pdf) {
                log.info("Start MinerU parsing for PDF document, docId={}, fileName={}",
                        document.getDocId(), originalFilename);
                KnowledgeDocument convertedDocument = minerUProcessBaseService.process(document, file.getInputStream());
                log.info("MinerU parsing completed for PDF document, docId={}, status={}, convertedDocUrl={}",
                        convertedDocument.getDocId(), convertedDocument.getStatus(), convertedDocument.getConvertedDocUrl());
                return convertedDocument;
            }
            log.info("Knowledge document upload completed without MinerU parsing, docId={}, fileName={}",
                    document.getDocId(), originalFilename);
            return document;
        } catch (Exception ex) {
            log.error("Knowledge document upload failed, fileName={}, uploadUser={}", originalFilename, uploadUser, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
