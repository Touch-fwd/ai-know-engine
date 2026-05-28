package cn.weidong.llm.aiknowengine.service.impl;

import cn.weidong.llm.aiknowengine.entity.KnowledgeSegment;
import cn.weidong.llm.aiknowengine.mapper.KnowledgeSegmentMapper;
import cn.weidong.llm.aiknowengine.service.KnowledgeSegmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSegmentServiceImpl
        extends ServiceImpl<KnowledgeSegmentMapper, KnowledgeSegment>
        implements KnowledgeSegmentService {
}
