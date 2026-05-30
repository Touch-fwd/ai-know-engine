package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.constant.DocumentStatus;
import cn.weidong.llm.aiknowengine.document.constant.FileType;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.service.*;
import cn.weidong.llm.aiknowengine.document.util.FileTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 默认文档处理服务实现。
 */
@Service
public class DocumentHandleServiceImpl implements DocumentHandleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentHandleServiceImpl.class);

    @Autowired
    private  KnowledgeDocumentService knowledgeDocumentService;
    @Autowired
    private  FileStorageService fileStorageService;
    @Autowired
    private FileProcessServiceFactory fileProcessServiceFactory;

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
}
