package com.anfioo.howtocook.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内部 Token 过滤器注册（开发文档 §5.4）：拦截一切路径，白名单与校验规则见 {@link InternalTokenFilter}。
 */
@Configuration
public class InternalTokenFilterConfig {

    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(
            @Value("${howtocook.security.internal-token}") String internalToken) {
        FilterRegistrationBean<InternalTokenFilter> registration = new FilterRegistrationBean<>(
                new InternalTokenFilter(internalToken));
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }
}
