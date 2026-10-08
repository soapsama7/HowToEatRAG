package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户列表项（GET /api/auth/users，仅 ROOT）。
 */
@Getter
@Builder
public class UserListResponse {

    /** 用户 ID */
    private final Long userId;

    /** 用户名 */
    private final String username;

    /** 昵称 */
    private final String nickname;

    /** 状态：1 正常 0 禁用 */
    private final Integer status;

    /** 角色编码列表（USER / ADMIN / ROOT） */
    private final List<String> roles;

    /** 创建时间 */
    private final LocalDateTime createdAt;
}
