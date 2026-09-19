package com.anfioo.howtocook.app.controller;

import com.anfioo.howtocook.app.service.McpClientSupport;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【临时】MCP Client 冒烟接口（Step 3.3 验收用，Step 5.2 清理时删除）：
 * 直接触发一次 search_chunks 工具调用，验证主应用经 MCP 协议调通 mcp-tool-server。
 */
@RestController
@RequiredArgsConstructor
public class McpSmokeController {

    private final List<McpSyncClient> mcpSyncClients;
    private final McpClientSupport mcpClientSupport;

    /** 冒烟：调用 search_chunks("红烧鱼怎么做", topK=3) 返回工具原始结果 */
    @GetMapping("/api/mcp/smoke")
    public String smoke() {
        mcpClientSupport.ensureInitialized();
        McpSyncClient client = mcpSyncClients.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("MCP Client 未装配"));
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "search_chunks", java.util.Map.of("query", "红烧鱼怎么做", "topK", 3)));
        return String.valueOf(result);
    }
}
