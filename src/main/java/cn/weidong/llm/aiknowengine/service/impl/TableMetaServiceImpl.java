package cn.weidong.llm.aiknowengine.service.impl;

import cn.weidong.llm.aiknowengine.entity.TableMeta;
import cn.weidong.llm.aiknowengine.mapper.TableMetaMapper;
import cn.weidong.llm.aiknowengine.service.TableMetaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TableMetaServiceImpl extends ServiceImpl<TableMetaMapper, TableMeta> implements TableMetaService {
}
