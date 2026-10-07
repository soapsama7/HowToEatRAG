package com.anfioo.howtocook.app.config;

import com.anfioo.howtocook.common.entity.user.Role;
import com.anfioo.howtocook.common.entity.user.User;
import com.anfioo.howtocook.common.entity.user.UserRole;
import com.anfioo.howtocook.common.enums.user.RoleCode;
import com.anfioo.howtocook.common.mapper.user.RoleMapper;
import com.anfioo.howtocook.common.mapper.user.UserMapper;
import com.anfioo.howtocook.common.mapper.user.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * root 账号初始化器（优化 2.2 三档权限）：应用启动时确保 root 账号存在并绑定 ROOT 角色。
 * <p>密码来源：环境变量 {@code ROOT_PASSWORD}；未设置时使用缺省初始密码 {@code root@12345}
 * 并在启动日志显式警告。生产部署必须设置 ROOT_PASSWORD。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RootAccountInitializer {

    /** 缺省初始密码（仅本地开发用；生产必须通过 ROOT_PASSWORD 覆盖） */
    private static final String DEFAULT_ROOT_PASSWORD = "root@12345";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner rootAccountRunner() {
        return args -> {
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, "root"));
            if (exists > 0) {
                return;
            }

            String rawPassword = System.getenv().getOrDefault("ROOT_PASSWORD", DEFAULT_ROOT_PASSWORD);
            if (DEFAULT_ROOT_PASSWORD.equals(rawPassword)) {
                log.warn("============================================================");
                log.warn("root 账号使用缺省初始密码 {}，请尽快修改或设置环境变量 ROOT_PASSWORD", DEFAULT_ROOT_PASSWORD);
                log.warn("============================================================");
            } else {
                log.info("root 账号不存在，按 ROOT_PASSWORD 环境变量创建");
            }

            User root = new User();
            root.setUsername("root");
            root.setPassword(passwordEncoder.encode(rawPassword));
            root.setNickname("超级管理员");
            root.setStatus(1);
            userMapper.insert(root);

            Role rootRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getCode, RoleCode.ROOT.name()));
            if (rootRole == null) {
                log.error("ROOT 角色种子数据缺失，root 账号未绑定角色（请检查 V5__role_root.sql 是否已执行）");
                return;
            }
            UserRole userRole = new UserRole();
            userRole.setUserId(root.getId());
            userRole.setRoleId(rootRole.getId());
            userRoleMapper.insert(userRole);
            log.info("root 账号已创建并绑定 ROOT 角色, userId={}", root.getId());
        };
    }
}
