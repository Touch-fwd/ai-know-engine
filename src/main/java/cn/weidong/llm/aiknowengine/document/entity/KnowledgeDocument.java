package cn.weidong.llm.aiknowengine.document.entity;

import cn.weidong.llm.aiknowengine.document.constant.DocumentStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    /** 文档ID */
    @TableId(value = "doc_id", type = IdType.AUTO)
    private Long docId;

    /** 文档标题 */
    @NotBlank
    @Size(max = 1024)
    @TableField("doc_title")
    private String docTitle;

    /** 上传用户 */
    @TableField("upload_user")
    private String uploadUser;

    /** 文档URL */
    @TableField("doc_url")
    private String docUrl;

    /** 转换后的文档URL */
    @TableField("converted_doc_url")
    private String convertedDocUrl;

    /** 文档失效日期 */
    @TableField("expire_date")
    private LocalDate expireDate;

    /** 状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED */
    @NotBlank
    @Size(max = 32)
    @TableField("status")
    private DocumentStatus status;

    /** 可见范围 */
    @TableField("accessible_by")
    private String accessibleBy;

    /** 文档描述 */
    @TableField("description")
    private String description;

    /** 知识库类型：DOCUMENT_SEARCH, DATA_QUERY */
    @TableField("knowledge_base_type")
    private String knowledgeBaseType;

    /** 扩展字段，保存JSON字符串 */
    @TableField("extension")
    private String extension;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 修改时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 乐观锁版本号 */
    @Version
    @TableField("lock_version")
    private Integer lockVersion;

    /** 是否删除：0-未删除，1-已删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
