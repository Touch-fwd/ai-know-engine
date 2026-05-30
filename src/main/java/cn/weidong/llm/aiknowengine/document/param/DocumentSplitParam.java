package cn.weidong.llm.aiknowengine.document.param;

import cn.weidong.llm.aiknowengine.document.constant.SplitType;

/**
 * 文档切分参数。
 *
 * @param splitType 切分类型：TITLE、LENGTH、SEPARATOR、REGEX、SMART
 * @param chunkSize 分片大小
 * @param overlap 分片重叠大小
 * @param titleLevel 标题层级，预留给标题切分策略扩展
 * @param separator 分隔符
 * @param regex 正则表达式
 */
public record DocumentSplitParam(
        SplitType splitType,
        Integer chunkSize,
        Integer overlap,
        Integer titleLevel,
        String separator,
        String regex
) {
}
