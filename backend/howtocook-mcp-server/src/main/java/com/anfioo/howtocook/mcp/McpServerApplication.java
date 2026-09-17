package com.anfioo.howtocook.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Tool Server 启动类（独立部署，仅 docker 内网可达）。
 * <p>scanBasePackages 覆盖 howtocook-common 中的公共配置与能力（检索 / 存储 / 解析）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.anfioo.howtocook")
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

}
