package com.anfioo.howtocook.common.entity.sys;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志表，对应 {@code audit_log}（由 @AuditOperation 切面异步入库，Step 1.3）。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户 ID */
    private Long userId;

    /** 操作用户名（冗余，防止用户删除后无法溯源） */
    private String username;

    /** 操作名，如 UPLOAD_DOCUMENT */
    private String operation;

    /** 请求方法与路径 */
    private String method;

    /** 入参摘要（截断 500 字符，脱敏） */
    private String params;

    /** 结果：SUCCESS / FAIL */
    private String result;

    /** 客户端 IP */
    private String ip;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
