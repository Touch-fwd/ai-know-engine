package cn.weidong.llm.aiknowengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@TableName("table_meta")
public class TableMeta {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 表名 */
    @NotBlank
    @Size(max = 128)
    @TableField("table_name")
    private String tableName;

    /** 表描述 */
    @Size(max = 512)
    @TableField("description")
    private String description;

    /** 建表语句 */
    @TableField("create_sql")
    private String createSql;

    /** 字段信息（JSON格式） */
    @TableField("columns_info")
    private String columnsInfo;

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

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreateSql() {
        return createSql;
    }

    public void setCreateSql(String createSql) {
        this.createSql = createSql;
    }

    public String getColumnsInfo() {
        return columnsInfo;
    }

    public void setColumnsInfo(String columnsInfo) {
        this.columnsInfo = columnsInfo;
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
