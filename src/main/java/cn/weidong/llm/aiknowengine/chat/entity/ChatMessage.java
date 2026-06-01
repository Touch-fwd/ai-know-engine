package cn.weidong.llm.aiknowengine.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 消息唯一标识 */
    @NotBlank
    @Size(max = 64)
    @TableField("message_id")
    private String messageId;

    /** 所属会话ID */
    @NotBlank
    @Size(max = 64)
    @TableField("conversation_id")
    private String conversationId;

    /** 角色：USER/ASSISTANT */
    @NotBlank
    @Size(max = 32)
    @TableField("type")
    private String type;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 改写后的内容 */
    @TableField("transform_content")
    private String transformContent;

    /** Token数量 */
    @TableField("token_count")
    private Integer tokenCount;

    /** 使用的模型名称 */
    @Size(max = 128)
    @TableField("model_name")
    private String modelName;

    /** RAG引用内容JSON数组 */
    @TableField("rag_references")
    private String ragReferences;

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

    /** 扩展元数据JSON格式 */
    @TableField("metadata")
    private String metadata;
}
