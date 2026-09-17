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
 * 管理员账号初始化器：应用启动时确保 admin 账号存在。
 * <p>密码来源：环境变量 {@code ADMIN_PASSWORD}；未设置时使用缺省初始密码 {@code admin@12345}
 * 并在启动日志显式警告（开发文档 Step 1.2 ⑤）。生产部署必须设置 ADMIN_PASSWORD。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminAccountInitializer {

    /** 缺省初始密码（仅本地开发用；生产必须通过 ADMIN_PASSWORD 覆盖） */
    private static final String DEFAULT_ADMIN_PASSWORD = "admin@12345";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner adminAccountRunner() {
        return args -> {
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, "admin"));
            if (exists > 0) {
                return;
            }

            String rawPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
            if (DEFAULT_ADMIN_PASSWORD.equals(rawPassword)) {
                log.warn("============================================================");
                log.warn("admin 账号使用缺省初始密码 {}，请尽快修改或设置环境变量 ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
                log.warn("============================================================");
            } else {
                log.info("admin 账号不存在，按 ADMIN_PASSWORD 环境变量创建");
            }

            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(rawPassword));
            admin.setNickname("管理员");
            admin.setStatus(1);
            userMapper.insert(admin);

            Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getCode, RoleCode.ADMIN.name()));
            if (adminRole == null) {
                log.error("ADMIN 角色种子数据缺失，admin 账号未绑定角色（请检查 V2__seed.sql 是否已执行）");
                return;
            }
            UserRole userRole = new UserRole();
            userRole.setUserId(admin.getId());
            userRole.setRoleId(adminRole.getId());
            userRoleMapper.insert(userRole);
            log.info("admin 账号已创建并绑定 ADMIN 角色, userId={}", admin.getId());
        };
    }
}
