package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.constant.SplitType;
import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.param.DocumentSplitParam;
import cn.weidong.llm.aiknowengine.document.service.DocumentHandleService;
import cn.weidong.llm.aiknowengine.document.service.KnowledgeDocumentService;
import cn.weidong.llm.aiknowengine.document.service.KnowledgeSegmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class DocumentHandleServiceImplTest {

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @Autowired
    private KnowledgeSegmentService knowledgeSegmentService;

    @Autowired
    DocumentHandleService documentHandleService;

    @Test
    void embedAndStore() {

        KnowledgeDocument knowledgeDocument = knowledgeDocumentService.getById(15);

        DocumentSplitParam documentSplitParam = new DocumentSplitParam(SplitType.SMART, 1000, 100, null, null, null);

        documentHandleService.split(knowledgeDocument,documentSplitParam);

//        documentHandleService.embedAndStore(knowledgeDocument);


    }
}