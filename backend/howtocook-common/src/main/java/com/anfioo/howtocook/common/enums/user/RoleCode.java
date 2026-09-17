package com.anfioo.howtocook.common.enums.user;

import lombok.Getter;

/**
 * 角色编码，对应 {@code role.code} 字段（RBAC）。
 */
@Getter
public enum RoleCode {

    /** 普通用户 */
    USER("普通用户"),
    /** 管理员（可访问 /api/admin/**） */
    ADMIN("管理员");

    private final String desc;

    RoleCode(String desc) {
        this.desc = desc;
    }
}
