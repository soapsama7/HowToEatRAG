package com.anfioo.howtocook.common.enums.user;

import lombok.Getter;

/**
 * 角色编码，对应 {@code role.code} 字段（RBAC）。
 */
@Getter
public enum RoleCode {

    /** 普通用户 */
    USER("普通用户"),
    /** 管理员（可访问 /api/admin/** 管理端接口） */
    ADMIN("管理员"),
    /** 超级管理员（继承 ADMIN，另可提权/降权 admin、封号/解封、查看 agent 日志） */
    ROOT("超级管理员");

    private final String desc;

    RoleCode(String desc) {
        this.desc = desc;
    }
}
