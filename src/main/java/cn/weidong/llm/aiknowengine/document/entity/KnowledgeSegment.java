package cn.weidong.llm.aiknowengine.document.entity;

import cn.weidong.llm.aiknowengine.document.constant.SegmentStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_segment")
public class KnowledgeSegment {

    /** 片段ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文本内容 */
    @NotBlank
    @TableField("text")
    private String text;

    /** 分片ID */
    @TableField("chunk_id")
    private String chunkId;

    /** 元数据 */
    @TableField("metadata")
    private String metadata;

    /** 所属文档ID */
    @NotNull
    @TableField("document_id")
    private Long documentId;

    /** 顺序 */
    @NotNull
    @TableField("chunk_order")
    private Integer chunkOrder;

    /** 嵌入ID */
    @TableField("embedding_id")
    private String embeddingId;

    /** 状态：STORED, VECTOR_STORED */
    @TableField("status")
    private SegmentStatus status;

    /** 是否跳过嵌入生成 */
    @TableField("skip_embedding")
    private Integer skipEmbedding;

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
