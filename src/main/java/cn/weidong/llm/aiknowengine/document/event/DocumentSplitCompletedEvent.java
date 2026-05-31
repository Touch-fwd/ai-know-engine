package cn.weidong.llm.aiknowengine.document.event;

/**
 * 文档切分完成事件。
 *
 * @param docId 完成切分的文档 ID
 */
public record DocumentSplitCompletedEvent(Long docId) {
}
