//package cn.weidong.llm.aiknowengine.rag.modules;
//
//import dev.langchain4j.data.document.Document;
//import dev.langchain4j.data.document.DocumentSplitter;
//import dev.langchain4j.data.document.Metadata;
//import dev.langchain4j.data.segment.TextSegment;
//
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * 按文本长度切分文档的简单切分器。
// * <p>
// * 类名沿用业务语义中的 WordSplitter，但这里按字符窗口切分，避免中文文本没有空格时无法切分。
// */
//public class DocumentByWordSplitter implements DocumentSplitter {
//
//    private final int chunkSize;
//    private final int overlap;
//
//    public DocumentByWordSplitter(int chunkSize, int overlap) {
//        this.chunkSize = Math.max(chunkSize, 1);
//        this.overlap = Math.max(Math.min(overlap, this.chunkSize - 1), 0);
//    }
//
//    @Override
//    public List<TextSegment> split(Document document) {
//        String text = document.text();
//        Metadata metadata = document.metadata();
//        List<TextSegment> segments = new ArrayList<>();
//        if (text == null || text.isBlank()) {
//            return segments;
//        }
//
//        int start = 0;
//        while (start < text.length()) {
//            int end = Math.min(start + chunkSize, text.length());
//            segments.add(TextSegment.from(text.substring(start, end), metadata.copy()));
//            if (end == text.length()) {
//                break;
//            }
//            start = end - overlap;
//        }
//        return segments;
//    }
//}
