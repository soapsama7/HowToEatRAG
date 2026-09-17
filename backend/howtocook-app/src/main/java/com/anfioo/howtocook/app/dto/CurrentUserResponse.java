package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 当前用户信息（GET /api/auth/me）。
 */
@Getter
@Builder
public class CurrentUserResponse {

    /** 用户 ID */
    private final Long userId;

    /** 用户名 */
    private final String username;

    /** 昵称 */
    private final String nickname;

    /** 角色编码列表（USER / ADMIN） */
    private final List<String> roles;
}
