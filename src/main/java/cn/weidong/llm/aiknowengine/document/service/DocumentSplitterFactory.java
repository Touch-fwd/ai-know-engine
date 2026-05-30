package cn.weidong.llm.aiknowengine.document.service;

import cn.weidong.llm.aiknowengine.document.constant.SplitType;
import cn.weidong.llm.aiknowengine.document.param.DocumentSplitParam;
import cn.weidong.llm.aiknowengine.rag.modules.DocumentByRegexSplitter;
import cn.weidong.llm.aiknowengine.rag.modules.DocumentByWordSplitter;
import cn.weidong.llm.aiknowengine.rag.modules.MarkdownHeaderParentTextSplitter;
import dev.langchain4j.data.document.DocumentSplitter;
import org.springframework.stereotype.Component;

/**
 * 文档切分器工厂。
 */
@Component
public class DocumentSplitterFactory {

    public DocumentSplitter get(DocumentSplitParam param) {
        int chunkSize = valueOrDefault(param == null ? null : param.chunkSize(), 1000);
        int overlap = valueOrDefault(param == null ? null : param.overlap(), 100);
        SplitType splitType = param == null || param.splitType() == null
                ? SplitType.SMART
                : param.splitType();

        return switch (splitType) {
            case TITLE -> new MarkdownHeaderParentTextSplitter(chunkSize, overlap);
            case LENGTH -> new DocumentByWordSplitter(chunkSize, overlap);
            case SEPARATOR -> new DocumentByRegexSplitter(param.separator(), "\\n\\n", chunkSize, overlap);
            case REGEX -> new DocumentByRegexSplitter(param.regex(), "\\n\\n", chunkSize, overlap);
            case SMART -> new MarkdownHeaderParentTextSplitter(chunkSize, (int) (chunkSize * 0.1));
        };
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return value;
    }
}
