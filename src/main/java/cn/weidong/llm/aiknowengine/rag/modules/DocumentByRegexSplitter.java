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
// * 先按正则切分，再按 chunkSize 聚合的文档切分器。
// */
//public class DocumentByRegexSplitter implements DocumentSplitter {
//
//    private final String regex;
//    private final String joinSeparator;
//    private final int chunkSize;
//    private final int overlap;
//
//    public DocumentByRegexSplitter(String regex, String joinSeparator, int chunkSize, int overlap) {
//        this.regex = regex;
//        this.joinSeparator = joinSeparator == null ? "\n\n" : joinSeparator;
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
//        if (regex == null || regex.isBlank()) {
//            return new DocumentByWordSplitter(chunkSize, overlap).split(document);
//        }
//
//        String[] parts = text.split(regex);
//        StringBuilder current = new StringBuilder();
//        for (String part : parts) {
//            if (part == null || part.isBlank()) {
//                continue;
//            }
//            String normalizedPart = part.trim();
//            int nextLength = current.isEmpty()
//                    ? normalizedPart.length()
//                    : current.length() + joinSeparator.length() + normalizedPart.length();
//            if (!current.isEmpty() && nextLength > chunkSize) {
//                addWindowedSegments(segments, current.toString(), metadata);
//                current.setLength(0);
//            }
//            if (!current.isEmpty()) {
//                current.append(joinSeparator);
//            }
//            current.append(normalizedPart);
//        }
//        if (!current.isEmpty()) {
//            addWindowedSegments(segments, current.toString(), metadata);
//        }
//        return segments;
//    }
//
//    private void addWindowedSegments(List<TextSegment> segments, String text, Metadata metadata) {
//        if (text.length() <= chunkSize) {
//            segments.add(TextSegment.from(text, metadata.copy()));
//            return;
//        }
//        segments.addAll(new DocumentByWordSplitter(chunkSize, overlap)
//                .split(Document.from(text, metadata.copy())));
//    }
//}
