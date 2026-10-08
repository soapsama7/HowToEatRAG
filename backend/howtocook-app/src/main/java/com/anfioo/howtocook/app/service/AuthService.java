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

    /**
     * 提升为管理员（Review 修订 R4）：
     * 目标用户须存在；不可操作自己（防误降级后无可用管理员）；已具备 ADMIN 视为重复操作报 400。
     */
    public void grantAdmin(long targetUserId) {
        requireManageable(targetUserId);
        if (hasRole(targetUserId, RoleCode.ADMIN.name())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户已是管理员");
        }
        bindRole(targetUserId, RoleCode.ADMIN.name());
        log.info("用户已提升为管理员: targetUserId={}, operator={}", targetUserId, StpUtil.getLoginIdAsLong());
    }

    /**
     * 降级为普通用户（Review 修订 R4）：移除 ADMIN 绑定；若 USER 绑定缺失则补绑。
     * 不具备 ADMIN 视为重复操作报 400。
     */
    public void revokeAdmin(long targetUserId) {
        requireManageable(targetUserId);
        if (!hasRole(targetUserId, RoleCode.ADMIN.name())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户不是管理员");
        }
        Role adminRole = requireRole(RoleCode.ADMIN.name());
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, targetUserId)
                .eq(UserRole::getRoleId, adminRole.getId()));
        if (!hasRole(targetUserId, RoleCode.USER.name())) {
            bindRole(targetUserId, RoleCode.USER.name());
        }
        log.info("用户已降级为普通用户: targetUserId={}, operator={}", targetUserId, StpUtil.getLoginIdAsLong());
    }

    /**
     * 封号（ROOT 权限，路由层保证）：目标用户须存在、禁止操作自己、重复封禁报 400。
     */
    public void banUser(long targetUserId) {
        User target = requireManageable(targetUserId);
        if (target.getStatus() != null && target.getStatus() == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户已被封禁");
        }
        target.setStatus(0);
        userMapper.updateById(target);
        log.info("用户已封禁: targetUserId={}, operator={}", targetUserId, StpUtil.getLoginIdAsLong());
    }

    /**
     * 解封（ROOT 权限，路由层保证）：目标用户须存在、禁止操作自己、重复解封报 400。
     */
    public void unbanUser(long targetUserId) {
        User target = requireManageable(targetUserId);
        if (target.getStatus() == null || target.getStatus() == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户未被封禁");
        }
        target.setStatus(1);
        userMapper.updateById(target);
        log.info("用户已解封: targetUserId={}, operator={}", targetUserId, StpUtil.getLoginIdAsLong());
    }

    /** 用户管理公共校验：目标用户存在 + 禁止操作自己，返回目标用户 */
    private User requireManageable(long targetUserId) {
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标用户不存在");
        }
        if (targetUserId == StpUtil.getLoginIdAsLong()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能操作自己");
        }
        return target;
    }

    /** 用户是否拥有某角色编码 */
    private boolean hasRole(long userId, String roleCode) {
        return getRoleCodes(userId).contains(roleCode);
    }

    /** 给用户绑定角色（按角色编码） */
    private void bindRole(long userId, String roleCode) {
        Role role = requireRole(roleCode);
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }

    /** 按编码查角色（种子数据缺失视为内部错误） */
    private Role requireRole(String roleCode) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, roleCode));
        if (role == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "角色种子数据缺失: " + roleCode);
        }
        return role;
    }
}
