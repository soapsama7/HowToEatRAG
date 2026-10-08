package com.anfioo.howtocook.app.controller;

import com.anfioo.howtocook.app.aspect.AuditOperation;
import com.anfioo.howtocook.app.dto.CurrentUserResponse;
import com.anfioo.howtocook.app.dto.LoginRequest;
import com.anfioo.howtocook.app.dto.LoginResponse;
import com.anfioo.howtocook.app.dto.RegisterRequest;
import com.anfioo.howtocook.app.service.AuthService;
import com.anfioo.howtocook.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 登出 / 当前用户 / 角色管理。
 * <p>register、login 在拦截器白名单中公开；logout、me 需登录；
 * 用户管理接口（提权/降权、封号/解封）挂在 /api/auth 下，由 SaTokenConfig 显式要求 ROOT 角色（优化 2.2 三档权限）。</p>
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

    /** 提升用户为管理员（仅 ROOT；不可操作自己） */
    @AuditOperation(operation = "GRANT_ADMIN", category = "USER")
    @PostMapping("/users/{userId}/role")
    public Result<Void> grantAdmin(@PathVariable long userId) {
        authService.grantAdmin(userId);
        return Result.ok();
    }

    /** 降级用户为普通用户（仅 ROOT；不可操作自己） */
    @AuditOperation(operation = "REVOKE_ADMIN", category = "USER")
    @DeleteMapping("/users/{userId}/role")
    public Result<Void> revokeAdmin(@PathVariable long userId) {
        authService.revokeAdmin(userId);
        return Result.ok();
    }

    /** 封禁用户（仅 ROOT） */
    @AuditOperation(operation = "BAN_USER", category = "USER")
    @PostMapping("/users/{userId}/ban")
    public Result<Void> banUser(@PathVariable long userId) {
        authService.banUser(userId);
        return Result.ok();
    }

    /** 解封用户（仅 ROOT） */
    @AuditOperation(operation = "UNBAN_USER", category = "USER")
    @DeleteMapping("/users/{userId}/ban")
    public Result<Void> unbanUser(@PathVariable long userId) {
        authService.unbanUser(userId);
        return Result.ok();
    }
}
