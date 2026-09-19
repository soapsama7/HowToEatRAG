package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 对话上下文一轮（供 DbChatMemory 读取最近历史）。
 * <p>独立 DTO 的原因：common 实体 chat.Message 与 Spring AI 的 messages.Message 同简单名，
 * 同类共存会引用歧义（Step 4.1），故 DbChatMemory 不直接接触实体。</p>
 */
@Data
@Builder
public class MemoryTurn {

    /** 消息类型：USER_MESSAGE / FINAL_ANSWER */
    private String messageType;

    /** 正文 */
    private String content;
}
