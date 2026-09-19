package com.anfioo.howtocook.app.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Client 配置（Step 3.3）：
 * SSE 连接参数在 application.yml（spring.ai.mcp.client.sse.connections），
 * 内部 Token 通过 SDK 的请求定制器注入——所有发往 mcp-tool-server 的请求
 * （含 /mcp/message 工具调用）自动携带 X-Internal-Token 请求头。
 * <p>连接失败不 fail-fast（见 application.yml 注释与 Step 3.3 约定）：Spring AI 对
 * 初始化失败的连接仅记录告警，主应用以"无知识库"模式继续运行。</p>
 */
@Configuration
public class McpClientConfig {

    @Bean
    public McpSyncHttpClientRequestCustomizer internalTokenHeaderCustomizer(
            @Value("${howtocook.security.internal-token}") String internalToken) {
        return (builder, method, uri, body, context) -> builder.header("X-Internal-Token", internalToken);
    }
}
