package cn.weidong.llm.aiknowengine.document.controller;

import cn.weidong.llm.aiknowengine.common.PageResponse;
import cn.weidong.llm.aiknowengine.document.entity.TableMeta;
import cn.weidong.llm.aiknowengine.document.service.TableMetaService;
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
@RequestMapping("/api/table-metas")
public class TableMetaController {

    private final TableMetaService tableMetaService;

    public TableMetaController(TableMetaService tableMetaService) {
        this.tableMetaService = tableMetaService;
    }

    @GetMapping
    public PageResponse<TableMeta> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String tableName) {
        LambdaQueryWrapper<TableMeta> wrapper = new LambdaQueryWrapper<TableMeta>()
                .like(tableName != null && !tableName.isBlank(), TableMeta::getTableName, tableName)
                .orderByDesc(TableMeta::getCreatedAt)
                .orderByDesc(TableMeta::getId);
        Page<TableMeta> page = tableMetaService.page(Page.of(current, size), wrapper);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    @GetMapping("/{id}")
    public TableMeta get(@PathVariable Long id) {
        TableMeta tableMeta = tableMetaService.getById(id);
        if (tableMeta == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table meta not found");
        }
        return tableMeta;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableMeta create(@Valid @RequestBody TableMeta tableMeta) {
        tableMeta.setId(null);
        tableMetaService.save(tableMeta);
        return tableMeta;
    }

    @PutMapping("/{id}")
    public TableMeta update(@PathVariable Long id, @Valid @RequestBody TableMeta tableMeta) {
        tableMeta.setId(id);
        boolean updated = tableMetaService.updateById(tableMeta);
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table meta not found");
        }
        return tableMetaService.getById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        boolean deleted = tableMetaService.removeById(id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table meta not found");
        }
    }
}
