package cn.weidong.llm.aiknowengine.document.service;

import cn.weidong.llm.aiknowengine.document.entity.KnowledgeDocument;
import cn.weidong.llm.aiknowengine.document.param.DocumentSplitParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档处理服务入口。
 * <p>
 * 用于承接文档上传后的解析、转换、分块等处理流程。
 */
public interface DocumentHandleService {

    /**
     * 上传并转换文档。
     * <p>
     * 文件会先上传到 MinIO 并保存文档记录；如果是 PDF，则继续调用 MinerU 完成转换，
     * 最终返回转换后的文档信息。
     *
     * @param file 上传文件
     * @param uploadUser 上传用户
     * @param accessibleBy 可见范围
     * @return 文档记录
     */
    KnowledgeDocument upload(MultipartFile file, String uploadUser, String accessibleBy);

    /**
     * 切分已转换完成的文档。
     *
     * @param document 文档记录，状态必须为 CONVERTED
     * @param documentSplitParam 切分参数
     * @return 切分片段数量
     */
    int split(KnowledgeDocument document, DocumentSplitParam documentSplitParam);
}
