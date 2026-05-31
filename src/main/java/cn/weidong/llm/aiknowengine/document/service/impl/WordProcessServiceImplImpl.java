package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.constant.FileType;
import cn.weidong.llm.aiknowengine.document.constant.KnowledgeBaseType;
import cn.weidong.llm.aiknowengine.document.service.MinerUProcessBaseServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class WordProcessServiceImplImpl extends MinerUProcessBaseServiceImpl {

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        return fileType == FileType.DOC && knowledgeBaseType == KnowledgeBaseType.DOCUMENT_SEARCH;
    }
}
