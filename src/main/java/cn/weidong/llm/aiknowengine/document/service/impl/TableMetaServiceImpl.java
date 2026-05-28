package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.entity.TableMeta;
import cn.weidong.llm.aiknowengine.document.mapper.TableMetaMapper;
import cn.weidong.llm.aiknowengine.document.service.TableMetaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TableMetaServiceImpl extends ServiceImpl<TableMetaMapper, TableMeta> implements TableMetaService {
}
