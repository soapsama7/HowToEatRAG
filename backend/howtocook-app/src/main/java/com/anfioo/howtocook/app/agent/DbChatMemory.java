package com.anfioo.howtocook.app.agent;

import com.anfioo.howtocook.app.dto.MemoryTurn;
import com.anfioo.howtocook.app.service.ConversationService;
import com.anfioo.howtocook.common.enums.chat.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 message 表的 ChatMemory 适配（开发文档 §5.5 / Step 4.1）：
 * <ul>
 *   <li>读取：最近 memory-window（默认 10）条，仅 USER_MESSAGE / FINAL_ANSWER 参与上下文，
 *       TOOL_CALL / TOOL_RESULT 等中间消息不注入；</li>
 *   <li>写入：UserMessage → USER_MESSAGE，AssistantMessage → FINAL_ANSWER；</li>
 *   <li>conversationId 为会话表主键的字符串形式。</li>
 * </ul>
 * <p>持久化统一委托 {@link ConversationService}——common 实体 chat.Message 与
 * Spring AI 的 messages.Message 同简单名，本类不直接接触实体（Step 4.1，先例见 1.3 注解更名）。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DbChatMemory implements ChatMemory {

    private final ConversationService conversationService;

    /** 携带的最近消息条数（howtocook.agent.memory-window） */
    @Value("${howtocook.agent.memory-window:10}")
    private int memoryWindow;

    @Override
    public void add(String conversationId, List<Message> messages) {
        long conversationIdLong = parseId(conversationId);
        if (conversationIdLong <= 0 || messages == null || messages.isEmpty()) {
            return;
        }
        for (Message message : messages) {
            if (message instanceof UserMessage userMessage) {
                conversationService.appendTurn(conversationIdLong,
                        MessageType.USER_MESSAGE.name(), userMessage.getText());
            } else if (message instanceof AssistantMessage assistantMessage) {
                conversationService.appendTurn(conversationIdLong,
                        MessageType.FINAL_ANSWER.name(), assistantMessage.getText());
            }
            // SystemMessage / ToolResponseMessage 等不落库（TOOL 消息不参与上下文）
        }
    }

    /** 读取最近 memory-window 条可注入上下文的消息（时间正序返回） */
    @Override
    public List<Message> get(String conversationId) {
        long conversationIdLong = parseId(conversationId);
        if (conversationIdLong <= 0) {
            return List.of();
        }
        List<MemoryTurn> turns = conversationService.recentTurns(conversationIdLong, memoryWindow);
        List<Message> result = new ArrayList<>(turns.size());
        for (MemoryTurn turn : turns) {
            if (MessageType.USER_MESSAGE.name().equals(turn.getMessageType())) {
                result.add(new UserMessage(turn.getContent()));
            } else {
                result.add(new AssistantMessage(turn.getContent()));
            }
        }
        return result;
    }

    /** 清空会话上下文：物理删除该会话全部消息（会话删除仍走逻辑删） */
    @Override
    public void clear(String conversationId) {
        long conversationIdLong = parseId(conversationId);
        if (conversationIdLong > 0) {
            conversationService.clearMessages(conversationIdLong);
        }
    }

    private long parseId(String conversationId) {
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            log.warn("conversationId 非数字: {}", conversationId);
            return -1;
        }
    }
}
