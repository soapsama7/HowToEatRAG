package com.anfioo.howtocook.app.controller;

import com.anfioo.howtocook.app.dto.CurrentUserResponse;
import com.anfioo.howtocook.app.dto.LoginRequest;
import com.anfioo.howtocook.app.dto.LoginResponse;
import com.anfioo.howtocook.app.dto.RegisterRequest;
import com.anfioo.howtocook.app.service.AuthService;
import com.anfioo.howtocook.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 登出 / 当前用户。
 * <p>register、login 在拦截器白名单中公开；logout、me 需登录。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册（用户名唯一，默认 USER 角色），返回用户 ID */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /** 登录，返回 token 与基础用户信息 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 登出（使当前 token 失效） */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    /** 当前登录用户信息 + 角色 */
    @GetMapping("/me")
    public Result<CurrentUserResponse> me() {
        return Result.ok(authService.currentUser());
    }
}
