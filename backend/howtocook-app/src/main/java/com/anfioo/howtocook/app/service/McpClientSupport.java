package com.anfioo.howtocook.app.service;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP Client 容错初始化支持（Step 3.3）：
 * 关闭 Spring AI 的启动期急切初始化（spring.ai.mcp.client.initialized=false）后，
 * 由本服务在应用就绪时尝试初始化——失败仅告警，主应用以"无知识库"模式继续运行；
 * 对话/冒烟链路调用 {@link #ensureInitialized()} 幂等补初始化（如 mcp-tool-server 后续恢复）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientSupport {

    private final List<McpSyncClient> mcpSyncClients;

    /** 应用就绪后尝试一次初始化（失败不阻断启动） */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        boolean ok = ensureInitialized();
        if (!ok) {
            log.warn("mcp-tool-server 不可用，主应用以无知识库模式运行（对话功能受限）");
        }
    }

    /** 幂等初始化：未连接的客户端逐个尝试；全部成功返回 true */
    public synchronized boolean ensureInitialized() {
        boolean allOk = true;
        for (McpSyncClient client : mcpSyncClients) {
            if (client.isInitialized()) {
                continue;
            }
            try {
                client.initialize();
                log.info("MCP Client 初始化成功: {}", client.getClientInfo().name());
            } catch (Exception e) {
                allOk = false;
                log.warn("MCP Client 初始化失败: {}", e.getMessage());
            }
        }
        return allOk;
    }
}
