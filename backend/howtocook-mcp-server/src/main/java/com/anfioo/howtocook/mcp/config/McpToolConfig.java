package com.anfioo.howtocook.mcp.config;

import com.anfioo.howtocook.mcp.tool.RecipeTools;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 工具装配：把 RecipeTools 注册为 MCP 工具回调（search_chunks / get_recipe_detail）。
 */
@Configuration
public class McpToolConfig {

    @Bean
    public MethodToolCallbackProvider toolCallbackProvider(RecipeTools recipeTools) {
        return MethodToolCallbackProvider.builder().toolObjects(recipeTools).build();
    }
}
