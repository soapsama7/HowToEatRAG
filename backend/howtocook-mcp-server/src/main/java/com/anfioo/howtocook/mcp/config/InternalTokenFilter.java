package com.anfioo.howtocook.mcp.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.List;

/**
 * 内部预共享 Token 鉴权过滤器（开发文档 §5.4）：
 * 校验请求头 {@code X-Internal-Token}，不匹配返回 401。
 * <p>白名单：/actuator/health（compose healthcheck）与 /sse（SSE 连接建立）。
 * /mcp/message（工具调用）与其余一切端点都必须携带 Token——即使容器被误暴露，
 * 没有预共享 Token 也调不到工具（纵深防御）。主应用 MCP Client 默认 Header 注入同一 Token。</p>
 */
public class InternalTokenFilter implements Filter {

    /** 白名单前缀：健康检查 + SSE 连接建立端点 */
    public static final List<String> WHITELIST_PREFIXES = List.of("/actuator/health", "/sse");

    /** 预共享 Token 请求头名 */
    public static final String TOKEN_HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();
        boolean whitelisted = WHITELIST_PREFIXES.stream().anyMatch(path::startsWith);
        if (whitelisted || expectedToken.equals(req.getHeader(TOKEN_HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        resp.setStatus(HttpStatus.UNAUTHORIZED.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"code\":40100,\"message\":\"invalid internal token\",\"data\":null}");
    }
}
