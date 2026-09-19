package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话响应 DTO。
 */
@Data
@Builder
public class ConversationResponse {

    /** 会话 ID */
    private Long id;

    /** 会话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
