package com.anfioo.howtocook.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话请求体：POST /api/conversations/{id}/chat（开发文档 §5.6）。
 */
@Data
public class ChatRequest {

    /** 用户提问 */
    @NotBlank(message = "question 不能为空")
    private String question;
}
