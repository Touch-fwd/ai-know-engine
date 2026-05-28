package cn.weidong.llm.aiknowengine.controller;

import cn.weidong.llm.aiknowengine.common.PageResponse;
import cn.weidong.llm.aiknowengine.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.service.KnowledgeDocumentService;
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
@RequestMapping("/api/knowledge-documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @GetMapping
    public PageResponse<KnowledgeDocument> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(status != null && !status.isBlank(), KnowledgeDocument::getStatus, status)
                .orderByDesc(KnowledgeDocument::getCreatedAt)
                .orderByDesc(KnowledgeDocument::getDocId);
        Page<KnowledgeDocument> page = knowledgeDocumentService.page(Page.of(current, size), wrapper);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    @GetMapping("/{docId}")
    public KnowledgeDocument get(@PathVariable Long docId) {
        KnowledgeDocument document = knowledgeDocumentService.getById(docId);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found");
        }
        return document;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocument create(@Valid @RequestBody KnowledgeDocument document) {
        document.setDocId(null);
        knowledgeDocumentService.save(document);
        return document;
    }

    @PutMapping("/{docId}")
    public KnowledgeDocument update(@PathVariable Long docId, @Valid @RequestBody KnowledgeDocument document) {
        document.setDocId(docId);
        boolean updated = knowledgeDocumentService.updateById(document);
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found");
        }
        return knowledgeDocumentService.getById(docId);
    }

    @DeleteMapping("/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long docId) {
        boolean deleted = knowledgeDocumentService.removeById(docId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found");
        }
    }
}
