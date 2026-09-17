package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 登录响应体：token + 基础用户信息。
 * <p>token 放在响应体中，前端后续请求以 {@code Authorization: <token>} 请求头携带（与 sa-token.token-name 一致）。</p>
 */
@Getter
@Builder
public class LoginResponse {

    /** Sa-Token 会话 token */
    private final String token;

    /** 用户 ID（即会话 loginId） */
    private final Long userId;

    /** 用户名 */
    private final String username;

    /** 昵称 */
    private final String nickname;
}
