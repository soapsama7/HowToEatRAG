package com.anfioo.howtocook.common.entity.sys;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 索引任务表，对应 {@code index_task}（RocketMQ 异步索引的状态机载体，Step 2.3）。
 */
@Data
@TableName("index_task")
public class IndexTask {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号（UUID，全局唯一；消费幂等锁的 key） */
    private String taskNo;

    /** 关联文档 ID */
    private Long docId;

    /** 任务类型：INDEX / REINDEX / DELETE_CLEAN（见 IndexTaskType） */
    private String taskType;

    /** 任务状态：PENDING / PROCESSING / SUCCESS / FAILED（见 IndexTaskStatus） */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    /** 重试次数 */
    private Integer retryCount;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
