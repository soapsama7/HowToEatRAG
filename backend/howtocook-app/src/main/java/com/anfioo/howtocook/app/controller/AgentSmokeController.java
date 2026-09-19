package com.anfioo.howtocook.app.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.agent.AgentRunResult;
import com.anfioo.howtocook.app.agent.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【临时】Agent 冒烟接口（Step 4.2 验收用，Step 5.2 清理时删除）：
 * 直接触发 Agent 决策链路，返回最终回答 + trace + references。
 */
@RestController
@RequiredArgsConstructor
public class AgentSmokeController {

    private final AgentService agentService;

    @GetMapping("/api/agent/smoke")
    public AgentRunResult smoke(@RequestParam long conversationId, @RequestParam String question) {
        return agentService.ask(conversationId, StpUtil.getLoginIdAsLong(), question);
    }
}
