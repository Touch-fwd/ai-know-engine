package cn.weidong.llm.aiknowengine.document.listener;

import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.config.DocumentAsyncConfig;
import cn.weidong.llm.aiknowengine.document.event.DocumentSplitCompletedEvent;
import cn.weidong.llm.aiknowengine.document.service.DocumentHandleService;
import cn.weidong.llm.aiknowengine.document.service.KnowledgeDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 监听文档切分完成事件，并异步拉起向量化入库流程。
 */
@Component
public class DocumentEmbeddingEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingEventListener.class);

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final DocumentHandleService documentHandleService;

    public DocumentEmbeddingEventListener(KnowledgeDocumentService knowledgeDocumentService,
                                          DocumentHandleService documentHandleService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.documentHandleService = documentHandleService;
    }

    @Async(DocumentAsyncConfig.DOCUMENT_EMBEDDING_TASK_EXECUTOR)
    @EventListener
    public void handleDocumentSplitCompleted(DocumentSplitCompletedEvent event) {
        Long docId = event.docId();
        KnowledgeDocument document = knowledgeDocumentService.getById(docId);
        if (document == null) {
            log.warn("Skip document embedding because document does not exist, docId={}", docId);
            return;
        }

        log.info("Document split event received, start async embedding, docId={}", docId);
        documentHandleService.embedAndStore(document);
    }
}
