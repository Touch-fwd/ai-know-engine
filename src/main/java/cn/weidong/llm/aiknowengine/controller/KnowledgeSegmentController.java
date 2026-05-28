package cn.weidong.llm.aiknowengine.controller;

import cn.weidong.llm.aiknowengine.common.PageResponse;
import cn.weidong.llm.aiknowengine.entity.KnowledgeSegment;
import cn.weidong.llm.aiknowengine.service.KnowledgeSegmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/knowledge-segments")
public class KnowledgeSegmentController {

    private final KnowledgeSegmentService knowledgeSegmentService;

    public KnowledgeSegmentController(KnowledgeSegmentService knowledgeSegmentService) {
        this.knowledgeSegmentService = knowledgeSegmentService;
    }

    @GetMapping
    public PageResponse<KnowledgeSegment> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<KnowledgeSegment> wrapper = new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(documentId != null, KnowledgeSegment::getDocumentId, documentId)
                .eq(status != null && !status.isBlank(), KnowledgeSegment::getStatus, status)
                .orderByAsc(KnowledgeSegment::getDocumentId)
                .orderByAsc(KnowledgeSegment::getChunkOrder);
        Page<KnowledgeSegment> page = knowledgeSegmentService.page(Page.of(current, size), wrapper);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    @GetMapping("/{id}")
    public KnowledgeSegment get(@PathVariable Long id) {
        KnowledgeSegment segment = knowledgeSegmentService.getById(id);
        if (segment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge segment not found");
        }
        return segment;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeSegment create(@Valid @RequestBody KnowledgeSegment segment) {
        segment.setId(null);
        knowledgeSegmentService.save(segment);
        return segment;
    }

    @PutMapping("/{id}")
    public KnowledgeSegment update(@PathVariable Long id, @Valid @RequestBody KnowledgeSegment segment) {
        segment.setId(id);
        boolean updated = knowledgeSegmentService.updateById(segment);
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge segment not found");
        }
        return knowledgeSegmentService.getById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        boolean deleted = knowledgeSegmentService.removeById(id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge segment not found");
        }
    }
}
