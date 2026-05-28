package cn.weidong.llm.aiknowengine.service.impl;

import cn.weidong.llm.aiknowengine.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.mapper.KnowledgeDocumentMapper;
import cn.weidong.llm.aiknowengine.service.KnowledgeDocumentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeDocumentServiceImpl
        extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {
}
