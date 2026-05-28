package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.entity.KnowledgeSegment;
import cn.weidong.llm.aiknowengine.document.mapper.KnowledgeSegmentMapper;
import cn.weidong.llm.aiknowengine.document.service.KnowledgeSegmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSegmentServiceImpl
        extends ServiceImpl<KnowledgeSegmentMapper, KnowledgeSegment>
        implements KnowledgeSegmentService {
}
