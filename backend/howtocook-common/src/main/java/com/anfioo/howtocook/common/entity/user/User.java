package com.anfioo.howtocook.common.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表，对应 {@code "user"}。
 * <p>注意：user 在 PostgreSQL 中是保留字，DDL 与 @TableName 均需双引号包裹。</p>
 */
@Data
@TableName("\"user\"")
public class User {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，唯一 */
    private String username;

    /** BCrypt 密码哈希 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 状态：1 正常 0 禁用 */
    private Integer status;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0 未删 1 已删 */
    @TableLogic
    private Integer deleted;
}
