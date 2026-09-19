package com.anfioo.howtocook.app.agent;

import com.anfioo.howtocook.app.dto.MemoryTurn;
import com.anfioo.howtocook.app.service.ConversationService;
import com.anfioo.howtocook.app.service.McpClientSupport;
import com.anfioo.howtocook.app.service.PreferenceService;
import com.anfioo.howtocook.common.constant.AgentPrompts;
import com.anfioo.howtocook.common.enums.chat.MessageType;
import com.anfioo.howtocook.common.enums.chat.MessageRole;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Agent 服务（开发文档 §5.5 / Step 4.2）：Agentic RAG 决策链路。
 * <ul>
 *   <li>System Prompt：AgentPrompts 模板 + 用户偏好注入；</li>
 *   <li>工具：MCP search_chunks / get_recipe_detail（经 TracedToolCallback 包装，收集 trace/引用并强制轮数/超时约束）；</li>
 *   <li>记忆：DbChatMemory 读取最近 memory-window 条历史注入上下文（仅 USER_MESSAGE / FINAL_ANSWER）；</li>
 *   <li>落库：USER 消息与 ASSISTANT(FINAL_ANSWER) 消息由本服务持久化，trace/references 随 ASSISTANT 消息保存。</li>
 * </ul>
 */
@Slf4j
@Service
public class AgentService {

    /** 携带的最近消息条数（howtocook.agent.memory-window） */
    @org.springframework.beans.factory.annotation.Value("${howtocook.agent.memory-window:10}")
    private int memoryWindow;

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ConversationService conversationService;
    private final PreferenceService preferenceService;
    private final McpClientSupport mcpClientSupport;
    private final ObjectMapper objectMapper;

    public AgentService(ChatClient.Builder chatClientBuilder,
                        ToolCallbackProvider toolCallbackProvider,
                        ConversationService conversationService,
                        PreferenceService preferenceService,
                        McpClientSupport mcpClientSupport,
                        ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.toolCallbackProvider = toolCallbackProvider;
        this.conversationService = conversationService;
        this.preferenceService = preferenceService;
        this.mcpClientSupport = mcpClientSupport;
        this.objectMapper = objectMapper;
    }

    /** 同步问答（Step 4.2 验收与临时冒烟用；SSE 流式版随 Step 4.3） */
    public AgentRunResult ask(long conversationId, long userId, String question) {
        conversationService.getOwned(conversationId, userId);
        mcpClientSupport.ensureInitialized();

        // 1) 上下文：最近 memory-window 条历史（不含本次提问）
        List<Message> history = buildHistory(conversationId);

        // 2) 工具：MCP 回调包装（trace/引用收集 + 轮数/超时/重试约束）
        AgentRunContext context = new AgentRunContext();
        ToolCallback[] wrappedTools = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(cb -> (ToolCallback) new TracedToolCallback(cb, context, objectMapper))
                .toArray(ToolCallback[]::new);

        // 3) System Prompt + 偏好注入；USER 消息落库（带上下文调用）
        String system = String.format(AgentPrompts.SYSTEM_PROMPT_TEMPLATE,
                preferenceService.loadAsPromptText(userId));
        conversationService.appendMessage(conversationId, MessageRole.USER.name(),
                MessageType.USER_MESSAGE.name(), question, null, null, null);
        conversationService.updateTitleIfDefault(conversationId, question);

        String answer;
        try {
            answer = chatClient.prompt()
                    .system(system)
                    .messages(history)
                    .user(question)
                    .toolCallbacks(wrappedTools)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Agent 调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "对话服务暂时不可用，请稍后再试");
        }

        // 4) 最终回答 + trace/references 落库
        String finalAnswer = answer == null ? "" : answer;
        conversationService.appendMessage(conversationId, MessageRole.ASSISTANT.name(),
                MessageType.FINAL_ANSWER.name(), finalAnswer, null,
                toJson(context.getReferences()), toJson(context.getTrace()));

        AgentRunResult result = new AgentRunResult();
        result.setFinalAnswer(finalAnswer);
        result.setTrace(context.getTrace());
        result.setReferences(context.getReferences());
        return result;
    }

    /** 由持久化消息构造 Spring AI 历史消息（仅 USER_MESSAGE / FINAL_ANSWER） */
    private List<Message> buildHistory(long conversationId) {
        List<MemoryTurn> turns = conversationService.recentTurns(conversationId, memoryWindow);
        List<Message> history = new ArrayList<>(turns.size());
        for (MemoryTurn turn : turns) {
            if (MessageType.USER_MESSAGE.name().equals(turn.getMessageType())) {
                history.add(new UserMessage(turn.getContent()));
            } else {
                history.add(new AssistantMessage(turn.getContent()));
            }
        }
        return history;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
