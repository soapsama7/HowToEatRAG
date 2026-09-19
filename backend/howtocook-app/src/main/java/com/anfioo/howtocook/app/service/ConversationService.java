package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.app.dto.MemoryTurn;
import com.anfioo.howtocook.app.dto.MessageResponse;
import com.anfioo.howtocook.common.entity.chat.Conversation;
import com.anfioo.howtocook.common.entity.chat.Message;
import com.anfioo.howtocook.common.enums.chat.MessageRole;
import com.anfioo.howtocook.common.enums.chat.MessageType;
import com.anfioo.howtocook.common.mapper.chat.ConversationMapper;
import com.anfioo.howtocook.common.mapper.chat.MessageMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话服务：会话 CRUD（归属校验）+ 历史消息查询 + 消息落库（供对话链路与 ChatMemory 复用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    /** 新会话默认标题（首条消息后由对话链路改写） */
    public static final String DEFAULT_TITLE = "新对话";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /** 新建会话（title 可空，默认"新对话"） */
    public Conversation create(long userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : title);
        conversationMapper.insert(conversation);
        return conversation;
    }

    /** 我的会话分页（创建时间倒序） */
    public Page<Conversation> listMine(long userId, long pageNum, long pageSize) {
        Page<Conversation> page = new Page<>(pageNum, pageSize);
        return conversationMapper.selectPage(page, new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getUpdatedAt));
    }

    /** 查询会话并校验归属：不存在 404，越权 403 */
    public Conversation getOwned(long conversationId, long userId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (conversation.getUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
        return conversation;
    }

    /** 历史消息（时间正序；references/trace 解析为 JSON 结构） */
    public List<MessageResponse> messages(long conversationId, long userId) {
        getOwned(conversationId, userId);
        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getId));
        List<MessageResponse> result = new ArrayList<>(messages.size());
        for (Message m : messages) {
            result.add(MessageResponse.builder()
                    .id(m.getId())
                    .role(m.getRole())
                    .messageType(m.getMessageType())
                    .content(m.getContent())
                    .toolName(m.getToolName())
                    .references(parseJson(m.getReferences()))
                    .trace(parseJson(m.getTrace()))
                    .createdAt(m.getCreatedAt())
                    .build());
        }
        return result;
    }

    /** 删除会话（逻辑删） */
    public void delete(long conversationId, long userId) {
        getOwned(conversationId, userId);
        conversationMapper.deleteById(conversationId);
    }

    /**
     * 追加一条消息（对话链路 / ChatMemory 共用的落库入口）。
     * references/trace 入参为已序列化的 JSON 字符串（可空）。
     */
    public Long appendMessage(long conversationId, String role, String messageType,
                              String content, String toolName, String referencesJson, String traceJson) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setToolName(toolName);
        message.setReferences(referencesJson);
        message.setTrace(traceJson);
        messageMapper.insert(message);
        return message.getId();
    }

    /** 会话标题改写（首条消息后由对话链路调用） */
    public void updateTitleIfDefault(long conversationId, String firstQuestion) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null && DEFAULT_TITLE.equals(conversation.getTitle())
                && firstQuestion != null && !firstQuestion.isBlank()) {
            String title = firstQuestion.strip();
            conversation.setTitle(title.length() > 30 ? title.substring(0, 30) : title);
            conversationMapper.updateById(conversation);
        }
    }

    /**
     * 读取最近 limit 条可注入上下文的消息（USER_MESSAGE / FINAL_ANSWER，时间正序）。
     * <p>供 DbChatMemory 使用——实体与 Spring AI 的 Message 同简单名，此处封装避免歧义（Step 4.1）。</p>
     */
    public List<MemoryTurn> recentTurns(long conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Message> recent = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .in(Message::getMessageType, MessageType.USER_MESSAGE.name(), MessageType.FINAL_ANSWER.name())
                .orderByDesc(Message::getId)
                .last("LIMIT " + limit));
        java.util.Collections.reverse(recent);
        List<MemoryTurn> turns = new ArrayList<>(recent.size());
        for (Message message : recent) {
            turns.add(MemoryTurn.builder()
                    .messageType(message.getMessageType())
                    .content(message.getContent() == null ? "" : message.getContent())
                    .build());
        }
        return turns;
    }

    /** 追加一轮上下文消息（type 取 MessageType.USER_MESSAGE / FINAL_ANSWER；供 DbChatMemory 写入） */
    public Long appendTurn(long conversationId, String messageType, String content) {
        String role = MessageType.USER_MESSAGE.name().equals(messageType)
                ? MessageRole.USER.name() : MessageRole.ASSISTANT.name();
        return appendMessage(conversationId, role, messageType, content, null, null, null);
    }

    /** 清空会话全部消息（物理删；供 DbChatMemory.clear） */
    public void clearMessages(long conversationId) {
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId));
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
