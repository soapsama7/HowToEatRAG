package com.anfioo.howtocook.app.agent;

import com.anfioo.howtocook.app.dto.MemoryTurn;
import com.anfioo.howtocook.app.service.ConversationService;
import com.anfioo.howtocook.common.enums.chat.MessageType;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史消息注入 Advisor（优化 2.5）：在 before 阶段从 message 表读取最近 memory-window 条
 * USER_MESSAGE / FINAL_ANSWER，注入到当前用户消息之前，替代 AgentService 里的 buildHistory()。
 * <p>每次对话创建一个实例（携带 conversationId），经 {@code ChatClient.prompt().advisors(...)} 装配。</p>
 */
public class AgentHistoryAdvisor implements BaseAdvisor {

    private static final int ORDER = 10;

    private final ConversationService conversationService;
    private final int memoryWindow;
    private final long conversationId;

    public AgentHistoryAdvisor(ConversationService conversationService, int memoryWindow, long conversationId) {
        this.conversationService = conversationService;
        this.memoryWindow = memoryWindow;
        this.conversationId = conversationId;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        List<Message> history = loadHistory();
        if (history.isEmpty()) {
            return request;
        }
        Prompt prompt = request.prompt();
        List<Message> instructions = prompt.getInstructions();
        int insertAt = lastUserMessageIndex(instructions);

        List<Message> merged = new ArrayList<>(instructions.size() + history.size());
        for (int i = 0; i < instructions.size(); i++) {
            if (i == insertAt) {
                merged.addAll(history);
            }
            merged.add(instructions.get(i));
        }
        if (insertAt >= instructions.size()) {
            merged.addAll(history);
        }
        return request.mutate().prompt(new Prompt(merged, prompt.getOptions())).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public String getName() {
        return "AgentHistoryAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /** 从持久化消息构造 Spring AI 历史消息（仅 USER_MESSAGE / FINAL_ANSWER） */
    private List<Message> loadHistory() {
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

    /** 找到最后一条 UserMessage 的下标，历史消息注入到它之前；无 UserMessage 则追加到末尾 */
    private int lastUserMessageIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return i;
            }
        }
        return messages.size();
    }
}
