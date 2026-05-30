package cn.weidong.llm.aiknowengine.document.service;

import cn.weidong.llm.aiknowengine.document.constant.FileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileProcessServiceFactory {
    @Autowired
    private List<FileProcessService> fileProcessServiceList;

    public FileProcessService get(FileType fileProcessType) {
        return fileProcessServiceList.stream()
                .filter(service -> service.supports(fileProcessType))
                .findFirst().orElse(null);
    }
}
