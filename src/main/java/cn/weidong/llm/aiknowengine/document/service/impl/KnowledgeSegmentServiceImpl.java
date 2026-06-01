package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.entity.KnowledgeSegment;
import cn.weidong.llm.aiknowengine.document.mapper.KnowledgeSegmentMapper;
import cn.weidong.llm.aiknowengine.document.service.KnowledgeSegmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeSegmentServiceImpl
        extends ServiceImpl<KnowledgeSegmentMapper, KnowledgeSegment>
        implements KnowledgeSegmentService {

    @Override
    public String getTextByChunkId(String chunkId) {
        if (!StringUtils.hasText(chunkId)) {
            return null;
        }
        KnowledgeSegment segment = getOne(new LambdaQueryWrapper<KnowledgeSegment>()
                .select(KnowledgeSegment::getText)
                .eq(KnowledgeSegment::getChunkId, chunkId)
                .last("LIMIT 1"));
        return segment == null ? null : segment.getText();
    }
}
