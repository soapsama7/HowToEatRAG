package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.app.dto.CurrentUserResponse;
import com.anfioo.howtocook.app.dto.LoginRequest;
import com.anfioo.howtocook.app.dto.LoginResponse;
import com.anfioo.howtocook.app.dto.RegisterRequest;
import com.anfioo.howtocook.common.entity.user.Role;
import com.anfioo.howtocook.common.entity.user.User;
import com.anfioo.howtocook.common.entity.user.UserRole;
import com.anfioo.howtocook.common.enums.user.RoleCode;
import com.anfioo.howtocook.common.mapper.user.RoleMapper;
import com.anfioo.howtocook.common.mapper.user.UserMapper;
import com.anfioo.howtocook.common.mapper.user.UserRoleMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务：注册 / 登录 / 登出 / 当前用户。
 * <p>安全约定：密码仅存 BCrypt 哈希；登录失败统一提示"用户名或密码错误"，不泄露用户是否存在。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 注册：用户名唯一校验 → BCrypt 加密入库 → 绑定默认 USER 角色。
     */
    public Long register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已被占用");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null ? request.getUsername() : request.getNickname());
        user.setStatus(1);
        userMapper.insert(user);

        bindRole(user.getId(), RoleCode.USER.name());
        log.info("新用户注册: userId={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    /**
     * 登录：校验密码（失败提示不区分用户不存在/密码错误）→ StpUtil.login 以 userId 为会话主体。
     */
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        StpUtil.login(user.getId());
        // 会话中冗余用户名，供审计日志切面取用（避免每次审计查库）
        StpUtil.getSession().set("username", user.getUsername());
        return LoginResponse.builder()
                .token(StpUtil.getTokenValue())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    /** 登出（使当前 token 失效，会话数据随之从 Redis 清除） */
    public void logout() {
        StpUtil.logout();
    }

    /** 当前登录用户信息 + 角色 */
    public CurrentUserResponse currentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return CurrentUserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(getRoleCodes(userId))
                .build();
    }

    /**
     * 查询用户角色编码（RBAC 数据源，供 StpInterfaceImpl 与 /me 使用）。
     */
    public List<String> getRoleCodes(long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(role -> role.getCode())
                .toList();
    }

    /** 给用户绑定角色（按角色编码） */
    private void bindRole(long userId, String roleCode) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, roleCode));
        if (role == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "角色种子数据缺失: " + roleCode);
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }
}
