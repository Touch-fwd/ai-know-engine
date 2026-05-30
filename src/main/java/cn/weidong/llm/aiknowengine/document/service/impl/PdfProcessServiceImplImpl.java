package cn.weidong.llm.aiknowengine.document.service.impl;

import cn.weidong.llm.aiknowengine.document.constant.FileType;
import cn.weidong.llm.aiknowengine.document.service.MinerUProcessBaseServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class PdfProcessServiceImplImpl extends MinerUProcessBaseServiceImpl {
    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.PDF;
    }
}
