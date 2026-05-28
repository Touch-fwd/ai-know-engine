package cn.weidong.llm.aiknowengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

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
    private String status;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkOrder() {
        return chunkOrder;
    }

    public void setChunkOrder(Integer chunkOrder) {
        this.chunkOrder = chunkOrder;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    public void setEmbeddingId(String embeddingId) {
        this.embeddingId = embeddingId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSkipEmbedding() {
        return skipEmbedding;
    }

    public void setSkipEmbedding(Integer skipEmbedding) {
        this.skipEmbedding = skipEmbedding;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
