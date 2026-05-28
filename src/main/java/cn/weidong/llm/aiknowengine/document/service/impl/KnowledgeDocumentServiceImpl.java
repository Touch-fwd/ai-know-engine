package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.mapper.KnowledgeDocumentMapper;
import cn.weidong.llm.aiknowengine.document.service.KnowledgeDocumentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeDocumentServiceImpl
        extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {
}
