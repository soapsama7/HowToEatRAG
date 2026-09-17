package com.anfioo.howtocook.app.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.service.AuthService;
import com.anfioo.howtocook.common.enums.user.RoleCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：登录拦截 + 角色鉴权 + BCrypt 编码器。
 * <p>会话存储接 Redis（sa-token-redis-jackson 自动装配，配置见 application.yml 的 sa-token 段）。</p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** 无需登录即可访问的路径（注册/登录/运维健康检查/错误页） */
    private static final String[] AUTH_WHITELIST = {
            "/api/auth/register",
            "/api/auth/login",
            "/actuator/**",
            "/error"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 1) 白名单外一律要求登录
                    SaRouter.match("/**")
                            .notMatch(AUTH_WHITELIST)
                            .check(r -> StpUtil.checkLogin());
                    // 2) 管理端要求 ADMIN 角色（RBAC 数据源：StpInterfaceImpl）
                    SaRouter.match("/api/admin/**")
                            .check(r -> StpUtil.checkRole(RoleCode.ADMIN.name()));
                }))
                .addPathPatterns("/**")
                .order(0);
    }

    /** RBAC 数据源：从 user_role JOIN role 读取角色（注册为 Bean，Sa-Token 自动发现） */
    @Bean
    public StpInterfaceImpl stpInterfaceImpl(AuthService authService) {
        return new StpInterfaceImpl(authService);
    }
}
