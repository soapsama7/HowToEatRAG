package com.anfioo.howtocook.common.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色表，对应 {@code role}。种子数据由 V2__seed.sql 提供（Step 1.2）。
 */
@Data
@TableName("role")
public class Role {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码：USER / ADMIN（见 RoleCode） */
    private String code;

    /** 角色名 */
    private String name;

    /** 角色描述 */
    private String description;
}
